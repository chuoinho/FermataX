package me.aap.fermata.addon.podcast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

enum PodcastAction {
	SEARCH("search", R.id.podcast_search, R.string.podcast_search,
			me.aap.fermata.R.drawable.search),
	ADD_RSS("add-rss", R.id.podcast_add_rss, R.string.podcast_add_rss,
			me.aap.fermata.R.drawable.playlist_add),
	IMPORT_OPML("import-opml", R.id.podcast_import_opml, R.string.podcast_import_opml,
			me.aap.fermata.R.drawable.playlist),
	EXPORT_OPML("export-opml", R.id.podcast_export_opml, R.string.podcast_export_opml,
			me.aap.fermata.R.drawable.save);

	final String itemName;
	@IdRes
	final int menuId;
	@StringRes
	final int title;
	@DrawableRes
	final int icon;

	PodcastAction(String itemName, int menuId, int title, int icon) {
		this.itemName = itemName;
		this.menuId = menuId;
		this.title = title;
		this.icon = icon;
	}

	static PodcastAction fromMenuId(@IdRes int id) {
		for (PodcastAction action : values()) {
			if (action.menuId == id) return action;
		}
		return null;
	}
}
