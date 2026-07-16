package me.aap.fermata.addon.podcast;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

enum PodcastSection {
	CONTINUE("continue", R.string.podcast_continue, me.aap.fermata.R.drawable.play),
	NEW_EPISODES("new", R.string.podcast_new_episodes, me.aap.fermata.R.drawable.notification),
	SUBSCRIPTIONS("subscriptions", R.string.podcast_subscriptions,
			me.aap.fermata.R.drawable.podcast);

	final String id;
	@StringRes final int title;
	@DrawableRes final int icon;

	PodcastSection(String id, int title, int icon) {
		this.id = id;
		this.title = title;
		this.icon = icon;
	}
}
