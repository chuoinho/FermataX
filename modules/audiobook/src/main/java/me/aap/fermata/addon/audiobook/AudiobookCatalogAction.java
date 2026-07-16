package me.aap.fermata.addon.audiobook;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import me.aap.fermata.addon.audiobook.catalog.LibriVoxCatalogClient;

enum AudiobookCatalogAction {
	SEARCH("search", R.string.audiobook_search_librivox, me.aap.fermata.R.drawable.search,
			LibriVoxCatalogClient.Sort.RELEVANCE),
	POPULAR("popular", R.string.audiobook_popular, me.aap.fermata.R.drawable.favorite,
			LibriVoxCatalogClient.Sort.POPULAR),
	LATEST("latest", R.string.audiobook_latest, me.aap.fermata.R.drawable.audiobook,
			LibriVoxCatalogClient.Sort.LATEST);

	final String id;
	@StringRes final int title;
	@DrawableRes final int icon;
	final LibriVoxCatalogClient.Sort sort;

	AudiobookCatalogAction(String id, int title, int icon, LibriVoxCatalogClient.Sort sort) {
		this.id = id;
		this.title = title;
		this.icon = icon;
		this.sort = sort;
	}
}
