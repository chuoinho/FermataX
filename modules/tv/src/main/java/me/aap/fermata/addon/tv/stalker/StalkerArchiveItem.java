package me.aap.fermata.addon.tv.stalker;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.ArchiveItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

final class StalkerArchiveItem extends StalkerEpgItem implements ArchiveItem,
		PlayableItemPrefs, RemotePlaybackItem {
	private FutureSupplier<MediaMetadataCompat> metadata;

	StalkerArchiveItem(String id, StalkerTrackItem parent, StalkerEpgProgram program) {
		super(id, parent, program);
	}

	StalkerArchiveItem(StalkerEpgItem item) {
		super(item);
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

	@Override
	public boolean isSeekable() {
		return true;
	}

	@Override
	public boolean isLocationSensitive() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<MediaMetadataCompat> getMediaData() {
		FutureSupplier<MediaMetadataCompat> current = metadata;
		if (current != null) return current;
		MetadataBuilder builder = new MetadataBuilder();
		builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
		builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, description);
		builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, end - start);
		if (icon != null) builder.setImageUri(icon);
		return metadata = completed(builder.build());
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
		StalkerEpgItem item = getPrev();
		return (item instanceof StalkerArchiveItem archive) && !archive.isExpired() ?
				completed(archive) : completedNull();
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable() {
		StalkerEpgItem item = getNext();
		return (item instanceof StalkerArchiveItem archive) && !archive.isExpired() ?
				completed(archive) : completed(getParent());
	}

	@Override
	public long getExpirationTime() {
		return getParent().getCatchupExpirationTime(start);
	}

	@Override
	public boolean isExpired() {
		return !getParent().isCatchupSupported() || ArchiveItem.super.isExpired();
	}

	@Override
	public PlaybackRequestProfile getPlaybackRequestProfile() {
		StalkerSourceItem source = getParent().getStalkerSource();
		return StalkerPlayback.profile(source.getAccount().getPortalUri(),
				"stalker-archive-capability:" + source.getSourceId());
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback() {
		StalkerTrackItem track = getParent();
		if (!track.isCatalogCurrent() || isExpired()) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Stalker catch-up program is no longer available"));
		}
		StalkerSourceItem source = track.getStalkerSource();
		return StalkerPlayback.prepare(source.getApi().createArchiveLink(programId),
				"stalker-archive:" + source.getSourceId() + ':' + track.getChannelId() + ':' +
						programId);
	}

	@Override
	void scheduleReplacement() {
		long delay = getExpirationTime() - System.currentTimeMillis();
		if (delay < 0) return;
		App.get().getHandler().postDelayed(() -> {
			StalkerTrackItem track = getParent();
			if (track.isArchive(start, end)) return;
			track.replace(this, StalkerEpgItem::new);
		}, delay);
	}
}
