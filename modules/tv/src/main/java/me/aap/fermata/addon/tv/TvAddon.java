package me.aap.fermata.addon.tv;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.addon.VoiceSearchAddon;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class TvAddon implements MediaLibAddon, VoiceSearchAddon {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(TvAddon.class.getName());
	private static TvRootItem root;
	private final TvSourceRefreshCoordinator refreshCoordinator =
			new TvSourceRefreshCoordinator();

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.tv_fragment;
	}

	@NonNull
	@Override
	public String getVoiceTarget() {
		return "tv";
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new TvFragment();
	}

	@Override
	public boolean isSupportedItem(Item i) {
		return (i instanceof TvItem);
	}

	public synchronized TvRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) {
			refreshCoordinator.reset();
			root = new TvRootItem(lib);
		}
		return root;
	}

	TvSourceRefreshCoordinator getRefreshCoordinator() {
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
