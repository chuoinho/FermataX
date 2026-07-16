package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class XtreamCatchupFolder extends BrowsableItemBase implements TvItem, EpgItem {
	public static final String SCHEME = "tvxcf";
	static final String TYPE_LAST_24H = "last24";
	static final String TYPE_YESTERDAY = "yesterday";
	private final String type;

	private XtreamCatchupFolder(String id, XtreamTrackItem parent, String type) {
		super(id, parent, null);
		this.type = type;
	}

	public static FutureSupplier<? extends Item> create(@NonNull TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int slash = id.lastIndexOf('/');
		if (slash < 0) return completedNull();
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(XtreamTrackItem.SCHEME);
		tb.append(id, SCHEME.length(), slash);
		String trackId = tb.releaseString();
		String type = id.substring(slash + 1);
		FutureSupplier<? extends Item> f = root.getItem(XtreamTrackItem.SCHEME, trackId);
		return (f == null) ? completedNull() : f.map(i ->
				(i instanceof XtreamTrackItem) ? create((XtreamTrackItem) i, type) : null);
	}

	static XtreamCatchupFolder create(XtreamTrackItem parent, String type) {
		String id = SCHEME + parent.getId().substring(XtreamTrackItem.SCHEME.length()) +
				'/' + type;
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			return (i instanceof XtreamCatchupFolder) ? (XtreamCatchupFolder) i :
					new XtreamCatchupFolder(id, parent, type);
		}
	}

	@NonNull
	@Override
	public XtreamTrackItem getParent() {
		return (XtreamTrackItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		if (TYPE_YESTERDAY.equals(type)) {
			return getLib().getContext().getString(R.string.xtream_catchup_yesterday);
		}
		return getLib().getContext().getString(R.string.xtream_catchup_last_24h);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		long[] range = range();
		return getParent().getEpg().map(epg -> {
			List<Item> children = new ArrayList<>();
			for (XtreamEpgItem item : epg) {
				if (!(item instanceof XtreamArchiveItem)) continue;
				if (item instanceof XtreamWatchFromBeginningItem) continue;
				if ((item.getEndTime() <= range[0]) || (item.getStartTime() >= range[1])) continue;
				if (!((XtreamArchiveItem) item).isExpired()) children.add(item);
			}
			return children;
		});
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
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.xtream_catchup_programs, children.size());
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
		return me.aap.fermata.R.drawable.epg;
	}

	@Override
	public long getStartTime() {
		return range()[0];
	}

	@Override
	public long getEndTime() {
		return range()[1];
	}

	@Override
	public EpgItem getPrev() {
		return null;
	}

	@Override
	public EpgItem getNext() {
		return null;
	}

	private long[] range() {
		long now = System.currentTimeMillis();
		if (TYPE_YESTERDAY.equals(type)) {
			Calendar c = Calendar.getInstance();
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			long end = c.getTimeInMillis();
			c.add(Calendar.DAY_OF_MONTH, -1);
			return new long[]{c.getTimeInMillis(), end};
		}
		return new long[]{now - 24L * 60L * 60000L, now};
	}
}
