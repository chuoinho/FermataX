package me.aap.fermata.addon.web.yt;

import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebClient extends FermataWebClient {
	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		MainActivityDelegate.get(view.getContext()).clearVoiceSelection();
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
		if (!isYoutubeUri(request.getUrl())) {
			MainActivityDelegate a = MainActivityDelegate.get(view.getContext());

			try {
				if (!(a.showFragment(
						me.aap.fermata.R.id.web_browser_fragment) instanceof WebBrowserFragment f))
					return false;
				f.loadUrl(request.getUrl().toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		super.onPageFinished(view, url);
		if (view instanceof YoutubeWebView youtube) youtube.collectVoiceSearchResults(url);
	}
}
