package me.aap.fermata.addon.web.stremio;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;

/** Hosts the official Stremio Web UI; media playback remains inside its HTML5 player. */
@Keep
@SuppressWarnings("unused")
public final class StremioWebAddon extends WebBrowserAddon {
	static final String HOME_URL = "https://web.stremio.com/#/";
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(StremioWebAddon.class.getName());

	public StremioWebAddon() {
		super("stremio_web", HOME_URL);
	}

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.stremio_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public String getVoiceTarget() {
		return "stremio";
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new StremioWebFragment();
	}

	@Override
	public boolean handleVoiceSearch(MainActivityDelegate activity, String query, boolean play) {
		if (!(activity.showFragment(getAddonId()) instanceof StremioWebFragment fragment)) return false;
		fragment.openSearch(query);
		return true;
	}
}
