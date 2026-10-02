package me.aap.fermata.addon.web.tiktok;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.ui.activity.VoiceCommand;

/**
 * TikTok-specific configuration of the hosted WebView shell in FermataX.
 */
@Keep
@SuppressWarnings("unused")
public final class TikTokWebFragment extends WebBrowserFragment {

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.tiktok_browser, container, false);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.tiktok_fragment;
	}

	@Override
	protected boolean isDesktopVersionSupported() {
		return false;
	}

	@Override
	protected FermataWebClient createWebClient() {
		return new TikTokWebClient();
	}

	@Override
	protected String getSearchUrl() {
		return "https://www.tiktok.com/search?q=";
	}

	void openSearch(String query) {
		voiceCommand(new VoiceCommand(query, VoiceCommand.ACTION_FIND));
	}

	@Nullable
	@Override
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(TikTokAddon.class);
	}

	@Nullable
	public TikTokWebView getTikTokWebView() {
		FermataWebView web = getWebView();
		return (web instanceof TikTokWebView tikTokWebView) ? tikTokWebView : null;
	}
}
