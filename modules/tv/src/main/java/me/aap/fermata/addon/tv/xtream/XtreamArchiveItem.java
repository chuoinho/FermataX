package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.ArchiveItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
class XtreamArchiveItem extends XtreamEpgItem implements ArchiveItem, PlayableItemPrefs {
	private FutureSupplier<MediaMetadataCompat> md;

	XtreamArchiveItem(String id, @NonNull XtreamTrackItem track, long start, long end,
										String title, String descr, String icon) {
		super(id, track, start, end, title, descr, icon);
	}

	XtreamArchiveItem(XtreamEpgItem i) {
		this(i.getId(), i.getParent(), i.start, i.end, i.title, i.descr, i.icon);
		set(i);
	}

	@NonNull
	@Override
	public PlayableItemPrefs getPrefs() {
		return this;
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<MediaMetadataCompat> getMediaData() {
		FutureSupplier<MediaMetadataCompat> md = this.md;
		if (md != null) return md;
		MetadataBuilder b = new MetadataBuilder();
		b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
		b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, descr);
		b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, end - start);
		if (icon != null) b.setImageUri(icon);
		return this.md = completed(b.build());
	}

	@NonNull
	@Override
	public PlayableItem export(String exportId, MediaLib.BrowsableItem parent) {
		return getParent().export(exportId, parent);
	}

	@Override
	public String getOrigId() {
		return getId();
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable() {
		XtreamEpgItem prev = getPrev();
		return (prev instanceof XtreamArchiveItem) && !((XtreamArchiveItem) prev).isExpired()
				? completed((XtreamArchiveItem) prev) : completedNull();
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable() {
		XtreamEpgItem next = getNext();
		return (next instanceof XtreamArchiveItem) && !((XtreamArchiveItem) next).isExpired()
				? completed((XtreamArchiveItem) next) : completed(getParent());
	}

	@Override
	public long getExpirationTime() {
		return start + (24L * 60L * 60000L * getParent().getCatchUpDays());
	}

	@Override
	public boolean isExpired() {
		return !getParent().isCatchupSupported() || ArchiveItem.super.isExpired();
	}

	@Override
	public boolean isSeekable() {
		return getParent().isCatchupSupported();
	}

	@Override
	void scheduleReplacement() {
		long delay = getExpirationTime() - System.currentTimeMillis();
		if (delay < 0) return;
		App.get().getHandler().postDelayed(() -> {
			XtreamTrackItem track = getParent();
			if (track.isArchive(start, end)) return;
			track.replace(this, XtreamEpgItem::new);
		}, delay);
	}
}
