package me.aap.fermata.ui.fragment;

import me.aap.fermata.media.lib.MediaLib.Item;

/** Fragment capability for opening a resolved MediaLib item without concrete-type routing. */
public interface MediaItemNavigationTarget {
	void showMediaItem(Item item);
}
