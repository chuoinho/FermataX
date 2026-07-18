package me.aap.fermata.addon.tv.xtream;

import me.aap.fermata.media.lib.MediaLib.Item;

/** Marks cached Xtream catalog nodes with the account revision that produced their IDs. */
interface XtreamCatalogItem extends Item {
	long getCatalogRevision();

	default XtreamSourceItem getXtreamSource() {
		Item item = this;
		while (item != null) {
			if (item instanceof XtreamSourceItem source) return source;
			item = item.getParent();
		}
		throw new IllegalStateException("Xtream source is unavailable");
	}

	default boolean isCatalogCurrent() {
		return getXtreamSource().getCatalogRevision() == getCatalogRevision();
	}

	default XtreamAccount requireCurrentAccount() {
		return getXtreamSource().requireAccountRevision(getCatalogRevision());
	}

	static long revision(Item parent) {
		Item item = parent;
		while (item != null) {
			if (item instanceof XtreamSourceItem source) return source.getCatalogRevision();
			item = item.getParent();
		}
		throw new IllegalStateException("Xtream source is unavailable");
	}
}
