package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class XtreamWatchFromBeginningItem extends XtreamArchiveItem {
	public static final String SCHEME = "tvxwb";

	private XtreamWatchFromBeginningItem(String id, @NonNull XtreamTrackItem track,
																			 XtreamEpgItem current) {
		super(id, track, current.start, Math.max(System.currentTimeMillis(), current.start + 60000L),
				track.getLib().getContext().getString(R.string.xtream_watch_from_beginning),
				current.title, current.icon);
	}

	public static FutureSupplier<? extends Item> create(@NonNull TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int slash = id.indexOf('/');
		if (slash < 0) return completedNull();
		int dash = id.indexOf('-', slash + 1);
		if (dash < 0) return completedNull();
		long start;
		long end;

		try {
			start = Long.parseLong(id.substring(slash + 1, dash));
			end = Long.parseLong(id.substring(dash + 1));
		} catch (NumberFormatException ex) {
			return completedNull();
		}

		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(XtreamTrackItem.SCHEME);
		tb.append(id, SCHEME.length(), slash);
		String trackId = tb.releaseString();
		FutureSupplier<? extends Item> f = root.getItem(XtreamTrackItem.SCHEME, trackId);
		return (f == null) ? completedNull() : f.then(i -> {
			if (!(i instanceof XtreamTrackItem)) return completedNull();
			XtreamTrackItem track = (XtreamTrackItem) i;
			return track.getEpg().map(epg -> {
				for (XtreamEpgItem item : epg) {
					if ((item.getStartTime() == start) && (item.getEndTime() == end)) {
						return create(track, item);
					}
				}
				return null;
			});
		});
	}

	static XtreamWatchFromBeginningItem create(@NonNull XtreamTrackItem track,
																						 XtreamEpgItem current) {
		String id = SCHEME + track.getId().substring(XtreamTrackItem.SCHEME.length()) +
				'/' + current.getStartTime() + '-' + current.getEndTime();
		DefaultMediaLib lib = (DefaultMediaLib) track.getLib();

		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);
			return (i instanceof XtreamWatchFromBeginningItem) ?
					(XtreamWatchFromBeginningItem) i :
					new XtreamWatchFromBeginningItem(id, track, current);
		}
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable() {
		return completedNull();
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable() {
		return completed(getParent());
	}

	@Override
	public long getExpirationTime() {
		return getParent().getCatchupExpirationTime(start);
	}
}
