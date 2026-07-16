package me.aap.fermata.ui.policy;

import androidx.annotation.IdRes;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;

public final class ItemRoutePolicy {
	private ItemRoutePolicy() {
	}

	@IdRes
	public static int getFragmentId(Item i) {
		MediaLib.BrowsableItem root = i.getRoot();

		if (root instanceof MediaLib.Folders) return R.id.folders_fragment;
		if (root instanceof MediaLib.Favorites) return R.id.favorites_fragment;
		if (root instanceof MediaLib.Recent) return R.id.recent_fragment;
		if (root instanceof MediaLib.Playlists) return R.id.playlists_fragment;
		if (root instanceof ExtRoot extRoot) {
			AddonCapability capability = extRoot.getRouteCapability();
			if (capability != null) {
				int fragmentId = AddonManager.get().getFragmentId(capability);
				if (fragmentId != 0) return fragmentId;
			}
		}

		return AddonManager.get().getFragmentId(root);
	}

	@IdRes
	public static int getPlaybackOwnerFragmentId(PlayableItem item) {
		return getFragmentId(PlayableItemResolver.unwrap(item));
	}
}
