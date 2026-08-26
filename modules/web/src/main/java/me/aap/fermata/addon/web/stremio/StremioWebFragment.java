package me.aap.fermata.addon.web.stremio;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Stremio-specific configuration of the reusable hosted WebView shell. */
@Keep
@SuppressWarnings("unused")
public final class StremioWebFragment extends WebBrowserFragment {
	static final String SEARCH_URL = "https://web.stremio.com/#/search?search=";

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.stremio_browser, container, false);
	}

	@Override
	protected void registerListeners(MainActivityDelegate activity) {
		super.registerListeners(activity);
		activity.addBroadcastListener(this, FRAGMENT_CHANGED);
	}

	@Override
	protected void unregisterListeners(MainActivityDelegate activity) {
		super.unregisterListeners(activity);
		activity.removeBroadcastListener(this);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate activity, long event) {
		super.onActivityEvent(activity, event);
		if (event == FRAGMENT_CHANGED) updateMediaSessionClaim(activity.getActiveFragment() == this);
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		updateMediaSessionClaim(!hidden);
	}

	private void updateMediaSessionClaim(boolean active) {
		FermataWebView web = getWebView();
		if (web instanceof StremioWebView stremio) {
			StremioWebMediaSessionBridge bridge = stremio.getMediaSessionBridge();
			if (bridge != null) bridge.onFragmentActiveChanged(active);
		}
	}

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
