package me.aap.fermata.addon.stremio.ui.config;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Looper;
import android.os.Handler;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebViewClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.stremio.net.NetworkConsent;

/** Owns one isolated provider-configuration WebView session. */
public final class StremioConfigWebController implements AutoCloseable {
	private final StremioConfigWebView view;
	private final StremioConfigUrlPolicy policy;
	private final StremioConfigCallback callback;
	private final AtomicBoolean terminal = new AtomicBoolean();
	private final AtomicBoolean closed = new AtomicBoolean();
	private final StremioConfigWebClient client;
	private final Map<String, String> initialHeaders;
	private final StremioConfigResourceLoader resourceLoader;
	private final StremioConfigOriginCleaner originCleaner;
	private final StremioConfigWebIsolation isolation;
	private volatile long completionArmedUntil;
	private final AtomicBoolean started = new AtomicBoolean();
	private final AtomicBoolean destroyed = new AtomicBoolean();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private Runnable loadingDeadline;
	private static final long COMPLETION_WINDOW_MILLIS = 30_000L;
	private static final long RESOURCE_WAIT_SECONDS = 30L;
	private static final long PAGE_LOAD_TIMEOUT_MILLIS = 30_000L;

	public StremioConfigWebController(StremioConfigWebView view, String providerConfigUrl,
			boolean allowCleartext, StremioConfigCallback callback) {
		this(view, providerConfigUrl, new NetworkConsent(allowCleartext, false), Map.of(),
				StremioConfigResourceLoader.unavailable(), StremioConfigOriginCleaner.DEFAULT,
				callback, null);
	}

	StremioConfigWebController(StremioConfigWebView view, String providerConfigUrl,
			boolean allowCleartext, Map<String, String> initialHeaders,
			StremioConfigCallback callback) {
		this(view, providerConfigUrl, new NetworkConsent(allowCleartext, false), initialHeaders,
				StremioConfigResourceLoader.unavailable(), StremioConfigOriginCleaner.DEFAULT,
				callback, StremioConfigWebIsolation.testing());
	}

	StremioConfigWebController(StremioConfigWebView view, String providerConfigUrl,
			NetworkConsent consent, Map<String, String> initialHeaders,
			StremioConfigResourceLoader resourceLoader,
			StremioConfigCallback callback) {
		this(view, providerConfigUrl, consent, initialHeaders, resourceLoader,
				StremioConfigOriginCleaner.DEFAULT, callback, StremioConfigWebIsolation.testing());
	}

	static StremioConfigWebController production(StremioConfigWebView view,
			String providerConfigUrl, NetworkConsent consent,
			Map<String, String> initialHeaders, StremioConfigResourceLoader resourceLoader,
			StremioConfigCallback callback) {
		return new StremioConfigWebController(view, providerConfigUrl, consent, initialHeaders,
				resourceLoader, StremioConfigOriginCleaner.DEFAULT, callback, null);
	}

	StremioConfigWebController(StremioConfigWebView view, String providerConfigUrl,
			NetworkConsent consent, Map<String, String> initialHeaders,
			StremioConfigResourceLoader resourceLoader,
			StremioConfigOriginCleaner originCleaner, StremioConfigCallback callback) {
		this(view, providerConfigUrl, consent, initialHeaders, resourceLoader, originCleaner,
				callback, StremioConfigWebIsolation.testing());
	}

