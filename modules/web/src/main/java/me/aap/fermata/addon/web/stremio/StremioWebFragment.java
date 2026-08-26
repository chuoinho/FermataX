package me.aap.fermata.addon.web.stremio;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.addon.web.WebBrowserFragment;

/** Stremio-specific configuration of the reusable hosted WebView shell. */
@Keep
@SuppressWarnings("unused")
public final class StremioWebFragment extends WebBrowserFragment {
	static final String SEARCH_URL = "https://web.stremio.com/#/search?search=";
	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.stremio_fragment;
	}

	@Override
	protected boolean isDesktopVersionSupported() {
		return false;
	}

	@Override
	protected FermataWebClient createWebClient() {
		return new StremioWebClient();
	}

	@Override
	protected String getSearchUrl() {
		return SEARCH_URL;
	}

	void openSearch(String query) {
		voiceCommand(new me.aap.fermata.ui.activity.VoiceCommand(query,
				me.aap.fermata.ui.activity.VoiceCommand.ACTION_FIND));
	}

	@Nullable
	@Override
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(StremioWebAddon.class);
	}
}
