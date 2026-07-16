package me.aap.fermata.addon.tv;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.tv.m3u.TvM3uEpgItem;
import me.aap.fermata.addon.tv.m3u.TvM3uGroupItem;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.addon.tv.m3u.TvM3uTrackItem;
import me.aap.fermata.addon.tv.xtream.XtreamCatchupFolder;
import me.aap.fermata.addon.tv.xtream.XtreamCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamEpgItem;
import me.aap.fermata.addon.tv.xtream.XtreamEpisodeItem;
import me.aap.fermata.addon.tv.xtream.XtreamMovieItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeasonItem;
import me.aap.fermata.addon.tv.xtream.XtreamSectionItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeriesCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeriesItem;
import me.aap.fermata.addon.tv.xtream.XtreamSourceItem;
import me.aap.fermata.addon.tv.xtream.XtreamTrackItem;
import me.aap.fermata.addon.tv.xtream.XtreamVodCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamWatchFromBeginningItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

import static me.aap.utils.async.Completed.completed;

final class TvItemFactory {
	private final TvRootItem root;

	TvItemFactory(TvRootItem root) {
		this.root = root;
	}

	@Nullable
	FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return TvRootItem.ID.equals(id) ? completed(root) : null;

		switch (scheme) {
			case TvM3uItem.SCHEME:
				return root.createSource(toM3uSourceId(id));
			case TvM3uGroupItem.SCHEME:
				return TvM3uGroupItem.create(root, id);
			case TvM3uTrackItem.SCHEME:
				return TvM3uTrackItem.create(root, id);
			case TvM3uEpgItem.SCHEME:
				return TvM3uEpgItem.create(root, id);
			case XtreamSourceItem.SCHEME:
				return XtreamSourceItem.create(root, toXtreamSourceId(id));
			case XtreamSectionItem.SCHEME:
				return XtreamSectionItem.create(root, id);
			case XtreamCategoryItem.SCHEME:
				return XtreamCategoryItem.create(root, id);
			case XtreamTrackItem.SCHEME:
				return XtreamTrackItem.create(root, id);
			case XtreamEpgItem.SCHEME:
				return XtreamEpgItem.create(root, id);
			case XtreamCatchupFolder.SCHEME:
				return XtreamCatchupFolder.create(root, id);
			case XtreamWatchFromBeginningItem.SCHEME:
				return XtreamWatchFromBeginningItem.create(root, id);
			case XtreamVodCategoryItem.SCHEME:
				return XtreamVodCategoryItem.create(root, id);
			case XtreamMovieItem.SCHEME:
				return XtreamMovieItem.create(root, id);
			case XtreamSeriesCategoryItem.SCHEME:
				return XtreamSeriesCategoryItem.create(root, id);
			case XtreamSeriesItem.SCHEME:
				return XtreamSeriesItem.create(root, id);
			case XtreamSeasonItem.SCHEME:
				return XtreamSeasonItem.create(root, id);
			case XtreamEpisodeItem.SCHEME:
				return XtreamEpisodeItem.create(root, id);
			default:
				return null;
		}
	}

	private int toM3uSourceId(String id) {
		return Integer.parseInt(id.substring(TvM3uItem.SCHEME.length() + 1));
	}

	private int toXtreamSourceId(String id) {
		return Integer.parseInt(id.substring(XtreamSourceItem.SCHEME.length() + 1));
	}
}
