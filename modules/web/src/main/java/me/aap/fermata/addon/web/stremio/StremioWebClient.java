package me.aap.fermata.addon.web.stremio;

import android.graphics.Bitmap;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.web.FermataWebClient;

/** Keeps hosted Stremio navigation in its WebView instead of launching arbitrary Android intents. */
final class StremioWebClient extends FermataWebClient {
	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (view instanceof StremioWebView stremio) stremio.onMainFrameStarted(url);
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
			@NonNull WebResourceRequest request) {
		if (request.isForMainFrame() && (view instanceof StremioWebView stremio) &&
				stremio.interceptPlayerRoute(request.getUrl().toString())) return true;
		if (request.isForMainFrame() &&
				StremioWebNavigationPolicy.blocksExternalScheme(request.getUrl().getScheme())) {
			return true;
		}
		return super.shouldOverrideUrlLoading(view, request);
	}

	@Override
	protected FermataWebClient newReplacement() {
		return new StremioWebClient();
	}
}
