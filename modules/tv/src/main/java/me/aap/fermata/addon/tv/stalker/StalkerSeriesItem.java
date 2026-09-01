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

public final class StalkerSeriesItem extends BrowsableItemBase
		implements TvItem, StalkerCatalogItem {
	public static final String SCHEME = "tvssr";
	private StalkerSeries series;
	private final long catalogRevision;

	private StalkerSeriesItem(String id, StalkerContentCategoryItem parent,
			StalkerSeries series) {
		super(id, parent, null);
		this.series = series;
		catalogRevision = parent.getCatalogRevision();
	}

	public static StalkerSeriesItem create(StalkerContentCategoryItem parent,
			StalkerSeries series) {
		StalkerCategory category = parent.getCategory();
		String id = StalkerItemId.content(SCHEME, parent.getStalkerSource().getSourceId(),
				parent.getParent().getType(), category.id(), category.name(), series.id());
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerSeriesItem item = (StalkerSeriesItem) cached;
				if (!item.isCatalogCurrent()) {
					lib.removeFromCache(item);
					return new StalkerSeriesItem(id, parent, series);
				}
				item.series = series;
				return item;
			}
			return new StalkerSeriesItem(id, parent, series);
		}
	}

	public static FutureSupplier<StalkerSeriesItem> create(TvRootItem root, String id) {
		StalkerItemId.Content parsed = StalkerItemId.parseContent(id, SCHEME);
		String categoryId = StalkerContentCategoryItem.toId(parsed.sourceId(), parsed.type(),
				parsed.categoryId(), parsed.categoryName());
		FutureSupplier<? extends Item> categoryFuture = root.getItem(
				StalkerContentCategoryItem.SCHEME, categoryId);
		return (categoryFuture == null) ? completedNull() : categoryFuture.then(item -> {
			StalkerContentCategoryItem category = (StalkerContentCategoryItem) item;
			return (category == null) ? completedNull() : category.getSeries(parsed.contentId());
		});
	}

	public String getSeriesId() {
		return series.id();
	}

	public StalkerSeries getSeries() {
		return series;
	}

	public FutureSupplier<StalkerSeasonItem> getSeason(String id) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if ((child instanceof StalkerSeasonItem season) &&
						season.getSeasonId().equals(id)) return season;
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
	public StalkerContentCategoryItem getParent() {
		return (StalkerContentCategoryItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		return series.name();
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return getStalkerSource().getApi().getSeasons(series.id()).map(values -> {
			List<Item> children = new ArrayList<>(values.size());
			for (StalkerSeason value : values) children.add(StalkerSeasonItem.create(this, value));
			return children;
		});
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.sub_seasons, children.size());
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return (series.logo() == null) ? completedNull() : completed(Uri.parse(series.logo()));
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}
}
