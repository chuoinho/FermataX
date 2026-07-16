package me.aap.fermata.addon.tv.xtream;

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
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class XtreamSectionItem extends BrowsableItemBase implements TvItem {
	public static final String SCHEME = "tvxs";
	public static final String TYPE_LIVE = "live";
	public static final String TYPE_VOD = "vod";
	public static final String TYPE_SERIES = "series";
	private final String type;

	private XtreamSectionItem(String id, XtreamSourceItem parent, String type) {
		super(id, parent, null);
		this.type = type;
	}

	public static XtreamSectionItem create(XtreamSourceItem parent, String type) {
		String id = toId(parent.getSourceId(), type);
		DefaultMediaLib lib = parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			return (i != null) ? (XtreamSectionItem) i : new XtreamSectionItem(id, parent, type);
		}
	}

	public static FutureSupplier<XtreamSectionItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		if (end < 0) return completedNull();
		int sourceId = Integer.parseInt(id.substring(start, end));
		String type = id.substring(end + 1);
		FutureSupplier<? extends Item> f = root.getItem(XtreamSourceItem.SCHEME,
				XtreamSourceItem.toId(sourceId));
		return (f == null) ? completedNull() : f.then(i -> {
			XtreamSourceItem source = (XtreamSourceItem) i;
			return (source != null) ? source.getSection(type) : completedNull();
		});
	}

	public static String toId(int sourceId, String type) {
		return SharedTextBuilder.get().append(SCHEME).append(':').append(sourceId).append(':')
				.append(type).releaseString();
	}

	@NonNull
	@Override
	public XtreamSourceItem getParent() {
		return (XtreamSourceItem) super.getParent();
	}

	public String getType() {
		return type;
	}

	@NonNull
	@Override
	public String getName() {
		if (TYPE_LIVE.equals(type)) return getLib().getContext().getString(R.string.xtream_live_tv);
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
		if (TYPE_LIVE.equals(type)) {
			return getParent().getApi().getLiveCategories().map(categories -> {
				List<Item> children = new ArrayList<>(categories.size());
				for (XtreamCategory c : categories) children.add(XtreamCategoryItem.create(this, c));
				return children;
			});
		} else if (TYPE_VOD.equals(type)) {
			return getParent().getApi().getVodCategories().map(categories -> {
				List<Item> children = new ArrayList<>(categories.size());
				for (XtreamCategory c : categories) children.add(XtreamVodCategoryItem.create(this, c));
				return children;
			});
		} else if (TYPE_SERIES.equals(type)) {
			return getParent().getApi().getSeriesCategories().map(categories -> {
				List<Item> children = new ArrayList<>(categories.size());
				for (XtreamCategory c : categories) children.add(XtreamSeriesCategoryItem.create(this, c));
				return children;
			});
		}

		return completed(new ArrayList<>(0));
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
		return me.aap.fermata.R.drawable.tv;
	}
}
