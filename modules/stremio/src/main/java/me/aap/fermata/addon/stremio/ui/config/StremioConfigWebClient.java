package me.aap.fermata.addon.stremio.ui.config;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class StremioConfigWebClient extends WebViewClient {
	private final StremioConfigWebController controller;
	private final StremioConfigUrlPolicy policy;

	StremioConfigWebClient(StremioConfigWebController controller,
			StremioConfigUrlPolicy policy) {
		this.controller = controller;
		this.policy = policy;
	}

	@Override
	public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
		if (!request.isForMainFrame()) return false;
		String url = request.getUrl().toString();
		if (request.hasGesture() &&
				(policy.evaluate(url) == StremioConfigUrlPolicy.Decision.COMPLETE) &&
				(view instanceof StremioConfigWebView configView)) {
			configView.verifyExplicitCompletionNavigation(url);
			return true;
		}
		return controller.handleNavigation(url);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean shouldOverrideUrlLoading(WebView view, String url) {
		return controller.handleNavigation(url);
	}

	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
		if (!"GET".equalsIgnoreCase(request.getMethod())) {
			return request.isForMainFrame() ? postFormErrorResponse() :
					blockedResponse(405, "Method blocked");
		}
		return load(request.getUrl().toString(), request.getRequestHeaders(), request.isForMainFrame());
	}

	@SuppressWarnings("deprecation")
	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
		return load(url, Map.of(), false);
	}

	WebResourceResponse loadWorker(WebResourceRequest request) {
		if (!"GET".equalsIgnoreCase(request.getMethod())) {
			return blockedResponse(405, "Method blocked");
		}
		return load(request.getUrl().toString(), request.getRequestHeaders(), false);
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (view instanceof StremioConfigWebView configView) configView.pageChanged(url);
		if (controller.handleNavigation(url)) return;
		controller.loading(true);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		controller.loading(false);
	}

	@Override
	public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
		if (request.isForMainFrame()) controller.fail(StremioConfigCallback.Failure.LOAD_FAILED);
	}

	@Override
	public void onReceivedHttpError(WebView view, WebResourceRequest request,
			WebResourceResponse errorResponse) {
		if (request.isForMainFrame()) controller.fail(StremioConfigCallback.Failure.LOAD_FAILED);
	}

	@Override
	public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
		handler.cancel();
		controller.fail(StremioConfigCallback.Failure.SSL_ERROR);
	}

	@Override
	public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType,
			SafeBrowsingResponse response) {
		response.backToSafety(true);
		controller.fail(StremioConfigCallback.Failure.LOAD_FAILED);
	}

	@Override
	public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
		controller.fail(StremioConfigCallback.Failure.RENDERER_GONE);
		controller.close();
		return true;
	}

	private WebResourceResponse load(String url, Map<String, String> headers, boolean mainFrame) {
		if (!policy.isAllowedResource(url, mainFrame)) return blockedResponse();
		try {
			StremioConfigResourceLoader.Response response =
					controller.loadResource(url, headers, mainFrame);
			String finalUrl = response.finalUri().toASCIIString();
			if (policy.evaluate(finalUrl) == StremioConfigUrlPolicy.Decision.COMPLETE) {
				new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
						controller.handleNavigation(finalUrl));
				return blockedResponse();
			}
			return resourceResponse(response);
		} catch (Exception error) {
			if (mainFrame) viewPostFailure();
			return blockedResponse(502, "Load failed");
		}
	}

	private void viewPostFailure() {
		// shouldInterceptRequest runs off the UI thread on Android.
		android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
		handler.post(() -> controller.fail(StremioConfigCallback.Failure.LOAD_FAILED));
	}

	private static WebResourceResponse resourceResponse(
			StremioConfigResourceLoader.Response response) {
		String contentType = response.headers().get("content-type");
		String mime = "application/octet-stream";
		String encoding = "UTF-8";
		if (contentType != null) {
			String[] parts = contentType.split(";");
			if (!parts[0].isBlank()) mime = parts[0].trim();
			for (int i = 1; i < parts.length; i++) {
				String part = parts[i].trim();
				if (part.toLowerCase(Locale.ROOT).startsWith("charset=")) {
					encoding = part.substring(8).trim();
				}
			}
		}
		LinkedHashMap<String, String> headers = new LinkedHashMap<>(response.headers());
		headers.remove("content-encoding");
		headers.remove("content-length");
		headers.remove("set-cookie");
		headers.remove("set-cookie2");
		headers.put("Cache-Control", "no-store");
		int status = response.status();
		if ((status < 200) || (status > 299)) return blockedResponse(status, "HTTP error");
		return new WebResourceResponse(mime, encoding, status, "OK", Map.copyOf(headers),
				new ByteArrayInputStream(response.body()));
	}

	private static WebResourceResponse blockedResponse() {
		return blockedResponse(403, "Blocked");
	}

	static WebResourceResponse postFormErrorResponse() {
		byte[] body = postFormErrorHtml().getBytes(StandardCharsets.UTF_8);
		return new WebResourceResponse("text/html", StandardCharsets.UTF_8.name(), 200, "OK",
				Map.of("Cache-Control", "no-store"), new ByteArrayInputStream(body));
	}

	static String postFormErrorHtml() {
		return "<!doctype html><html><head><meta name=viewport content=\"width=device-width\">" +
				"<style>body{font-family:sans-serif;background:#111;color:#fff;padding:24px;" +
				"line-height:1.45}button{min-height:48px;padding:0 24px;font-size:18px}</style>" +
				"</head><body><h1>Form could not be submitted</h1>" +
				"<p>This provider uses a POST form that cannot be sent securely in this setup " +
				"window. Go back and use the provider's link-based setup if available.</p>" +
				"<button type=button onclick=\"history.back()\">Go back</button></body></html>";
	}

	private static WebResourceResponse blockedResponse(int status, String reason) {
		byte[] empty = new byte[0];
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			return new WebResourceResponse("text/plain", StandardCharsets.UTF_8.name(), status, reason,
					Map.of("Cache-Control", "no-store"), new ByteArrayInputStream(empty));
		}
		return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(empty));
	}
}
