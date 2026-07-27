package me.aap.fermata.addon.stremio.ui.config;

import android.webkit.CookieManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Clears only the configured provider origin; generic Web addon state is never touched. */
interface StremioConfigOriginCleaner {
	StremioConfigOriginCleaner DEFAULT = new DefaultCleaner();

	void clearBeforeLoad(StremioConfigWebView view, String initialUrl, Runnable completed);

	void clearBeforeDestroy(StremioConfigWebView view, String initialUrl, Runnable completed);

	final class DefaultCleaner implements StremioConfigOriginCleaner {
		@Override
		public void clearBeforeLoad(StremioConfigWebView view, String initialUrl,
				Runnable completed) {
			clearNativeOrigin(view, initialUrl, completed);
		}

		@Override
		public void clearBeforeDestroy(StremioConfigWebView view, String initialUrl,
				Runnable completed) {
			view.clearOriginStorage(origin(initialUrl), () -> {
				clearNativeOrigin(view, initialUrl, completed);
			});
		}
	}

	private static void clearNativeOrigin(StremioConfigWebView view, String initialUrl,
			Runnable completed) {
		AtomicBoolean completionDelivered = new AtomicBoolean();
		Runnable completeOnce = () -> {
			if (completionDelivered.compareAndSet(false, true)) completed.run();
		};
		try {
			URI uri = URI.create(initialUrl);
			String origin = origin(initialUrl);
			view.configStorage().deleteOrigin(origin);
			CookieManager cookies = view.configCookies();
			String stored = cookies.getCookie(initialUrl);
			if ((stored == null) || stored.isBlank()) {
				completeOnce.run();
				return;
			}
			List<String> values = new ArrayList<>();
			for (String field : stored.split(";")) {
				int equals = field.indexOf('=');
				String name = ((equals < 0) ? field : field.substring(0, equals)).trim();
				if (name.isEmpty()) continue;
				for (String path : cookiePaths(uri.getPath())) {
					values.add(expiredCookie(name, path, null));
					String host = uri.getHost();
					if ((host != null) && (host.indexOf(':') < 0) && !host.matches("[0-9.]+")) {
						values.add(expiredCookie(name, path, host));
						values.add(expiredCookie(name, path, '.' + host));
					}
				}
			}
			if (values.isEmpty()) {
				completeOnce.run();
				return;
			}
			AtomicBoolean finished = new AtomicBoolean();
			AtomicInteger remaining = new AtomicInteger(values.size());
			Runnable finish = () -> {
				if (!finished.compareAndSet(false, true)) return;
				cookies.flush();
				completeOnce.run();
			};
			view.postDelayed(finish, 1_000L);
			for (String value : values) {
				cookies.setCookie(origin, value, ignored -> {
					if (remaining.decrementAndGet() == 0) {
						view.removeCallbacks(finish);
						finish.run();
					}
				});
			}
		} catch (Throwable ignored) {
			// Cleanup is bounded and best-effort; config transport never forwards cookies.
			completeOnce.run();
		}
	}

	private static String expiredCookie(String name,
			String path, String domain) {
		StringBuilder value = new StringBuilder(name)
				.append("=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=")
				.append(path).append("; SameSite=Strict");
		if (domain != null) value.append("; Domain=").append(domain);
		return value.toString();
	}

	static String origin(String url) {
		URI uri = URI.create(url);
		return uri.getScheme() + "://" + uri.getRawAuthority();
	}

	static List<String> cookiePaths(String rawPath) {
		LinkedHashSet<String> paths = new LinkedHashSet<>();
		paths.add("/");
		String path = ((rawPath == null) || rawPath.isBlank()) ? "/" : rawPath;
		if (!path.startsWith("/")) path = '/' + path;
		int end = path.length();
		while (end > 1) {
			paths.add(path.substring(0, end));
			end = path.lastIndexOf('/', end - 1);
			if (end <= 0) break;
		}
		return List.copyOf(new ArrayList<>(paths));
	}
}
