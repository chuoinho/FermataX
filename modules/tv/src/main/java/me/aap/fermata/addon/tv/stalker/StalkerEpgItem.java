package me.aap.fermata.addon.tv.stalker;

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
import me.aap.utils.vfs.VirtualResource;

public class StalkerEpgItem extends ItemBase implements TvItem, EpgItem {
	public static final String SCHEME = "tvsepg";
	final String programId;
	final long start;
	final long end;
	final String title;
	final String description;
	final String icon;
	private StalkerEpgItem previous;
	private StalkerEpgItem next;

	StalkerEpgItem(String id, StalkerTrackItem parent, StalkerEpgProgram program) {
		super(id, parent, parent.getResource());
		programId = program.id();
		start = program.startTime();
		end = program.endTime();
		title = program.title();
		description = program.description();
		icon = program.icon();
	}

	StalkerEpgItem(StalkerEpgItem item) {
		super(item.getId(), item.getParent(), item.getResource());
		programId = item.programId;
		start = item.start;
		end = item.end;
		title = item.title;
		description = item.description;
		icon = item.icon;
		previous = item.previous;
		next = item.next;
		if (previous != null) previous.next = this;
		if (next != null) next.previous = this;
		set(item);
	}

	public static FutureSupplier<? extends Item> create(TvRootItem root, String id) {
		int slash = id.indexOf('/');
		if (slash < 0) return completedNull();
		String trackId = StalkerTrackItem.SCHEME + id.substring(SCHEME.length(), slash);
		FutureSupplier<? extends Item> trackFuture = root.getItem(StalkerTrackItem.SCHEME, trackId);
		return (trackFuture == null) ? completedNull() : trackFuture.then(item -> {
			if (!(item instanceof StalkerTrackItem track)) return completedNull();
			return track.getEpg().map(programs -> {
				for (StalkerEpgItem program : programs) {
					if (id.equals(program.getId())) return program;
				}
				return track;
			});
		});
	}

	static StalkerEpgItem create(StalkerTrackItem parent, StalkerEpgProgram program) {
		String id = SCHEME + parent.getId().substring(StalkerTrackItem.SCHEME.length()) + '/' +
				program.startTime() + '-' + program.endTime();
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			boolean archive = parent.isArchive(program);
			Item cached = lib.getFromCache(id);
			if (cached instanceof StalkerEpgItem item) {
				if (archive == (item instanceof StalkerArchiveItem)) return item;
				lib.removeFromCache(item);
			}
			return archive ? new StalkerArchiveItem(id, parent, program) :
					new StalkerEpgItem(id, parent, program);
		}
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		return completed(title);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		try (SharedTextBuilder builder = SharedTextBuilder.get()) {
			if (description != null) builder.append(description).append(".\n");
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(start);
			builder.append(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT,
					Locale.getDefault())).append(' ').append(calendar.get(Calendar.DAY_OF_MONTH))
					.append(", ");
			TextUtils.dateToTimeString(builder, start, false);
			builder.append(" - ");
			TextUtils.dateToTimeString(builder, end, false);
			return completed(builder.toString());
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return (icon == null) ? completedNull() : completed(Uri.parse(icon));
	}

	@NonNull
	@Override
	public StalkerTrackItem getParent() {
		return (StalkerTrackItem) super.getParent();
	}

	@NonNull
	@Override
	public VirtualResource getResource() {
		return getParent().getResource();
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
	public StalkerEpgItem getPrev() {
		return previous;
	}

	void setPrev(StalkerEpgItem previous) {
		this.previous = previous;
		if (previous instanceof StalkerArchiveItem) {
			scheduleReplacement();
		} else if ((previous == null) && (next == null)) {
			scheduleReplacement();
		}
	}

	@Override
	public StalkerEpgItem getNext() {
		return next;
	}

	void setNext(StalkerEpgItem next) {
		this.next = next;
		if (next instanceof StalkerArchiveItem) {
			next.scheduleReplacement();
		} else if ((next == null) && (previous == null)) {
			scheduleReplacement();
		}
	}

	void scheduleReplacement() {
		long delay = end + 1000 - System.currentTimeMillis();
		if (delay < 0) return;
		App.get().getHandler().postDelayed(() -> {
			StalkerTrackItem track = getParent();
			if (!track.isArchive(start, end)) return;
			track.replace(this, StalkerArchiveItem::new);
		}, delay);
	}
}
