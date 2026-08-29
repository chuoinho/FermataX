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
import android.webkit.WebViewClient;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import java.util.Locale;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.addon.web.yt.YoutubeFragment;
import me.aap.fermata.addon.external.ExternalNavigationPolicy;
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
	public enum PageEvent {
		MAIN_FRAME_STARTED,
		MAIN_FRAME_FINISHED,
		HISTORY_BACK_DISPATCHED,
		HISTORY_BACK_SUPPRESSED,
		MAIN_FRAME_ERROR,
		MAIN_FRAME_HTTP_ERROR,
		MAIN_FRAME_SSL_ERROR,
		RENDERER_GONE
	}

	public enum CustomViewEvent {
		ATTACH_REJECTED,
		ATTACHED,
		ATTACH_UNCONFIRMED,
		DETACH_REQUESTED,
		DETACHED
	}

	public enum FullscreenEvent {
		REQUEST_CREATED,
		BROWSER_REQUEST_DISPATCHED,
		BROWSER_CALLBACK_RECEIVED,
		REQUEST_ACCEPTED,
		REQUEST_REJECTED,
		STATE_CHANGED,
		BROWSER_VISIBILITY_CHANGED,
		FALLBACK_ENTERED,
		CUSTOM_VIEW_REJECTED,
		PRESENTATION_RELEASED,
		BACK_EXIT_REQUESTED
	}

	public enum PlaybackEvent {
		SESSION_STARTED,
		SESSION_INVALIDATED,
		OWNERSHIP_ADOPTED,
		OWNERSHIP_LOST,
		READY_SIGNAL,
		PLAYING_SIGNAL,
		PAUSED_SIGNAL,
		ENDED_SIGNAL,
		MUTE_CHANGED,
		AUDIBLE_START_REQUESTED,
		SIGNAL_REJECTED
	}

	/** Typed, privacy-safe observer shared by the Web and YouTube diagnostics boundaries. */
	public interface DiagnosticsObserver {
		void onPage(PageEvent event, DiagnosticsSnapshot snapshot);

		void onCustomView(CustomViewEvent event, DiagnosticsSnapshot snapshot);

		void onFullscreen(FullscreenEvent event, DiagnosticsSnapshot snapshot);

		void onPlayback(PlaybackEvent event, DiagnosticsSnapshot snapshot);
	}

	/** Only enum/bool/dimension/counter data is exposed to the diagnostics backend. */
	public static final class DiagnosticsSnapshot {
		private final Enum<?> state;
		private final boolean mainFrame;
		private final boolean allowed;
		private final boolean accepted;
		private final boolean resultKnown;
		private final boolean visible;
		private final boolean attached;
		private final boolean webVisible;
		private final boolean webAttached;
		private final boolean surfaceKnown;
		private final boolean surfaceValid;
		private final boolean browserFullscreen;
		private final boolean appFullscreen;
		private final boolean ownsPlayback;
		private final boolean playing;
		private final boolean muteKnown;
		private final boolean muted;
		private final int width;
		private final int height;
		private final int webWidth;
		private final int webHeight;
		private final int errorCode;
		private final long request;
		private final long generation;
		private final long revision;

		private DiagnosticsSnapshot(Builder b) {
			state = b.state;
			mainFrame = b.mainFrame;
			allowed = b.allowed;
			accepted = b.accepted;
			resultKnown = b.resultKnown;
			visible = b.visible;
			attached = b.attached;
			webVisible = b.webVisible;
			webAttached = b.webAttached;
			surfaceKnown = b.surfaceKnown;
			surfaceValid = b.surfaceValid;
			browserFullscreen = b.browserFullscreen;
			appFullscreen = b.appFullscreen;
			ownsPlayback = b.ownsPlayback;
			playing = b.playing;
			muteKnown = b.muteKnown;
			muted = b.muted;
			width = b.width;
			height = b.height;
			webWidth = b.webWidth;
			webHeight = b.webHeight;
			errorCode = b.errorCode;
			request = b.request;
			generation = b.generation;
			revision = b.revision;
		}

		public static Builder builder() {
			return new Builder();
		}

		public Builder toBuilder() {
			return new Builder().state(state).mainFrame(mainFrame).allowed(allowed)
					.result(resultKnown, accepted).view(visible, attached, width, height)
					.web(webVisible, webAttached, webWidth, webHeight)
					.surface(surfaceKnown, surfaceValid)
					.fullscreen(browserFullscreen, appFullscreen)
					.ownsPlayback(ownsPlayback).playing(playing)
					.mute(muteKnown, muted).errorCode(errorCode).request(request)
					.generation(generation).revision(revision);
		}

		public static final class Builder {
			private Enum<?> state;
			private boolean mainFrame;
			private boolean allowed;
			private boolean accepted;
			private boolean resultKnown;
			private boolean visible;
			private boolean attached;
			private boolean webVisible;
			private boolean webAttached;
			private boolean surfaceKnown;
			private boolean surfaceValid;
			private boolean browserFullscreen;
			private boolean appFullscreen;
			private boolean ownsPlayback;
			private boolean playing;
			private boolean muteKnown;
			private boolean muted;
			private int width;
			private int height;
			private int webWidth;
			private int webHeight;
			private int errorCode = Integer.MIN_VALUE;
			private long request;
			private long generation;
			private long revision;

			public Builder state(Enum<?> value) { state = value; return this; }
			public Builder mainFrame(boolean value) { mainFrame = value; return this; }
			public Builder allowed(boolean value) { allowed = value; return this; }
			public Builder result(boolean known, boolean value) {
				resultKnown = known;
				accepted = value;
				return this;
			}
			public Builder view(boolean value, boolean isAttached, int w, int h) {
				visible = value;
				attached = isAttached;
				width = Math.max(0, w);
				height = Math.max(0, h);
				return this;
			}
			public Builder web(boolean value, boolean isAttached, int w, int h) {
				webVisible = value;
				webAttached = isAttached;
				webWidth = Math.max(0, w);
				webHeight = Math.max(0, h);
				return this;
			}
			public Builder surface(boolean known, boolean valid) {
				surfaceKnown = known;
				surfaceValid = valid;
				return this;
			}
			public Builder fullscreen(boolean browser, boolean app) {
				browserFullscreen = browser;
				appFullscreen = app;
				return this;
			}
			public Builder ownsPlayback(boolean value) { ownsPlayback = value; return this; }
			public Builder playing(boolean value) { playing = value; return this; }
			public Builder mute(boolean known, boolean value) {
				muteKnown = known;
				muted = value;
				return this;
			}
			public Builder errorCode(int value) { errorCode = value; return this; }
			public Builder request(long value) { request = value; return this; }
			public Builder generation(long value) { generation = value; return this; }
			public Builder revision(long value) { revision = value; return this; }
			public DiagnosticsSnapshot build() { return new DiagnosticsSnapshot(this); }
		}
	}

	private static final DiagnosticsObserver DEFAULT_DIAGNOSTICS =
			new RuntimeDiagnosticsObserver();
	private final DiagnosticsObserver diagnosticsObserver;
	BooleanConsumer loading;
	private String failedMainFrameUrl;
	private String lastErrorKey;
	private String retryUrl;
	private int retryCount;
	private long retryGeneration;
	private ExternalNavigationPolicy externalNavigationPolicy;

	public FermataWebClient() {
		this(diagnosticsObserver());
	}

	protected FermataWebClient(DiagnosticsObserver observer) {
		diagnosticsObserver = (observer == null) ? diagnosticsObserver() : observer;
	}

	public static DiagnosticsObserver diagnosticsObserver() {
		return DEFAULT_DIAGNOSTICS;
	}

	protected final DiagnosticsObserver getDiagnosticsObserver() {
		return diagnosticsObserver;
	}

	private static final class RuntimeDiagnosticsObserver implements DiagnosticsObserver {
		@Override
		public void onPage(PageEvent event, DiagnosticsSnapshot snapshot) {
			record("page", event, snapshot, isPageError(event) ?
					DiagnosticPriority.ERROR : DiagnosticPriority.STATE);
		}

		@Override
		public void onCustomView(CustomViewEvent event, DiagnosticsSnapshot snapshot) {
			record("custom_view", event, snapshot, DiagnosticPriority.STATE);
		}

		@Override
		public void onFullscreen(FullscreenEvent event, DiagnosticsSnapshot snapshot) {
			record("fullscreen", event, snapshot,
					(event == FullscreenEvent.REQUEST_REJECTED ||
							event == FullscreenEvent.CUSTOM_VIEW_REJECTED) ?
							DiagnosticPriority.WARN : DiagnosticPriority.STATE);
		}

		@Override
		public void onPlayback(PlaybackEvent event, DiagnosticsSnapshot snapshot) {
			record("playback", event, snapshot,
					(event == PlaybackEvent.OWNERSHIP_LOST ||
						event == PlaybackEvent.SIGNAL_REJECTED) ?
							DiagnosticPriority.WARN : DiagnosticPriority.STATE);
		}

		private static boolean isPageError(PageEvent event) {
			return (event == PageEvent.MAIN_FRAME_ERROR) ||
					(event == PageEvent.MAIN_FRAME_HTTP_ERROR) ||
					(event == PageEvent.MAIN_FRAME_SSL_ERROR) ||
					(event == PageEvent.RENDERER_GONE);
		}

		private static void record(String group, Enum<?> event, DiagnosticsSnapshot snapshot,
				DiagnosticPriority priority) {
			try {
				Map<String, Object> data = new LinkedHashMap<>();
				if (snapshot != null) {
					if (snapshot.state != null) data.put("state", snapshot.state);
					data.put("main_frame", snapshot.mainFrame);
					data.put("allowed", snapshot.allowed);
					if (snapshot.resultKnown) data.put("accepted", snapshot.accepted);
					data.put("visible", snapshot.visible);
					data.put("attached", snapshot.attached);
					data.put("web_visible", snapshot.webVisible);
					data.put("web_attached", snapshot.webAttached);
					data.put("surface_known", snapshot.surfaceKnown);
					data.put("surface_valid", snapshot.surfaceValid);
					data.put("browser_fullscreen", snapshot.browserFullscreen);
					data.put("app_fullscreen", snapshot.appFullscreen);
					data.put("owns_playback", snapshot.ownsPlayback);
					data.put("playing", snapshot.playing);
					data.put("mute_known", snapshot.muteKnown);
					if (snapshot.muteKnown) data.put("muted", snapshot.muted);
					data.put("width", snapshot.width);
					data.put("height", snapshot.height);
					data.put("web_width", snapshot.webWidth);
					data.put("web_height", snapshot.webHeight);
					if (snapshot.errorCode != Integer.MIN_VALUE)
						data.put("error_code", snapshot.errorCode);
					data.put("request", snapshot.request);
					data.put("generation", snapshot.generation);
					data.put("revision", snapshot.revision);
				}
				AndroidDiagnosticsRuntime.get().recordEssential(
						"web_" + group, event.name().toLowerCase(Locale.ROOT), priority,
						null, data);
			} catch (RuntimeException ignored) {
				// Diagnostics must never affect WebView or playback behavior.
			}
		}
	}

	FermataWebClient createReplacement() {
		FermataWebClient replacement = newReplacement();
		if (replacement == null) {
			Log.e("WebViewClient replacement factory returned null");
			replacement = new FermataWebClient(getDiagnosticsObserver());
		}
		replacement.externalNavigationPolicy = externalNavigationPolicy;
		return replacement;
	}

	protected FermataWebClient newReplacement() {
		return new FermataWebClient(getDiagnosticsObserver());
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		boolean allowed = isExternalNavigationAllowed(url);
		if (!allowed) {
			diagnosticsObserver.onPage(PageEvent.MAIN_FRAME_STARTED,
					webSnapshot(view, true, false));
			view.stopLoading();
			if (view instanceof FermataWebView web) web.externalNavigationRejected();
			return;
		}
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
		diagnosticsObserver.onPage(PageEvent.MAIN_FRAME_STARTED,
				webSnapshot(view, true, true));
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		FermataWebView v = (FermataWebView) view;
		diagnosticsObserver.onPage(PageEvent.MAIN_FRAME_FINISHED,
				webSnapshot(view, true, true));
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
		if (!request.isForMainFrame()) return false;
		if (!isExternalNavigationAllowed(request.getUrl().toString())) {
			if (view instanceof FermataWebView web) web.externalNavigationRejected();
			return true;
		}
		// A policy-bound route remains on the Web surface. Routing it to another addon would
		// discard the Stremio owner and bypass the source policy.
		if (externalNavigationPolicy != null) return false;
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

	void setExternalNavigationPolicy(ExternalNavigationPolicy policy) {
		externalNavigationPolicy = policy;
	}

	void clearExternalNavigationPolicy(ExternalNavigationPolicy policy) {
		if (externalNavigationPolicy == policy) externalNavigationPolicy = null;
	}

	boolean isExternalNavigationAllowed(String value) {
		ExternalNavigationPolicy policy = externalNavigationPolicy;
		if (policy == null) return true;
		try {
			policy.validate(URI.create(value));
			return true;
		} catch (Exception rejected) {
			Log.w("Blocked policy-bound external Web navigation");
			return false;
		}
	}

	public static boolean isYoutubeUri(Uri uri) {
		if (uri == null) return false;
		String scheme = uri.getScheme();
		return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) &&
				isYoutubeHost(uri.getHost());
	}

	public static boolean isYoutubeHost(String host) {
		if (host == null) return false;
		host = host.toLowerCase(Locale.ROOT);
		boolean youtube = host.equals("youtube.com") || host.endsWith(".youtube.com");
		boolean television = host.equals("tv.youtube.com") || host.endsWith(".tv.youtube.com");
		return (youtube && !television) || host.equals("youtu.be");
	}

	@Override
	public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request,
														@NonNull WebResourceErrorCompat error) {
		boolean mainFrame = request.isForMainFrame();
		boolean hasDescription = mainFrame &&
				WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION);
		String desc = "unknown error";
		if (hasDescription) {
			desc = String.valueOf(error.getDescription());
		}
		String failureDescription = desc;
		dispatchResourceFailure(mainFrame,
				() -> {
					if (hasDescription) Log.e("Web error received: " + failureDescription);
					else Log.e("Web error received");
				},
				() -> diagnosticsObserver.onPage(PageEvent.MAIN_FRAME_ERROR,
						webSnapshot(view, true, true).toBuilder()
								.errorCode(error.getErrorCode()).build()));

		if (mainFrame) {
			failedMainFrameUrl = request.getUrl().toString();
			completeLoading(view);
			if (!scheduleAutoRetry(view, request.getUrl(), error.getErrorCode(), desc))
				showLoadError(view, request.getUrl(), desc);
		}

		super.onReceivedError(view, request, error);
	}

	static void dispatchResourceFailure(boolean mainFrame, Runnable legacyFailureLog,
			Runnable typedDiagnostics) {
		if (!mainFrame) return;
		legacyFailureLog.run();
		typedDiagnostics.run();
	}

	@Override
	public void onReceivedHttpError(@NonNull WebView view, @NonNull WebResourceRequest request,
																	@NonNull WebResourceResponse errorResponse) {
		if (request.isForMainFrame()) {
			String reason = "HTTP " + errorResponse.getStatusCode();
			String phrase = errorResponse.getReasonPhrase();
			if ((phrase != null) && !phrase.isEmpty()) reason += " " + phrase;
			Log.e("Web HTTP error received: " + reason);
			diagnosticsObserver.onPage(PageEvent.MAIN_FRAME_HTTP_ERROR,
					webSnapshot(view, true, true).toBuilder()
							.errorCode(errorResponse.getStatusCode()).build());
			failedMainFrameUrl = request.getUrl().toString();
			completeLoading(view);
			if (!scheduleAutoRetry(view, request.getUrl(),
					errorResponse.getStatusCode(), reason))
				showLoadError(view, request.getUrl(), reason);
		}

		super.onReceivedHttpError(view, request, errorResponse);
	}

	@Override
	public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
		handler.cancel();
		String url = error.getUrl();
		if (!isCurrentMainFrameSslError(view.getUrl(), url)) {
			Log.w("Ignored Web SSL error for subresource");
			return;
		}
		String reason = "SSL error";
		Log.e("Web SSL error received: " + reason);
		diagnosticsObserver.onPage(PageEvent.MAIN_FRAME_SSL_ERROR,
				webSnapshot(view, true, true).toBuilder()
						.errorCode(error.getPrimaryError()).build());
		failedMainFrameUrl = url;
		completeLoading(view);
		if (url != null) showLoadError(view, Uri.parse(url), reason);
	}

	/**
	 * {@link WebViewClient#onReceivedSslError} does not identify whether the failing request is the
	 * document or a subresource. A favicon must still be cancelled, but cannot fail the hosted page.
	 */
	static boolean isCurrentMainFrameSslError(String currentUrl, String errorUrl) {
		if ((currentUrl == null) || (errorUrl == null)) return false;
		try {
			URI current = URI.create(currentUrl);
			URI error = URI.create(errorUrl);
			return equalIgnoreCase(current.getScheme(), error.getScheme()) &&
					equalIgnoreCase(current.getHost(), error.getHost()) &&
					(current.getPort() == error.getPort()) &&
					equals(normalizePath(current.getPath()), normalizePath(error.getPath())) &&
					equals(current.getQuery(), error.getQuery());
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static boolean equalIgnoreCase(String first, String second) {
		return (first == null) ? (second == null) : first.equalsIgnoreCase(second);
	}

	private static boolean equals(String first, String second) {
		return (first == null) ? (second == null) : first.equals(second);
	}

	private static String normalizePath(String path) {
		return ((path == null) || path.isEmpty()) ? "/" : path;
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
		Log.e("WebView renderer gone, crashed: ", detail.didCrash());
		diagnosticsObserver.onPage(PageEvent.RENDERER_GONE,
				webSnapshot(view, true, true).toBuilder()
						.errorCode(detail.didCrash() ? 1 : 0).build());
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

	private boolean scheduleAutoRetry(WebView view, Uri uri, int errorCode, String reason) {
		if ((uri == null) || !isTransientLoadError(errorCode, reason)) return false;
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
		Log.e("Transient WebView error. Auto-retrying in ", delay, " ms: ", reason);
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
		return isTransientLoadError(Integer.MIN_VALUE, reason);
	}

	static boolean isTransientLoadError(int errorCode, String reason) {
		if ((errorCode == WebViewClient.ERROR_CONNECT) ||
				(errorCode == WebViewClient.ERROR_HOST_LOOKUP) ||
				(errorCode == WebViewClient.ERROR_IO) ||
				(errorCode == WebViewClient.ERROR_TIMEOUT) ||
				(errorCode == WebViewClient.ERROR_TOO_MANY_REQUESTS) ||
				(errorCode == 408) || (errorCode == 425) || (errorCode == 429) ||
				((errorCode >= 500) && (errorCode <= 504))) return true;
		if (reason == null) return false;
		String r = reason.toLowerCase(Locale.ROOT);
		return r.contains("timeout") || r.contains("timed_out") || r.contains("connection_timed_out") ||
				r.contains("connection timed out") || r.contains("host lookup") ||
				r.contains("name not resolved") || r.contains("connection reset") ||
				r.contains("connection refused") || r.contains("temporarily unavailable") ||
				r.contains("network_changed") || r.contains("network changed") ||
				r.contains("internet_disconnected") || r.contains("internet disconnected") ||
				r.contains("connection_closed") || r.contains("connection closed") ||
				r.contains("address_unreachable") || r.contains("address unreachable");
	}

	private static String getWebViewPackage() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			PackageInfo info = WebView.getCurrentWebViewPackage();
			if (info != null) return info.packageName + " " + info.versionName;
		}

		return "unknown";
	}

	private DiagnosticsSnapshot webSnapshot(WebView view, boolean mainFrame, boolean allowed) {
		if (view == null) {
			return DiagnosticsSnapshot.builder().mainFrame(mainFrame).allowed(allowed)
					.generation(retryGeneration).build();
		}
		return DiagnosticsSnapshot.builder().mainFrame(mainFrame).allowed(allowed)
				.view(view.getVisibility() == View.VISIBLE, view.isAttachedToWindow(),
						view.getWidth(), view.getHeight())
				.generation(retryGeneration).build();
	}
}
