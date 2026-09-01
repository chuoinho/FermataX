package me.aap.fermata.addon.tv.stalker;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.utils.async.Completed.completedNull;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Function;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

public final class StalkerTrackItem extends PlayableItemBase implements StreamItem,
		StreamItemPrefs, TvItem, StalkerCatalogItem, RemotePlaybackItem {
	public static final String SCHEME = "tvst";
	private static final long TIMELINE_AHEAD_TIME = 12L * 60L * 60000L;
	private static final int MAX_TIMELINE_PROGRAMS = 12;
	private final StalkerChannel channel;
	private final long catalogRevision;
	private volatile FutureSupplier<List<StalkerEpgItem>> epg;

	private StalkerTrackItem(String id, StalkerCategoryItem parent, StalkerChannel channel) {
		super(id, parent, GenericFileSystem.getInstance().create(
				parent.getParent().getAccount().getPortal()));
		this.channel = channel;
		catalogRevision = parent.getCatalogRevision();
	}

	public static StalkerTrackItem create(StalkerCategoryItem parent, StalkerChannel channel) {
		String id = toId(parent, channel);
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerTrackItem item = (StalkerTrackItem) cached;
				if (item.isCatalogCurrent()) return item;
				lib.removeFromCache(item);
			}
			return new StalkerTrackItem(id, parent, channel);
		}
	}

	public static FutureSupplier<StalkerTrackItem> create(TvRootItem root, String id) {
		StalkerItemId.Channel parsed = StalkerItemId.parseChannel(id, SCHEME);
		String categoryId = StalkerCategoryItem.toId(parsed.sourceId(), parsed.categoryId(),
				parsed.categoryName());
		FutureSupplier<? extends Item> categoryFuture = root.getItem(StalkerCategoryItem.SCHEME,
				categoryId);
		return (categoryFuture == null) ? completedNull() : categoryFuture.then(item -> {
			StalkerCategoryItem category = (StalkerCategoryItem) item;
			return (category == null) ? completedNull() : category.getTrack(parsed.channelId());
		});
	}

	private static String toId(StalkerCategoryItem parent, StalkerChannel channel) {
		StalkerCategory category = parent.getCategory();
		return StalkerItemId.channel(SCHEME, parent.getParent().getSourceId(), category.id(),
				category.name(), channel.id());
	}

	public String getChannelId() {
		return channel.id();
	}

	public synchronized FutureSupplier<List<StalkerEpgItem>> getEpg() {
		FutureSupplier<List<StalkerEpgItem>> current = epg;
		if (current != null) return current;
		FutureSupplier<List<StalkerEpgItem>> load = getParent().getParent().getApi()
				.getEpg(channel.id()).map(this::buildEpg);
		epg = load;
		load.onFailure(error -> clearFailedEpg(load));
		return load;
	}

	private synchronized void clearFailedEpg(FutureSupplier<List<StalkerEpgItem>> failed) {
		if (epg == failed) epg = null;
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
		return getEpg().map(programs -> {
			if (programs.isEmpty()) return Collections.emptyList();
			List<StalkerEpgItem> timeline = compactTimeline(programs, System.currentTimeMillis());
			List<EpgItem> children = new ArrayList<>(timeline.size() + 2);
			if (isCatchupSupported()) {
				children.add(StalkerCatchupFolder.create(this, StalkerCatchupFolder.TYPE_LAST_24H));
				children.add(StalkerCatchupFolder.create(this, StalkerCatchupFolder.TYPE_YESTERDAY));
			}
			children.addAll(timeline);
			return children;
		});
	}

	private static List<StalkerEpgItem> compactTimeline(List<StalkerEpgItem> programs, long now) {
		int start = -1;
		for (int i = 0; i < programs.size(); i++) {
			if (programs.get(i).getEndTime() > now) {
				start = i;
				break;
			}
		}
		if (start < 0) return Collections.singletonList(programs.get(programs.size() - 1));
		List<StalkerEpgItem> result = new ArrayList<>(MAX_TIMELINE_PROGRAMS);
		long until = now + TIMELINE_AHEAD_TIME;
		for (int i = start; i < programs.size() && result.size() < MAX_TIMELINE_PROGRAMS; i++) {
			StalkerEpgItem item = programs.get(i);
			if (!result.isEmpty() && (item.getStartTime() > until)) break;
			result.add(item);
		}
		return result;
	}

	private List<StalkerEpgItem> buildEpg(List<StalkerEpgProgram> programs) {
		if (programs.isEmpty()) return Collections.emptyList();
		programs.sort((first, second) -> Long.compare(first.startTime(), second.startTime()));
		List<StalkerEpgItem> result = new ArrayList<>(programs.size());
		for (StalkerEpgProgram program : programs) result.add(StalkerEpgItem.create(this, program));
		for (int i = 1; i < result.size(); i++) {
			StalkerEpgItem previous = result.get(i - 1);
			StalkerEpgItem next = result.get(i);
			previous.setNext(next);
			next.setPrev(previous);
		}
		return result;
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	@NonNull
	@Override
	public StalkerCategoryItem getParent() {
		return (StalkerCategoryItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		return channel.name();
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@Override
	public boolean isLocationSensitive() {
		return true;
	}

	@NonNull
	@Override
	public VirtualResource getResource() {
		return GenericFileSystem.getInstance().create(getParent().getParent().getAccount().getPortal());
	}

	@NonNull
	@Override
	public StreamItemPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		MetadataBuilder metadata = new MetadataBuilder();
		metadata.putString(METADATA_KEY_TITLE, getName());
		if (channel.logo() != null) metadata.setImageUri(channel.logo());
		return super.buildMeta(metadata);
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@Override
	public String getOrigId() {
		return getId();
	}

	@Override
	public String getUserAgent() {
		return getParent().getParent().getAccount().getUserAgent();
	}

	@Override
	public PlaybackRequestProfile getPlaybackRequestProfile() {
		StalkerAccount account = getParent().getParent().getAccount();
		return StalkerPlayback.profile(account.getPortalUri(),
				"stalker-capability:" + account.getSourceId());
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback() {
		StalkerSourceItem source = getParent().getParent();
		if (!isCatalogCurrent()) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Stalker portal catalog has changed"));
		}
		return StalkerPlayback.prepare(source.getApi().createLink(channel.command()),
				"stalker:" + source.getSourceId() + ':' + channel.id());
	}

	int getCatchupDays() {
		return channel.catchupDays();
	}

	boolean isCatchupSupported() {
		return getCatchupDays() > 0;
	}

	boolean isArchive(StalkerEpgProgram program) {
		return (program.archive() || isCatchupSupported()) &&
				isArchive(program.startTime(), program.endTime());
	}

	boolean isArchive(long start, long end) {
		if (!isCatchupSupported()) return false;
		long now = System.currentTimeMillis();
		return (end <= now) &&
				(start >= now - getCatchupDays() * 24L * 60L * 60000L);
	}

	long getCatchupExpirationTime(long start) {
		return start + getCatchupDays() * 24L * 60L * 60000L;
	}

	<From extends StalkerEpgItem, To extends StalkerEpgItem> void replace(From item,
			Function<From, To> convert) {
		FutureSupplier<List<StalkerEpgItem>> future = epg;
		if (future == null) return;
		List<StalkerEpgItem> programs = future.peek();
		if (programs == null) return;
		int index = programs.indexOf(item);
		if ((index < 0) || (programs.get(index) != item)) return;
		DefaultMediaLib lib = (DefaultMediaLib) getLib();
		To replacement;
		synchronized (lib.cacheLock()) {
			lib.removeFromCache(item);
			replacement = convert.apply(item);
		}
		programs.set(index, replacement);
	}

	@Override
	protected void reset() {
		super.reset();
		epg = null;
	}
}
