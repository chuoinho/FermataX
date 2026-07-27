package me.aap.fermata.addon.stremio;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

enum StremioAction {
	SEARCH(R.string.stremio_search, me.aap.fermata.R.drawable.search),
	ADD(R.string.stremio_add_addon, me.aap.fermata.R.drawable.playlist_add),
	ADDONS(R.string.stremio_installed_addons, me.aap.fermata.R.drawable.playlist);

	@StringRes
	final int title;
	@DrawableRes
	final int icon;

	StremioAction(@StringRes int title, @DrawableRes int icon) {
		this.title = title;
		this.icon = icon;
	}
}
