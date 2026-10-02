package me.aap.fermata.addon.web.tiktok;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * In-app TikTok Web Addon descriptor for FermataX.
 */
@Keep
public class TikTokAddon extends WebBrowserAddon {
	static final String HOME_URL = TikTokWebSessionPolicy.HOME_URL;
	private static final Pref<BooleanSupplier> HOME_REQUIRED = Pref.b("HOME_REQUIRED", false);

	public TikTokAddon() {
		super("tiktok", HOME_URL);
	}

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.tiktok_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return FermataAddon.findAddonInfo(TikTokAddon.class.getName());
	}

	@NonNull
	@Override
	public String getVoiceTarget() {
		return "tiktok";
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new TikTokWebFragment();
	}

	@Override
	public boolean handleVoiceSearch(MainActivityDelegate activity, String query, boolean play) {
		if (!(activity.showFragment(getAddonId()) instanceof TikTokWebFragment fragment)) return false;
		fragment.openSearch(query);
		return true;
	}

	@Override
	public void onAutomotiveShutdown() {
		requireHomeOnNextEntry();
		super.setLastUrl(HOME_URL);
		super.onAutomotiveShutdown();
	}

	boolean requiresHomeOnNextEntry() {
		return getPreferenceStore().getBooleanPref(HOME_REQUIRED);
	}

	void requireHomeOnNextEntry() {
		getPreferenceStore().applyBooleanPref(HOME_REQUIRED, true);
	}

	void onPageCommitted(String url) {
		if (requiresHomeOnNextEntry() && TikTokWebSessionPolicy.isHomeUrl(url)) {
			getPreferenceStore().applyBooleanPref(HOME_REQUIRED, false);
		}
	}

	@NonNull
	String getEntryUrl() {
		return TikTokWebSessionPolicy.entryUrl(requiresHomeOnNextEntry(), getLastUrl());
	}

	@Override
	protected String getLastUrl() {
		String url = super.getLastUrl();
		if (TikTokWebSessionPolicy.isPersistableRoute(url)) return url;
		return HOME_URL;
	}
}
