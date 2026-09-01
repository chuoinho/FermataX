package me.aap.fermata.addon.tv.stalker;

import me.aap.fermata.media.lib.MediaLib.Item;

interface StalkerCatalogItem {
	long getCatalogRevision();

	default StalkerSourceItem getStalkerSource() {
		Item item = (Item) this;
		while (item != null) {
			if (item instanceof StalkerSourceItem source) return source;
			item = item.getParent();
		}
		throw new IllegalStateException("Unknown Stalker catalog item");
	}

	default boolean isCatalogCurrent() {
		return getCatalogRevision() == getStalkerSource().getCatalogRevision();
	}
}
