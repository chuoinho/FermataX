package me.aap.fermata.addon.web.tiktok;

import android.graphics.Bitmap;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.web.FermataWebClient;

/**
 * Specialized WebViewClient for TikTok Web.
 * Keeps hosted routes within the WebView and informs bridges of frame transitions.
 */
final class TikTokWebClient extends FermataWebClient {

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (view instanceof TikTokWebView tikTokWebView) {
			tikTokWebView.onMainFrameStarted(url);
		}
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
			@NonNull WebResourceRequest request) {
		android.net.Uri uri = request.getUrl();
		if (uri == null) return false;
		String scheme = uri.getScheme();
		if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
			// Suppress non-web application schemes and intent redirects (e.g. snssdk1180://, intent://)
			return true;
		}

		String url = uri.toString();
		if (request.isForMainFrame()) {
			if (TikTokWebSessionPolicy.isHostedRoute(url)) {
				return false;
			}
		}
		return super.shouldOverrideUrlLoading(view, request);
	}

	@Override
	public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request,
			@NonNull androidx.webkit.WebResourceErrorCompat error) {
		if (request.getUrl() != null) {
			String scheme = request.getUrl().getScheme();
			if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
				return;
			}
		}
		super.onReceivedError(view, request, error);
	}

	@Override
	protected FermataWebClient newReplacement() {
		return new TikTokWebClient();
	}
}
