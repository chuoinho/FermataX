package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Mirrors YouTube's immutable playback descriptor into the shared MediaLib Recent list. */
final class YoutubeRecentSync {
	private YoutubeRecentSync() {
	}

	static void add(@Nullable MainActivityDelegate activity, YoutubeAddon addon, YoutubeItem item) {
		if ((activity == null) || (item == null)) return;
		DefaultMediaLib lib = (DefaultMediaLib) activity.getLib();
		lib.getRecent().addItem(new YoutubeAddon.YoutubeHistoryItem(addon, lib, item));
	}
}
