package me.aap.fermata.addon.tv.stalker;

interface StalkerCatalogItem {
	long getCatalogRevision();

	default StalkerSourceItem getStalkerSource() {
		if (this instanceof StalkerSourceItem source) return source;
		if (this instanceof StalkerCategoryItem category) return category.getParent();
		if (this instanceof StalkerTrackItem track) return track.getParent().getParent();
		throw new IllegalStateException("Unknown Stalker catalog item");
	}

	default boolean isCatalogCurrent() {
		return getCatalogRevision() == getStalkerSource().getCatalogRevision();
	}
}
