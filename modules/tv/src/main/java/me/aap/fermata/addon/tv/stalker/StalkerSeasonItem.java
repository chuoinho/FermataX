package me.aap.fermata.addon.tv.stalker;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StalkerSeasonItem extends BrowsableItemBase
		implements TvItem, StalkerCatalogItem {
	public static final String SCHEME = "tvssn";
	private StalkerSeason season;
	private final long catalogRevision;

	private StalkerSeasonItem(String id, StalkerSeriesItem parent, StalkerSeason season) {
		super(id, parent, null);
		this.season = season;
		catalogRevision = parent.getCatalogRevision();
	}

	public static StalkerSeasonItem create(StalkerSeriesItem parent, StalkerSeason season) {
		StalkerItemId.Content content = content(parent);
		String id = StalkerItemId.season(SCHEME, content, season.id());
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerSeasonItem item = (StalkerSeasonItem) cached;
				if (!item.isCatalogCurrent()) {
					lib.removeFromCache(item);
					return new StalkerSeasonItem(id, parent, season);
				}
				item.season = season;
				return item;
			}
			return new StalkerSeasonItem(id, parent, season);
		}
	}

	public static FutureSupplier<StalkerSeasonItem> create(TvRootItem root, String id) {
		StalkerItemId.Season parsed = StalkerItemId.parseSeason(id, SCHEME);
		String seriesId = StalkerItemId.content(StalkerSeriesItem.SCHEME, parsed.sourceId(),
				parsed.type(), parsed.categoryId(), parsed.categoryName(), parsed.contentId());
		FutureSupplier<? extends Item> seriesFuture = root.getItem(StalkerSeriesItem.SCHEME, seriesId);
		return (seriesFuture == null) ? completedNull() : seriesFuture.then(item -> {
			StalkerSeriesItem series = (StalkerSeriesItem) item;
			return (series == null) ? completedNull() : series.getSeason(parsed.seasonId());
		});
	}

	static StalkerItemId.Content content(StalkerSeriesItem parent) {
		StalkerContentCategoryItem categoryItem = parent.getParent();
		StalkerCategory category = categoryItem.getCategory();
		return new StalkerItemId.Content(parent.getStalkerSource().getSourceId(),
				categoryItem.getParent().getType(), category.id(), category.name(),
				parent.getSeriesId());
	}

	public String getSeasonId() {
		return season.id();
	}

	public int getSeasonNumber() {
		return season.number();
	}

	public StalkerSeason getSeason() {
		return season;
	}

	public FutureSupplier<StalkerEpisodeItem> getEpisode(String id) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if ((child instanceof StalkerEpisodeItem episode) &&
						episode.getEpisodeId().equals(id)) return episode;
			}
			return null;
		});
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	@NonNull
	@Override
	public StalkerSeriesItem getParent() {
		return (StalkerSeriesItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		return season.name();
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		List<Item> children = new ArrayList<>(season.episodes().size());
		for (StalkerEpisode episode : season.episodes()) {
			children.add(StalkerEpisodeItem.create(this, episode));
		}
		return completed(children);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.sub_episodes, children.size());
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return (season.logo() == null) ? completedNull() : completed(Uri.parse(season.logo()));
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}
}
