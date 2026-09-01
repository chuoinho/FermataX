package me.aap.fermata.addon.tv.stalker;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

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

public final class StalkerContentCategoryItem extends BrowsableItemBase
		implements TvItem, StalkerCatalogItem {
	public static final String SCHEME = "tvscc";
	private final StalkerCategory category;
	private final long catalogRevision;

	private StalkerContentCategoryItem(String id, StalkerSectionItem parent,
			StalkerCategory category) {
		super(id, parent, null);
		this.category = category;
		catalogRevision = parent.getCatalogRevision();
	}

	public static StalkerContentCategoryItem create(StalkerSectionItem parent,
			StalkerCategory category) {
		String id = toId(parent.getParent().getSourceId(), parent.getType(), category.id(),
				category.name());
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerContentCategoryItem item = (StalkerContentCategoryItem) cached;
				if (item.isCatalogCurrent()) return item;
				lib.removeFromCache(item);
			}
			return new StalkerContentCategoryItem(id, parent, category);
		}
	}

	public static FutureSupplier<StalkerContentCategoryItem> create(TvRootItem root, String id) {
		StalkerItemId.ContentCategory parsed = StalkerItemId.parseContentCategory(id, SCHEME);
		FutureSupplier<? extends Item> sectionFuture = root.getItem(StalkerSectionItem.SCHEME,
				StalkerSectionItem.toId(parsed.sourceId(), parsed.type()));
		return (sectionFuture == null) ? completedNull() : sectionFuture.map(item ->
				(item instanceof StalkerSectionItem section) ? create(section,
						new StalkerCategory(parsed.categoryId(), parsed.categoryName())) : null);
	}

	public static String toId(int sourceId, String type, String categoryId, String name) {
		return StalkerItemId.contentCategory(SCHEME, sourceId, type, categoryId, name);
	}

	public StalkerCategory getCategory() {
		return category;
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	@NonNull
	@Override
	public StalkerSectionItem getParent() {
		return (StalkerSectionItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		return category.name();
	}

	public FutureSupplier<StalkerVodItem> getVod(String id) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if ((child instanceof StalkerVodItem vod) && vod.getVodId().equals(id)) return vod;
			}
			return null;
		});
	}

	public FutureSupplier<StalkerSeriesItem> getSeries(String id) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if ((child instanceof StalkerSeriesItem series) &&
						series.getSeriesId().equals(id)) return series;
			}
			return null;
		});
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		StalkerSourceItem source = getParent().getParent();
		if (StalkerSectionItem.TYPE_VOD.equals(getParent().getType())) {
			return source.getApi().getVod(category.id()).map(values -> {
				List<Item> children = new ArrayList<>(values.size());
				for (StalkerVod value : values) children.add(StalkerVodItem.create(this, value));
				return children;
			});
		}
		return source.getApi().getSeries(category.id()).map(values -> {
			List<Item> children = new ArrayList<>(values.size());
			for (StalkerSeries value : values) children.add(StalkerSeriesItem.create(this, value));
			return children;
		});
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		int resource = StalkerSectionItem.TYPE_VOD.equals(getParent().getType()) ?
				R.string.sub_movies : R.string.sub_series;
		return getLib().getContext().getString(resource, children.size());
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}
}
