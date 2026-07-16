package me.aap.fermata.ui.fragment;

import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Recent;

final class DashboardCard {
	final DashboardItems.Item item;
	final PlayableItem playable;
	final int targetId;
	final int icon;
	final CharSequence title;
	final CharSequence subtitle;
	final boolean fixed;
	final boolean wide;
	final boolean playing;
	@Nullable
	final List<PlayableItem> recentItems;

	private DashboardCard(DashboardItems.Item item, PlayableItem playable, int targetId, int icon,
								CharSequence title, CharSequence subtitle, boolean fixed, boolean wide,
								boolean playing, @Nullable List<PlayableItem> recentItems) {
		this.item = item;
		this.playable = playable;
		this.targetId = targetId;
		this.icon = icon;
		this.title = title;
		this.subtitle = subtitle;
		this.fixed = fixed;
		this.wide = wide;
		this.playing = playing;
		this.recentItems = recentItems;
	}

	static DashboardCard item(DashboardItems.Item item) {
		return new DashboardCard(item, null, item.id, item.icon, item.title, item.subtitle,
				false, false, false, null);
	}

	DashboardCard withSubtitle(CharSequence subtitle) {
		return new DashboardCard(item, playable, targetId, icon, title, subtitle, fixed, wide,
				playing, recentItems);
	}

	static DashboardCard playable(PlayableItem playable, boolean playing,
												 @Nullable List<PlayableItem> recentItems) {
		return playable(playable, playable.getName(), playing, recentItems);
	}

	static DashboardCard playable(PlayableItem playable, CharSequence title, boolean playing,
												 @Nullable List<PlayableItem> recentItems) {
		String subtitle = "";
		if ((playable.getParent() != null) && !(playable.getParent() instanceof Recent)) {
			String parent = playable.getParent().getName();
			if (!TextUtils.isEmpty(parent)) subtitle = parent;
		}

		return new DashboardCard(null, playable, ID_NULL, playable.getIcon(), title,
				subtitle, true, true, playing, recentItems);
	}

	static DashboardCard recent(Context ctx, List<Item> items) {
		List<PlayableItem> recent = new ArrayList<>(3);
		for (Item item : items) {
			if (item instanceof PlayableItem playable) recent.add(playable);
			if (recent.size() == 3) break;
		}
		return new DashboardCard(null, null, R.id.recent_fragment, R.drawable.timer,
				ctx.getString(R.string.recent),
				itemSummary(items, ctx.getString(R.string.dashboard_recent_sub)),
				true, true, false, recent);
	}

	static CharSequence itemSummary(List<Item> items, CharSequence fallback) {
		List<CharSequence> names = new ArrayList<>(items.size());
		for (Item item : items) names.add(item.getName());
		return itemSummaryNames(names, fallback);
	}

	static CharSequence itemSummaryNames(List<? extends CharSequence> names, CharSequence fallback) {
		StringBuilder subtitle = new StringBuilder();
		int count = 0;

		for (CharSequence name : names) {
			if ((name == null) || (name.length() == 0)) continue;
			if (count++ != 0) subtitle.append(" - ");
			subtitle.append(name);
			if (count == 3) break;
		}

		return (count == 0) ? fallback : subtitle;
	}
}
