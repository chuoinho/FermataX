package me.aap.fermata.addon.web.stremio;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Supplier;

/** Hosts the official Stremio Web UI; media playback remains inside its HTML5 player. */
@Keep
@SuppressWarnings("unused")
public final class StremioWebAddon extends WebBrowserAddon {
	static final String HOME_URL = StremioWebSessionPolicy.HOME_URL;
	private static final Pref<BooleanSupplier> HOME_REQUIRED = Pref.b("HOME_REQUIRED", false);
	private static final Pref<Supplier<String>> LAST_DETAIL_URL = Pref.s("LAST_DETAIL_URL", HOME_URL);
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

	void beginExplicitNavigation() {
		getPreferenceStore().applyBooleanPref(HOME_REQUIRED, false);
	}

	void onPageCommitted(String url) {
		if (requiresHomeOnNextEntry() && StremioWebSessionPolicy.isHomeUrl(url)) {
			getPreferenceStore().applyBooleanPref(HOME_REQUIRED, false);
		}
	}

	@NonNull
	String getEntryUrl() {
		return StremioWebSessionPolicy.entryUrl(requiresHomeOnNextEntry(), getLastUrl());
	}

	@Override
	protected String getLastUrl() {
		String url = super.getLastUrl();
		if (StremioWebSessionPolicy.isPersistableRoute(url)) return url;

		String replacement = StremioWebSessionPolicy.replaceLegacyPlayerRoute(url,
				getPreferenceStore().getStringPref(LAST_DETAIL_URL));
		// A Player URL created by older builds may survive an update. Prefer the last exact
		// Stremio detail route; only use Home when that historical route no longer exists.
		super.setLastUrl(replacement);
		return replacement;
	}

	@Override
	protected void setLastUrl(String url) {
		if (!StremioWebSessionPolicy.isPersistableRoute(url)) return;
		super.setLastUrl(url);
		if (StremioWebSessionPolicy.isDetailRoute(url)) {
			getPreferenceStore().applyStringPref(LAST_DETAIL_URL, url);
		}
	}
}
