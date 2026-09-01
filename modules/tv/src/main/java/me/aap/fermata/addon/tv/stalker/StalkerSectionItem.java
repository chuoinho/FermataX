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
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.async.FutureSupplier;

public final class StalkerSectionItem extends BrowsableItemBase
		implements TvItem, StalkerCatalogItem {
	public static final String SCHEME = "tvss";
	public static final String TYPE_VOD = "vod";
	public static final String TYPE_SERIES = "series";
	private final String type;
	private final long catalogRevision;

	private StalkerSectionItem(String id, StalkerSourceItem parent, String type) {
		super(id, parent, null);
		this.type = type;
		catalogRevision = parent.getCatalogRevision();
	}

	public static StalkerSectionItem create(StalkerSourceItem parent, String type) {
		String id = toId(parent.getSourceId(), type);
		DefaultMediaLib lib = parent.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerSectionItem item = (StalkerSectionItem) cached;
				if (item.isCatalogCurrent()) return item;
				lib.removeFromCache(item);
			}
			return new StalkerSectionItem(id, parent, type);
		}
	}

	public static FutureSupplier<StalkerSectionItem> create(TvRootItem root, String id) {
		StalkerItemId.Section parsed = StalkerItemId.parseSection(id, SCHEME);
		FutureSupplier<? extends Item> sourceFuture = root.getItem(StalkerSourceItem.SCHEME,
				StalkerSourceItem.toId(parsed.sourceId()));
		return (sourceFuture == null) ? completedNull() : sourceFuture.map(item ->
				(item instanceof StalkerSourceItem source) ? create(source, parsed.type()) : null);
	}

	public static String toId(int sourceId, String type) {
		return StalkerItemId.section(SCHEME, sourceId, type);
	}

	public String getType() {
		return type;
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	@NonNull
	@Override
	public StalkerSourceItem getParent() {
		return (StalkerSourceItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		if (TYPE_VOD.equals(type)) return getLib().getContext().getString(R.string.xtream_movies);
		if (TYPE_SERIES.equals(type)) return getLib().getContext().getString(R.string.xtream_series);
		return type;
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		return completed(getName());
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		FutureSupplier<List<StalkerCategory>> categories = TYPE_VOD.equals(type) ?
				getParent().getApi().getVodCategories() : getParent().getApi().getSeriesCategories();
		return categories.map(values -> {
			List<Item> children = new ArrayList<>(values.size() + 1);
			boolean hasAll = false;
			for (StalkerCategory category : values) {
				hasAll |= StalkerCategoryItem.ALL.equals(category.id());
				children.add(StalkerContentCategoryItem.create(this, category));
			}
			if (!hasAll) {
				children.add(0, StalkerContentCategoryItem.create(this,
						new StalkerCategory(StalkerCategoryItem.ALL,
								getLib().getContext().getString(R.string.stalker_all_channels))));
			}
			return children;
		});
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}
}
