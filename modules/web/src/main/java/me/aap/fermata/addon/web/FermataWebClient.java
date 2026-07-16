package me.aap.fermata.addon.web;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.SslErrorHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import me.aap.fermata.addon.web.yt.YoutubeFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebClient extends WebViewClientCompat {
	BooleanConsumer loading;
	private String failedMainFrameUrl;
	private String lastErrorKey;
	private String retryUrl;
	private int retryCount;
	private long retryGeneration;

	FermataWebClient createReplacement() {
		try {
			return getClass().getConstructor().newInstance();
		} catch (Throwable ex) {
			Log.e(ex, "Failed to create replacement WebViewClient");
			return new FermataWebClient();
		}
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		retryGeneration++;
		failedMainFrameUrl = null;
		lastErrorKey = null;
		if ((retryUrl == null) || !retryUrl.equals(url)) {
			retryUrl = url;
			retryCount = 0;
		}
		if (loading != null) {
			loading.accept(true);
		} else {
			MainActivityDelegate.getActivityDelegate(view.getContext())
					.onSuccess(a -> a.setContentLoading(new Promise<>()));
		}
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		FermataWebView v = (FermataWebView) view;
		FutureSupplier<MainActivityDelegate> f =
				MainActivityDelegate.getActivityDelegate(v.getContext());
		f.onSuccess(a -> a.setContentLoading(Completed.completedVoid()));

		if (loading != null) {
			loading.accept(false);
			loading = null;
		}

		super.onPageFinished(view, url);
		((FermataWebView) view).hideKeyboard();
		if ((failedMainFrameUrl == null) || !failedMainFrameUrl.equals(url)) {
			retryGeneration++;
			retryCount = 0;
			v.pageLoaded(url);
		}
		f.onSuccess(a -> a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED));
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
																					@NonNull WebResourceRequest request) {
		if (isYoutubeUri(request.getUrl())) {
			try {
				MainActivityDelegate a =
						MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
				if (a == null) return false;
				if (!(a.showFragment(me.aap.fermata.R.id.youtube_fragment) instanceof YoutubeFragment f))
					return false;
				f.loadUrl(request.getUrl().toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}

	public static boolean isYoutubeUri(Uri uri) {
		String host = uri.getHost();
		return ((host != null) && ((host.endsWith("youtube.com") && !host.endsWith("tv.youtube.com")) ||
				host.equals("youtu.be")));
	}

	@Override
	public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request,
															@NonNull WebResourceErrorCompat error) {
		String desc = "unknown error";
		if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
			desc = String.valueOf(error.getDescription());
			Log.e("Web error received: " + desc);
		} else {
			Log.e("Web error received");
		}

		if (request.isForMainFrame()) {
			failedMainFrameUrl = request.getUrl().toString();
			completeLoading(view);
			if (!scheduleAutoRetry(view, request.getUrl(), desc))
				showLoadError(view, request.getUrl(), desc);
		}

		super.onReceivedError(view, request, error);
	}

	@Override
	public void onReceivedHttpError(@NonNull WebView view, @NonNull WebResourceRequest request,
																	@NonNull WebResourceResponse errorResponse) {
		if (request.isForMainFrame()) {
			String reason = "HTTP " + errorResponse.getStatusCode();
			String phrase = errorResponse.getReasonPhrase();
			if ((phrase != null) && !phrase.isEmpty()) reason += " " + phrase;
			Log.e("Web HTTP error received: " + reason);
			failedMainFrameUrl = request.getUrl().toString();
			completeLoading(view);
			if (!scheduleAutoRetry(view, request.getUrl(), reason))
				showLoadError(view, request.getUrl(), reason);
		}

		super.onReceivedHttpError(view, request, errorResponse);
	}

	@Override
	public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
		handler.cancel();
		String url = error.getUrl();
		String reason = "SSL error: " + error;
		Log.e("Web SSL error received: " + reason);
		failedMainFrameUrl = url;
		completeLoading(view);
		if (url != null) showLoadError(view, Uri.parse(url), reason);
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
		Log.e("WebView renderer gone, crashed: ", detail.didCrash());
		completeLoading(view);
		if (view instanceof FermataWebView v) return v.recoverRenderProcess();
		return super.onRenderProcessGone(view, detail);
	}

	private void completeLoading(WebView view) {
		MainActivityDelegate.getActivityDelegate(view.getContext())
				.onSuccess(a -> a.setContentLoading(Completed.completedVoid()));
		if (loading != null) {
			loading.accept(false);
			loading = null;
		}
	}

	private void showLoadError(WebView view, Uri uri, String reason) {
		if (uri == null) return;
		String url = uri.toString();
		if (url.startsWith("about:") || url.startsWith("data:")) return;
		String key = url + '\n' + reason;
		if (key.equals(lastErrorKey)) return;
		lastErrorKey = key;

		Context ctx = view.getContext();
		String msg = ctx.getString(R.string.web_page_load_failed_msg, url, reason, getWebViewPackage());
		MainActivityDelegate.getActivityDelegate(ctx).onSuccess(a -> {
			if (view.getParent() == null) return;
			a.createDialogBuilder(ctx)
					.setTitle(android.R.drawable.ic_dialog_alert, R.string.web_page_load_failed)
					.setMessage(msg)
					.setNegativeButton(android.R.string.ok, null)
					.setPositiveButton(R.string.retry, (d, w) -> view.reload())
					.show();
		});
	}

	private boolean scheduleAutoRetry(WebView view, Uri uri, String reason) {
		if ((uri == null) || !isTransientLoadError(reason)) return false;
		String url = uri.toString();
		if (url.startsWith("about:") || url.startsWith("data:")) return false;
		if (!url.equals(retryUrl)) {
			retryUrl = url;
			retryCount = 0;
		}
		if (!canAutoRetry(retryCount)) return false;

		int attempt = ++retryCount;
		long delay = getAutoRetryDelay(attempt);
		long generation = retryGeneration;
		Log.e("Transient WebView error. Auto-retrying in ", delay, " ms: ", reason, " URL: ", url);
		view.postDelayed(() -> {
			if (view.getParent() == null) return;
			String current = view.getUrl();
			if (shouldRunRetry(generation, retryGeneration, url, current, failedMainFrameUrl)) {
				view.loadUrl(url);
			}
		}, delay);
		return true;
	}

	static boolean canAutoRetry(int retryCount) {
		return retryCount < 2;
	}

	static long getAutoRetryDelay(int attempt) {
		return (attempt == 1) ? 1200L : 3000L;
	}

	static boolean shouldRunRetry(long scheduledGeneration, long currentGeneration,
														String scheduledUrl, String currentUrl, String failedUrl) {
		return (scheduledGeneration == currentGeneration) && ((currentUrl == null) ||
				currentUrl.equals(scheduledUrl) || scheduledUrl.equals(failedUrl));
	}

	static boolean isTransientLoadError(String reason) {
		if (reason == null) return false;
		String r = reason.toLowerCase();
		return r.contains("timeout") || r.contains("timed_out") || r.contains("connection_timed_out") ||
				r.contains("connection timed out") || r.contains("host lookup") ||
				r.contains("name not resolved") || r.contains("connection reset") ||
				r.contains("connection refused") || r.contains("temporarily unavailable");
	}

	private static String getWebViewPackage() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			PackageInfo info = WebView.getCurrentWebViewPackage();
			if (info != null) return info.packageName + " " + info.versionName;
		}

		return "unknown";
	}
}
