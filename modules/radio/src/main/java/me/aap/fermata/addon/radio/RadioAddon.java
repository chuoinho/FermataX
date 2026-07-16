package me.aap.fermata.addon.radio;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

@Keep
@SuppressWarnings("unused")
public class RadioAddon implements MediaLibAddon {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(RadioAddon.class.getName());
	private static RadioRootItem root;
	private final RadioRefreshCoordinator refreshCoordinator = new RadioRefreshCoordinator();

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.radio_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new RadioFragment();
	}

	@Override
	public boolean isSupportedItem(Item i) {
		return i instanceof RadioItem;
	}

	public synchronized RadioRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) {
			refreshCoordinator.reset();
			root = new RadioRootItem(lib);
		}
		return root;
	}

	synchronized RadioRootItem recreateRoot(DefaultMediaLib lib) {
		root = new RadioRootItem(lib);
		return root;
	}

	RadioRefreshCoordinator getRefreshCoordinator() {
		return refreshCoordinator;
	}

	@Override
	public void start() {
		refreshCoordinator.start();
	}

	@Override
	public void stop() {
		refreshCoordinator.stop();
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
																								String id) {
		return getRootItem(lib).getItem(scheme, id);
	}
}
