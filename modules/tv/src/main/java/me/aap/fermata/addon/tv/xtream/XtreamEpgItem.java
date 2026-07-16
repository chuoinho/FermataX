package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Locale;

import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemBase;
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class XtreamEpgItem extends ItemBase implements TvItem, EpgItem {
	public static final String SCHEME = "tvxepg";
	final long start;
	final long end;
	final String title;
	final String descr;
	final String icon;
	private XtreamEpgItem prev;
	private XtreamEpgItem next;

	XtreamEpgItem(String id, @NonNull XtreamTrackItem track, long start, long end,
								String title, String descr, String icon) {
		super(id, track, track.getResource());
		this.start = start;
		this.end = end;
		this.title = title;
		this.descr = descr;
		this.icon = icon;
	}

	XtreamEpgItem(XtreamArchiveItem i) {
		this(i.getId(), i.getParent(), i.start, i.end, i.title, i.descr, i.icon);
		set(i);
	}

	public static FutureSupplier<? extends Item> create(@NonNull TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int slash = id.indexOf('/');
		if (slash < 0) return completedNull();
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(XtreamTrackItem.SCHEME);
		tb.append(id, SCHEME.length(), slash);
		String trackId = tb.releaseString();
		FutureSupplier<? extends Item> f = root.getItem(XtreamTrackItem.SCHEME, trackId);
		return (f == null) ? completedNull() : f.then(t -> {
			if (t == null) return completedNull();
			return ((XtreamTrackItem) t).getEpg().map(l -> {
				for (XtreamEpgItem e : l) {
					if (id.equals(e.getId())) return e;
				}
				return t;
			});
		});
	}

	static XtreamEpgItem create(@NonNull XtreamTrackItem track, XtreamEpgProgram program) {
		String id = SCHEME + track.getId().substring(XtreamTrackItem.SCHEME.length()) +
				'/' + program.getStartTime() + '-' + program.getEndTime();
		DefaultMediaLib lib = (DefaultMediaLib) track.getLib();

		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);
			if (i != null) return (XtreamEpgItem) i;
			if (track.isArchive(program)) {
				return new XtreamArchiveItem(id, track, program.getStartTime(), program.getEndTime(),
						program.getTitle(), program.getDescription(), program.getIcon());
			}
			return new XtreamEpgItem(id, track, program.getStartTime(), program.getEndTime(),
					program.getTitle(), program.getDescription(), program.getIcon());
		}
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		return completed(title);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			if (descr != null) b.append(descr).append(".\n");
			Calendar c = Calendar.getInstance();
			Locale l = Locale.getDefault();
			c.setTimeInMillis(start);
			b.append(c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, l)).append(' ');
			b.append(c.get(Calendar.DAY_OF_MONTH)).append(", ");
			TextUtils.dateToTimeString(b, start, false);
			b.append(" - ");
			TextUtils.dateToTimeString(b, end, false);
			return completed(b.toString());
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return (icon == null) ? completedNull() : completed(Uri.parse(icon));
	}

	@NonNull
	@Override
	public XtreamTrackItem getParent() {
		return (XtreamTrackItem) super.getParent();
	}

	@Override
	public long getStartTime() {
		return start;
	}

	@Override
	public long getEndTime() {
		return end;
	}

	@Override
	public XtreamEpgItem getPrev() {
		return prev;
	}

	void setPrev(XtreamEpgItem prev) {
		this.prev = prev;
		if (prev instanceof XtreamArchiveItem) {
			scheduleReplacement();
		} else if ((prev == null) && (next == null)) {
			scheduleReplacement();
		}
	}

	@Override
	public XtreamEpgItem getNext() {
		return next;
	}

	void setNext(XtreamEpgItem next) {
		this.next = next;
		if (next instanceof XtreamArchiveItem) {
			next.scheduleReplacement();
		} else if ((next == null) && (prev == null)) {
			scheduleReplacement();
		}
	}

	void scheduleReplacement() {
		long delay = end + 1000 - System.currentTimeMillis();
		if (delay < 0) return;
		App.get().getHandler().postDelayed(() -> {
			XtreamTrackItem track = getParent();
			if (!track.isArchive(start, end)) return;
			track.replace(this, XtreamArchiveItem::new);
		}, delay);
	}
}
