package me.aap.fermata.addon.audiobook;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

enum AudiobookSection {
	CONTINUE("continue", R.string.audiobook_continue, me.aap.fermata.R.drawable.play),
	LIBRARY("library", R.string.audiobook_library, me.aap.fermata.R.drawable.audiobook),
	DOWNLOADS("downloads", R.string.audiobook_downloads, me.aap.fermata.R.drawable.save),
	DISCOVER("discover", R.string.audiobook_discover, me.aap.fermata.R.drawable.search),
	SOURCES("sources", R.string.audiobook_sources, me.aap.fermata.R.drawable.add_folder);

	final String id;
	@StringRes final int title;
	@DrawableRes final int icon;

	AudiobookSection(String id, int title, int icon) {
		this.id = id;
		this.title = title;
		this.icon = icon;
	}
}
