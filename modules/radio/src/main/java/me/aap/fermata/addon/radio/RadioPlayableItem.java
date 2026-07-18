package me.aap.fermata.addon.radio;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.vfs.VirtualResource;

/** A URL-backed station that still participates in Fermata's managed media lifecycle. */
abstract class RadioPlayableItem extends ExtPlayable implements RadioItem {
	RadioPlayableItem(String id, @NonNull BrowsableItem parent, @NonNull VirtualResource resource) {
		super(id, parent, resource);
	}

	@Override
	public final boolean isExternal() {
		return false;
	}

	@Override
	public final boolean isCacheable() {
		return false;
	}
}
