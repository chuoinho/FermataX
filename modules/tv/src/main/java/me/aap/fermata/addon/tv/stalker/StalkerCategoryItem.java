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

public final class StalkerCategoryItem extends BrowsableItemBase
		implements TvItem, StalkerCatalogItem {
	public static final String SCHEME = "tvsc";
	public static final String ALL = "*";
	private final StalkerCategory category;
	private final long catalogRevision;

	private StalkerCategoryItem(String id, StalkerSourceItem parent, StalkerCategory category) {
		super(id, parent, null);
		this.category = category;
		catalogRevision = parent.getCatalogRevision();
	}

	public static StalkerCategoryItem create(StalkerSourceItem parent,
			StalkerCategory category) {
		String id = toId(parent.getSourceId(), category.id(), category.name());
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerCategoryItem item = (StalkerCategoryItem) cached;
				if (item.isCatalogCurrent()) return item;
				lib.removeFromCache(item);
			}
			return new StalkerCategoryItem(id, parent, category);
		}
	}

	public static FutureSupplier<StalkerCategoryItem> create(TvRootItem root, String id) {
		StalkerItemId.Category parsed = StalkerItemId.parseCategory(id, SCHEME);
		FutureSupplier<? extends Item> sourceFuture = root.getItem(StalkerSourceItem.SCHEME,
				StalkerSourceItem.toId(parsed.sourceId()));
		return (sourceFuture == null) ? completedNull() : sourceFuture.then(item -> {
			StalkerSourceItem source = (StalkerSourceItem) item;
			return (source == null) ? completedNull() : source.getCategory(parsed.categoryId(),
					parsed.categoryName());
		});
	}

	public static String toId(int sourceId, String categoryId, String name) {
		return StalkerItemId.category(SCHEME, sourceId, categoryId, name);
	}

	public FutureSupplier<StalkerTrackItem> getTrack(String channelId) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if ((child instanceof StalkerTrackItem track) &&
						track.getChannelId().equals(channelId)) return track;
			}
			return null;
		});
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
	public StalkerSourceItem getParent() {
		return (StalkerSourceItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		return category.name();
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return getParent().getApi().getChannels().map(channels -> {
			List<Item> children = new ArrayList<>();
			for (StalkerChannel channel : channels) {
				if (ALL.equals(category.id()) || category.id().equals(channel.categoryId())) {
					children.add(StalkerTrackItem.create(this, channel));
				}
			}
			return children;
		});
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.sub_ch, children.size());
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}
}