	private StremioConfigWebController(StremioConfigWebView view, String providerConfigUrl,
			NetworkConsent consent, Map<String, String> initialHeaders,
			StremioConfigResourceLoader resourceLoader,
			StremioConfigOriginCleaner originCleaner, StremioConfigCallback callback,
			StremioConfigWebIsolation testIsolation) {
		this.view = Objects.requireNonNull(view, "view");
		this.callback = Objects.requireNonNull(callback, "callback");
		this.initialHeaders = Map.copyOf(Objects.requireNonNull(initialHeaders, "initialHeaders"));
		this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
		this.originCleaner = Objects.requireNonNull(originCleaner, "originCleaner");
		policy = new StremioConfigUrlPolicy(providerConfigUrl,
				Objects.requireNonNull(consent, "consent").allowCleartext());
		client = new StremioConfigWebClient(this, policy);
		isolation = (testIsolation != null) ? testIsolation :
				StremioConfigWebIsolation.production(view, client);
		try {
			configureWebView();
		} catch (RuntimeException failure) {
			view.destroy();
			isolation.closeAfterDestroy();
			throw failure;
		}
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void configureWebView() {
		WebSettings settings = view.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
		settings.setDatabaseEnabled(false);
		settings.setGeolocationEnabled(false);
		settings.setAllowFileAccess(false);
		settings.setAllowContentAccess(false);
		settings.setAllowFileAccessFromFileURLs(false);
		settings.setAllowUniversalAccessFromFileURLs(false);
		settings.setJavaScriptCanOpenWindowsAutomatically(false);
		settings.setSupportMultipleWindows(false);
		settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
		settings.setMediaPlaybackRequiresUserGesture(true);
		settings.setSaveFormData(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
		view.setFocusable(true);
		view.setFocusableInTouchMode(true);
		view.bind(this);
		view.setWebViewClient(client);
		CookieManager cookies = isolation.cookies();
		cookies.setAcceptThirdPartyCookies(view, false);
	}

	public void start() {
		if (closed.get() || !started.compareAndSet(false, true)) return;
		String initialUrl = policy.initialUrl();
		originCleaner.clearBeforeLoad(view, initialUrl, () -> {
			if (!closed.get() && !handleNavigation(initialUrl)) view.loadUrl(initialUrl);
		});
	}

	boolean handleNavigation(String url) {
		if (closed.get()) return true;
		if (terminal.get()) return true;
		return switch (policy.evaluate(url)) {
			case NAVIGATE -> false;
			case COMPLETE -> {
				if (!isCompletionArmed()) {
					fail(StremioConfigCallback.Failure.BLOCKED_NAVIGATION);
					yield true;
				}
				if (terminal.compareAndSet(false, true)) {
					view.stopLoading();
					callback.onLoadingChanged(false);
					callback.onConfigured(new StremioConfigResult(url));
				}
				yield true;
			}
			case BLOCKED -> {
				fail(StremioConfigCallback.Failure.BLOCKED_NAVIGATION);
				yield true;
			}
		};
	}

	void noteExplicitCompletionGesture() {
		completionArmedUntil = System.currentTimeMillis() + COMPLETION_WINDOW_MILLIS;
	}

	StremioConfigResourceLoader.Response loadResource(String url,
			Map<String, String> requestHeaders) throws Exception {
		return loadResource(url, requestHeaders, true);
	}

	StremioConfigResourceLoader.Response loadResource(String url,
			Map<String, String> requestHeaders, boolean mainFrame) throws Exception {
		if (closed.get() || !policy.isAllowedResource(url, mainFrame)) {
			throw new SecurityException("Blocked configuration resource");
		}
		URI uri = URI.create(url);
		boolean providerOrigin = policy.isProviderOrigin(url);
		LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		requestHeaders.forEach((name, value) -> {
			if (safeRequestHeader(name, value, providerOrigin)) headers.put(name, value);
		});
		if (policy.isInitialUrl(url)) headers.putAll(initialHeaders);
		if (providerOrigin) {
			String cookie = isolation.cookies().getCookie(url);
			if ((cookie != null) && !cookie.isBlank()) headers.put("Cookie", cookie);
		}
		StremioConfigResourceLoader.Response response = resourceLoader
				.load(uri, Map.copyOf(headers)).get(RESOURCE_WAIT_SECONDS, TimeUnit.SECONDS);
		String finalUrl = response.finalUri().toASCIIString();
		if (!policy.isAllowedResource(finalUrl, mainFrame)) {
			throw new SecurityException("Configuration resource redirected outside provider origin");
		}
		if (policy.isProviderOrigin(finalUrl)) applyResponseCookies(finalUrl, response.headers());
		return response;
	}

	private void applyResponseCookies(String url, Map<String, String> headers) {
		headers.forEach((name, value) -> {
			if ((name != null) && (value != null) &&
					(name.equalsIgnoreCase("set-cookie") ||
							name.equalsIgnoreCase("set-cookie2"))) {
				isolation.cookies().setCookie(url, value);
			}
		});
	}

	void loading(boolean loading) {
		if (closed.get() || terminal.get()) return;
		cancelLoadingDeadline();
		if (loading) {
			Runnable deadline = () -> {
				if (loadingDeadline == null) return;
				loadingDeadline = null;
				fail(StremioConfigCallback.Failure.LOAD_FAILED);
			};
			loadingDeadline = deadline;
			mainHandler.postDelayed(deadline, PAGE_LOAD_TIMEOUT_MILLIS);
		}
		callback.onLoadingChanged(loading);
	}

	void fail(StremioConfigCallback.Failure failure) {
		if (!closed.get() && terminal.compareAndSet(false, true)) {
			cancelLoadingDeadline();
			view.stopLoading();
			callback.onLoadingChanged(false);
			callback.onFailure(failure);
		}
	}

	boolean isClosed() {
		return closed.get();
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		cancelLoadingDeadline();
		terminal.set(true);
		if (Looper.myLooper() == Looper.getMainLooper()) destroyWebView();
		else view.post(this::destroyWebView);
	}

	private void destroyWebView() {
		view.stopLoading();
		originCleaner.clearBeforeDestroy(view, policy.initialUrl(), this::finishDestroyWebView);
	}

	private void finishDestroyWebView() {
		if (!destroyed.compareAndSet(false, true)) return;
		view.setWebChromeClient(null);
		view.setWebViewClient(new WebViewClient());
		view.clearHistory();
		view.clearFormData();
		view.removeAllViews();
		view.destroy();
		isolation.closeAfterDestroy();
	}

	private void cancelLoadingDeadline() {
		Runnable deadline = loadingDeadline;
		loadingDeadline = null;
		if (deadline != null) mainHandler.removeCallbacks(deadline);
	}

	CookieManager cookies() {
		return isolation.cookies();
	}

	WebStorage storage() {
		return isolation.storage();
	}

	private boolean isCompletionArmed() {
		return System.currentTimeMillis() <= completionArmedUntil;
	}

	private static boolean safeRequestHeader(String name, String value, boolean providerOrigin) {
		if ((name == null) || (value == null)) return false;
		String lower = name.toLowerCase(java.util.Locale.ROOT);
		if (lower.equals("host") || lower.equals("connection") ||
				lower.equals("content-length") || lower.equals("cookie") ||
				lower.equals("authorization") || lower.equals("proxy-authorization") ||
				lower.equals("x-stremio-configuration-token")) return false;
		if (!providerOrigin && (lower.equals("origin") || lower.equals("referer"))) return false;
		return (name.indexOf('\r') < 0) && (name.indexOf('\n') < 0) &&
				(value.indexOf('\r') < 0) && (value.indexOf('\n') < 0);
	}
}
