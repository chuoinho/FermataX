package me.aap.fermata.addon.tv.stalker;

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
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.async.FutureSupplier;

public final class StalkerCatchupFolder extends BrowsableItemBase implements TvItem, EpgItem {
	public static final String SCHEME = "tvscf";
	static final String TYPE_LAST_24H = "last24";
	static final String TYPE_YESTERDAY = "yesterday";
	private final String type;

	private StalkerCatchupFolder(String id, StalkerTrackItem parent, String type) {
		super(id, parent, null);
		this.type = type;
	}

	public static FutureSupplier<? extends Item> create(TvRootItem root, String id) {
		int slash = id.lastIndexOf('/');
		if (slash < 0) return completedNull();
		String trackId = StalkerTrackItem.SCHEME + id.substring(SCHEME.length(), slash);
		String type = id.substring(slash + 1);
		FutureSupplier<? extends Item> trackFuture = root.getItem(StalkerTrackItem.SCHEME, trackId);
		return (trackFuture == null) ? completedNull() : trackFuture.map(item ->
				(item instanceof StalkerTrackItem track) ? create(track, type) : null);
	}

	static StalkerCatchupFolder create(StalkerTrackItem parent, String type) {
		String id = SCHEME + parent.getId().substring(StalkerTrackItem.SCHEME.length()) + '/' + type;
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			Item cached = lib.getFromCache(id);
			return (cached instanceof StalkerCatchupFolder folder) ? folder :
					new StalkerCatchupFolder(id, parent, type);
		}
	}

	@NonNull
	@Override
	public StalkerTrackItem getParent() {
		return (StalkerTrackItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(TYPE_YESTERDAY.equals(type) ?
				R.string.xtream_catchup_yesterday : R.string.xtream_catchup_last_24h);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		long[] range = range();
		return getParent().getEpg().map(programs -> {
			List<Item> children = new ArrayList<>();
			for (StalkerEpgItem item : programs) {
				if (!(item instanceof StalkerArchiveItem archive) || archive.isExpired()) continue;
				if ((item.getEndTime() > range[0]) && (item.getStartTime() < range[1])) {
					children.add(item);
				}
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
		if (!TYPE_YESTERDAY.equals(type)) return new long[]{now - 24L * 60L * 60000L, now};
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		long end = calendar.getTimeInMillis();
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		return new long[]{calendar.getTimeInMillis(), end};
	}
}
