package me.aap.fermata.addon.tv.xtream;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.concurrent.ConcurrentUtils.ensureMainThread;
import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.event.ListenerLeakDetector;
import me.aap.utils.function.Function;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class XtreamTrackItem extends PlayableItemBase implements StreamItem, StreamItemPrefs, TvItem,
		XtreamCatalogItem {
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final AtomicReferenceFieldUpdater<XtreamTrackItem, FutureSupplier<List<XtreamEpgItem>>> EPG =
			(AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater.newUpdater(XtreamTrackItem.class, FutureSupplier.class, "epg");
	private static final long TIMELINE_AHEAD_TIME = 12L * 60L * 60000L;
	private static final int MAX_TIMELINE_PROGRAMS = 12;
	public static final String SCHEME = "tvxt";
	private final XtreamChannel channel;
	private final long catalogRevision;
	private long epgStart;
	private long epgStop;
	private String epgTitle;
	private String epgDesc;
	private String epgIcon;
	private String nextTitle;
	private List<Item.ChangeListener> listeners;
	private volatile FutureSupplier<List<XtreamEpgItem>> epg;

	private XtreamTrackItem(String id, XtreamCategoryItem parent, XtreamChannel channel) {
		super(id, parent,
				GenericFileSystem.getInstance().create(parent.getParent().getParent().getAccount()
						.buildLiveStreamUrl(channel.getStreamId())));
		this.channel = channel;
		catalogRevision = XtreamCatalogItem.revision(parent);
	}

	public static XtreamTrackItem create(XtreamCategoryItem parent, XtreamChannel channel) {
		String id = toId(parent, channel);
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			if (i != null) {
				XtreamTrackItem item = (XtreamTrackItem) i;
				if (item.isCatalogCurrent()) return item;
				lib.removeFromCache(item);
			}
			return new XtreamTrackItem(id, parent, channel);
		}
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	public static FutureSupplier<XtreamTrackItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		ParsedId parsed = parseId(id);
		String categoryId = XtreamCategoryItem.toId(parsed.sourceId, parsed.categoryId,
				parsed.categoryName);
		FutureSupplier<? extends Item> f = root.getItem(XtreamCategoryItem.SCHEME, categoryId);
		return (f == null) ? completedNull() : f.then(i -> {
			XtreamCategoryItem category = (XtreamCategoryItem) i;
			return (category != null) ? category.getTrack(parsed.streamId) : completedNull();
		});
	}

	public static String toId(XtreamCategoryItem parent, XtreamChannel channel) {
		XtreamCategory c = parent.getCategory();
		return XtreamItemId.stream(SCHEME, parent.getParent().getParent().getSourceId(),
				c.getId(), c.getName(), channel.getStreamId());
	}

	public int getStreamId() {
		return channel.getStreamId();
	}

	@Override
	public boolean isLocationSensitive() {
		return true;
	}

	@NonNull
	@Override
	public VirtualResource getResource() {
		return GenericFileSystem.getInstance().create(requireCurrentAccount()
				.buildLiveStreamUrl(getStreamId()));
	}

	@Override
	public boolean isRecentEligible() {
		return isCatalogCurrent();
	}

	@NonNull
	@Override
	@SuppressWarnings("unchecked")
	public FutureSupplier<List<XtreamEpgItem>> getEpg() {
		FutureSupplier<List<XtreamEpgItem>> l = EPG.get(this);
		if (l != null) return l;

		Promise<List<XtreamEpgItem>> load = new Promise<>();

		for (; !EPG.compareAndSet(this, null, load); l = EPG.get(this)) {
			if (l != null) return l;
		}

		getParent().getParent().getParent().getApi().getEpg(getStreamId()).map(this::buildEpg)
				.thenReplaceOrClear(EPG, this, load);
		l = EPG.get(this);
		return (l != null) ? l : load;
	}

	FutureSupplier<XtreamEpgItem> getCurrentEpg() {
		long time = System.currentTimeMillis();
		return getEpg().map(epg -> findEpg(epg, time));
	}

	@NonNull
	@Override
	public FutureSupplier<List<EpgItem>> getChildren() {
		return getTimelineChildren();
	}

	@NonNull
	@Override
	public FutureSupplier<List<EpgItem>> getUnsortedChildren() {
		return getTimelineChildren();
	}

	private FutureSupplier<List<EpgItem>> getTimelineChildren() {
		return getEpg().map(epg -> {
			if (epg.isEmpty()) return Collections.emptyList();
			long now = System.currentTimeMillis();
			List<XtreamEpgItem> programs = compactTimeline(epg, now);
			List<EpgItem> children = new ArrayList<>(programs.size() + 3);
			if (isCatchupSupported()) {
				XtreamEpgItem current = findEpg(epg, now);
				if ((current != null) && (current.getStartTime() < now)) {
					children.add(XtreamWatchFromBeginningItem.create(this, current));
				}
				children.add(XtreamCatchupFolder.create(this, XtreamCatchupFolder.TYPE_LAST_24H));
				children.add(XtreamCatchupFolder.create(this, XtreamCatchupFolder.TYPE_YESTERDAY));
			}
			children.addAll(programs);
			return children;
		});
	}

	private List<XtreamEpgItem> compactTimeline(List<XtreamEpgItem> epg, long now) {
		int startIdx = -1;
		for (int i = 0, size = epg.size(); i < size; i++) {
			if (epg.get(i).getEndTime() > now) {
				startIdx = i;
				break;
			}
		}

		if (startIdx < 0) return Collections.singletonList(epg.get(epg.size() - 1));

		long until = now + TIMELINE_AHEAD_TIME;
		List<XtreamEpgItem> timeline = new ArrayList<>(MAX_TIMELINE_PROGRAMS);
		for (int i = startIdx, size = epg.size(); i < size; i++) {
			XtreamEpgItem item = epg.get(i);
			if (!timeline.isEmpty() && (item.getStartTime() > until)) break;
			timeline.add(item);
			if (timeline.size() >= MAX_TIMELINE_PROGRAMS) break;
		}
		return timeline;
	}

	@NonNull
	@Override
	public XtreamCategoryItem getParent() {
		return (XtreamCategoryItem) super.getParent();
	}

	@NonNull
	@Override
	public StreamItemPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public String getName() {
		return channel.getName();
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return buildMeta(new MetadataBuilder());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		String desc = epgDesc;
		String progIcon = epgIcon;
		long start = epgStart;
		long stop = epgStop;
		long dur = (start > 0) && (start < stop) ? (stop - start) : 0;
		meta.putString(METADATA_KEY_TITLE, getName());
		meta.putString(METADATA_KEY_DISPLAY_SUBTITLE, SharedTextBuilder.apply(this::buildSubtitle));
		if (desc != null) meta.putString(METADATA_KEY_DISPLAY_DESCRIPTION, desc);
		if (progIcon != null) meta.putString(METADATA_KEY_DISPLAY_ICON_URI, progIcon);
		if (channel.getIcon() != null) meta.setImageUri(channel.getIcon());
		if (dur > 0) meta.putLong(METADATA_KEY_DURATION, dur);
		return super.buildMeta(meta);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		String t = md.getString(METADATA_KEY_DISPLAY_SUBTITLE);
		return (t != null) ? t : buildSubtitle(tb);
	}

	private String buildSubtitle(SharedTextBuilder tb) {
		String title = epgTitle;
		if (!isNullOrBlank(title)) tb.append(title);

		long start = epgStart;
		long stop = epgStop;
		if ((start > 0) && (start < stop)) {
			if (tb.length() != 0) tb.append(". ");
			TextUtils.dateToTimeString(tb, start, false);
			tb.append(" - ");
			TextUtils.dateToTimeString(tb, stop, false);
		}

		if (!isNullOrBlank(nextTitle)) {
			if (tb.length() != 0) tb.append('\n');
			tb.append(getLib().getContext().getString(me.aap.fermata.addon.tv.R.string.xtream_next))
					.append(": ").append(nextTitle);
		}

		return (tb.length() == 0) ? getParent().getName() : tb.toString();
	}

	@Override
	protected FutureSupplier<Bundle> buildExtras() {
		return getMediaData().map(m -> {
			long start = epgStart;
			long end = epgStop;
			if ((start <= 0) || (end < start)) return null;
			Bundle b = new Bundle();
			b.putLong(STREAM_START_TIME, start);
			b.putLong(STREAM_END_TIME, end);
			return b;
		});
	}

	@Override
	protected boolean isMediaDataValid(FutureSupplier<MediaMetadataCompat> d) {
		return validate(d);
	}

	@Override
	protected boolean isMediaDescriptionValid(FutureSupplier<MediaDescriptionCompat> d) {
		return validate(d);
	}

	private boolean validate(FutureSupplier<?> d) {
		return (d != null) && (!d.isDone() || (epgStop == 0) ||
				(epgStop > System.currentTimeMillis()));
	}

	@Override
	public boolean isSeekable() {
		return isCatchupSupported() && (epgStart > 0) && (epgStart < epgStop);
	}

	@Override
	public boolean isSeekable(long time) {
		int days = getCatchUpDays();
		if ((days <= 0) || !isCatchupSupported()) return false;
		long now = System.currentTimeMillis();
		return (time <= now) && (time >= getArchiveStartTime(now));
	}

	@Nullable
	@Override
	public Uri getLocation(long time, long duration) {
		if (!isSeekable(time)) return null;
		long now = System.currentTimeMillis();
		if (time >= now) return getLocation();
		return Uri.parse(requireCurrentAccount()
				.buildTimeshiftStreamUrl(getStreamId(), time, duration));
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@Override
	public String getOrigId() {
		return XtreamItemId.orig(getId(), SCHEME);
	}

	@Nullable
	@Override
	public String getUserAgent() {
		return requireCurrentAccount().getUserAgent();
	}

	int getCatchUpDays() {
		return channel.getTvArchiveDuration();
	}

	boolean isCatchupSupported() {
		return channel.isTvArchive() && (getCatchUpDays() > 0);
	}

	boolean isArchive(XtreamEpgProgram program) {
		return (program.hasArchive() || isCatchupSupported()) &&
				isArchive(program.getStartTime(), program.getEndTime());
	}

	boolean isArchive(long start, long end) {
		if (!isCatchupSupported()) return false;
		long now = System.currentTimeMillis();
		return (end <= now) && (start >= getArchiveStartTime(now));
	}

	long getArchiveStartTime(long time) {
		return time - (getCatchUpDays() * 24L * 60L * 60000L);
	}

	long getCatchupExpirationTime(long start) {
		return start + (getCatchUpDays() * 24L * 60L * 60000L);
	}

	private List<XtreamEpgItem> buildEpg(List<XtreamEpgProgram> programs) {
		if (programs.isEmpty()) {
			updateEpg(0, 0, null, null, null, null, false);
			return Collections.emptyList();
		}

		Collections.sort(programs, (a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
		List<XtreamEpgItem> items = new ArrayList<>(programs.size());
		for (XtreamEpgProgram program : programs) items.add(XtreamEpgItem.create(this, program));

		for (int i = 1, size = items.size(); i < size; i++) {
			XtreamEpgItem prev = items.get(i - 1);
			XtreamEpgItem cur = items.get(i);
			prev.setNext(cur);
			cur.setPrev(prev);
		}
		items.get(0).setPrev(null);
		items.get(items.size() - 1).setNext(null);
		updateCurrent(items, true);
		return items;
	}

	private void updateCurrent(List<XtreamEpgItem> items, boolean force) {
		long now = System.currentTimeMillis();
		XtreamEpgItem current = findEpg(items, now);
		XtreamEpgItem next = null;
		if (current != null) {
			XtreamEpgItem n = current.getNext();
			if (n != null) next = n;
		} else {
			for (XtreamEpgItem item : items) {
				if (item.getStartTime() > now) {
					next = item;
					break;
				}
			}
		}

		updateEpg((current != null) ? current.getStartTime() : 0,
				(current != null) ? current.getEndTime() : 0,
				(current != null) ? current.title : null,
				(current != null) ? current.descr : null,
				(current != null) ? current.icon : null,
				(next != null) ? next.title : null,
				force);
	}

	private XtreamEpgItem findEpg(List<XtreamEpgItem> items, long time) {
		for (XtreamEpgItem item : items) {
			if ((item.getStartTime() <= time) && (item.getEndTime() > time)) return item;
		}
		return null;
	}

	private void updateEpg(long start, long stop, String title, String desc, String icon,
												 String nextTitle, boolean force) {
		App.get().run(() -> {
			epgStart = start;
			epgStop = stop;
			epgTitle = title;
			epgDesc = desc;
			epgIcon = icon;
			this.nextTitle = nextTitle;

			if (force) {
				super.reset();
				notifyListeners();
			}
		});
	}

	@Override
	public boolean addChangeListener(Item.ChangeListener l) {
		ensureMainThread(true);
		List<Item.ChangeListener> listeners = this.listeners;
		if (listeners == null) this.listeners = listeners = new LinkedList<>();
		else if (listeners.contains(l)) return true;
		listeners.add(l);
		if (BuildConfig.D) ListenerLeakDetector.add(this, l);
		return true;
	}

	@Override
	public boolean removeChangeListener(Item.ChangeListener l) {
		ensureMainThread(true);
		List<Item.ChangeListener> listeners = this.listeners;
		if ((listeners == null) || !listeners.remove(l)) return false;
		if (BuildConfig.D) ListenerLeakDetector.remove(this, l);
		return true;
	}

	@Override
	protected void reset() {
		super.reset();
		epg = null;
	}

	<From extends XtreamEpgItem, To extends XtreamEpgItem> void replace(From i,
																																		 Function<From, To> convert) {
		FutureSupplier<List<XtreamEpgItem>> f = EPG.get(this);
		if (f == null) return;
		List<XtreamEpgItem> l = f.peek();
		if (l == null) return;
		int idx = Collections.binarySearch(l, i);
		XtreamEpgItem old = (idx < 0) ? null : l.get(idx);
		if (old != i) return;
		DefaultMediaLib lib = (DefaultMediaLib) getLib();
		XtreamEpgItem repl;

		synchronized (lib.cacheLock()) {
			lib.removeFromCache(i);
			repl = convert.apply(i);
		}

		XtreamEpgItem prev = i.getPrev();
		XtreamEpgItem next = i.getNext();
		l.set(idx, repl);
		if (prev != null) {
			repl.setPrev(prev);
			prev.setNext(repl);
		}
		if (next != null) {
			repl.setNext(next);
			next.setPrev(repl);
		}
		notifyListeners();
	}

	private void notifyListeners() {
		List<Item.ChangeListener> listeners = this.listeners;
		if ((listeners != null) && !listeners.isEmpty()) {
			for (Item.ChangeListener l : listeners) l.mediaItemChanged(this);
		}
	}

	static ParsedId parseId(String id) {
		XtreamItemId.Stream parsed = XtreamItemId.stream(id);
		return new ParsedId(parsed.sourceId, parsed.categoryId, parsed.categoryName,
				parsed.streamId);
	}

	static final class ParsedId {
		final int sourceId;
		final String categoryId;
		final String categoryName;
		final int streamId;

		ParsedId(int sourceId, String categoryId, String categoryName, int streamId) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.categoryName = categoryName;
			this.streamId = streamId;
		}
	}
}
