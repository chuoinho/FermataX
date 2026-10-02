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
		String url = request.getUrl().toString();
		if (request.isForMainFrame()) {
			if (TikTokWebSessionPolicy.isHostedRoute(url)) {
				return false;
			}
		}
		return super.shouldOverrideUrlLoading(view, request);
	}

	@Override
	protected FermataWebClient newReplacement() {
		return new TikTokWebClient();
	}
}
