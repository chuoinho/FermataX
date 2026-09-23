package me.aap.fermata.ui.fragment;

import android.content.Context;

import java.util.function.Supplier;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.utils.function.Consumer;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceView;

final class AddonPrefsBuilder
		implements Consumer<PreferenceView.Opts>, AddonManager.Listener {
	private final AddonManager manager;
	private final AddonInfo info;
	private final PreferenceStore store;
	private final Supplier<Context> contextSupplier;
	private PreferenceSet set;
	private boolean closed;

	AddonPrefsBuilder(AddonManager manager, AddonInfo info, PreferenceStore store,
						 Supplier<Context> contextSupplier) {
		this.manager = manager;
		this.info = info;
		this.store = store;
		this.contextSupplier = contextSupplier;
		manager.addBroadcastListener(this);
	}

	void configure(PreferenceSet set) {
		if (closed) return;
		this.set = set;

		set.addBooleanPref(o -> {
			o.title = R.string.enable;
			o.pref = info.enabledPref;
			o.store = store;
		});

		FermataAddon addon = manager.getAddon(info.className);
		if (addon != null) {
			ChangeableCondition condition = PrefCondition.create(store, info.enabledPref);
			addon.contributeSettings(contextSupplier.get(), store, set, condition);
		}
	}

	@Override
	public void accept(PreferenceView.Opts options) {
		options.title = info.addonName;
		options.icon = info.icon;
	}

	@Override
	public void onAddonChanged(AddonManager manager, AddonInfo info, boolean installed) {
		if (!closed && (set != null)) set.configure(this::configure);
	}

	void close() {
		if (closed) return;
		closed = true;
		set = null;
		manager.removeBroadcastListener(this);
	}
}
