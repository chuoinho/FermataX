package me.aap.fermata.addon.podcast;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.utils.async.Completed.completed;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import me.aap.fermata.addon.podcast.data.PodcastPlaybackSource;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.generic.GenericFileSystem;

final class PodcastEpisodeItem extends ExtPlayable implements PodcastItem, PlaybackProgressItem {
	private final PodcastEpisodeRecord episode;
	private final PodcastPlaybackSource playback;
	private final ArtworkLoader artwork;
	private final ProgressStore progressStore;
	private final File downloadedFile;
	private volatile long resumePosition;

	PodcastEpisodeItem(BrowsableItem parent, PodcastEpisodeRecord episode,
			PodcastPlaybackSource playback, String artworkUrl) {
		this(parent, episode, playback, artworkUrl, null);
	}

	PodcastEpisodeItem(BrowsableItem parent, PodcastEpisodeRecord episode,
			PodcastPlaybackSource playback, String artworkUrl, ProgressStore progressStore) {
		this(parent, episode, playback, () -> completed(artworkUrl), progressStore);
	}

	PodcastEpisodeItem(BrowsableItem parent, PodcastEpisodeRecord episode,
			PodcastPlaybackSource playback, ArtworkLoader artwork, ProgressStore progressStore) {
		this(parent, episode, playback, artwork, progressStore, null);
	}

	PodcastEpisodeItem(BrowsableItem parent, PodcastEpisodeRecord episode,
			PodcastPlaybackSource playback, ArtworkLoader artwork, ProgressStore progressStore,
			@Nullable File downloadedFile) {
		super(PodcastRootItem.episodeId(episode.getFeedKey(), episode.getEpisodeKey()), parent,
				GenericFileSystem.getInstance().create(playback.getUrl()));
		this.episode = episode;
		this.playback = playback;
		this.artwork = artwork;
		this.progressStore = progressStore;
		this.downloadedFile = downloadedFile;
		resumePosition = Math.max(episode.getProgressMs(), 0);
	}

	PodcastEpisodeRecord getEpisode() {
		return episode;
	}

	@NonNull
	@Override
	public String getName() {
		String title = episode.getTitle();
		return title.isEmpty() ? episode.getFeedTitle() : title;
	}

	@NonNull
	@Override
	public Uri getLocation() {
		return isDownloaded() ? Uri.fromFile(downloadedFile) : Uri.parse(playback.getUrl());
	}

	@NonNull
	@Override
	public Map<String, String> getRequestHeaders() {
		return isDownloaded() ? Collections.emptyMap() : playback.getHeaders();
	}

	boolean isDownloaded() {
		return (downloadedFile != null) && downloadedFile.isFile();
	}

	@Override
	public boolean isExternal() {
		return false;
	}

	@Override
	public boolean isStream() {
		return false;
	}

	@Override
	public boolean isLocationSensitive() {
		return (episode.getMediaCredentialRef() != null) || !playback.getHeaders().isEmpty();
	}

	@Override
	public boolean isSeekable() {
		return true;
	}

	@Override
	public long getResumePosition() {
		return resumePosition;
	}

	@Override
	public FutureSupplier<Void> savePlaybackProgress(long position, boolean completed) {
		resumePosition = Math.max(position, 0);
		if (progressStore == null) return me.aap.utils.async.Completed.completedVoid();
		return progressStore.update(episode.getFeedKey(), episode.getEpisodeKey(), resumePosition,
				completed, System.currentTimeMillis());
	}

	@Override
	public boolean isVideo() {
		return episode.getMimeType().toLowerCase(java.util.Locale.ROOT).startsWith("video/");
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		MetadataBuilder metadata = new MetadataBuilder();
		metadata.putString(METADATA_KEY_TITLE, getName());
		metadata.putString(METADATA_KEY_ALBUM, episode.getFeedTitle());
		if (!episode.getAuthor().isEmpty()) metadata.putString(METADATA_KEY_ARTIST,
				episode.getAuthor());
		if (episode.getDurationMs() > 0) metadata.putLong(METADATA_KEY_DURATION,
				episode.getDurationMs());
		return artwork.load().ifFail(error -> "").then(artworkUrl -> {
			if ((artworkUrl != null) && !artworkUrl.isEmpty()) metadata.setImageUri(artworkUrl);
			return buildMeta(metadata);
		});
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat metadata, SharedTextBuilder text) {
		if (!episode.getAuthor().isEmpty()) text.append(episode.getAuthor());
		else text.append(episode.getFeedTitle());
		if (episode.getDurationMs() > 0) {
			if (text.length() != 0) text.append(" - ");
			me.aap.utils.text.TextUtils.timeToString(text, (int) (episode.getDurationMs() / 1000));
		}
		return text.toString();
	}

	@FunctionalInterface
	interface ArtworkLoader {
		FutureSupplier<String> load();
	}

	@FunctionalInterface
	interface ProgressStore {
		FutureSupplier<Void> update(String feedKey, String episodeKey, long position,
				boolean played, long lastPlayedMs);
	}
}
