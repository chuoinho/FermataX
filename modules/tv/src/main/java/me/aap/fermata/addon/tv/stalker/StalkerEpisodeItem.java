package me.aap.fermata.addon.tv.stalker;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.utils.async.Completed.completedNull;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

public final class StalkerEpisodeItem extends PlayableItemBase implements StreamItem,
		StreamItemPrefs, TvItem, StalkerCatalogItem, RemotePlaybackItem {
	public static final String SCHEME = "tvse";
	private StalkerEpisode episode;
	private final long catalogRevision;

	private StalkerEpisodeItem(String id, StalkerSeasonItem parent, StalkerEpisode episode) {
		super(id, parent, GenericFileSystem.getInstance().create(
				parent.getStalkerSource().getAccount().getPortal()));
		this.episode = episode;
		catalogRevision = parent.getCatalogRevision();
	}

	public static StalkerEpisodeItem create(StalkerSeasonItem parent, StalkerEpisode episode) {
		StalkerItemId.Content content = StalkerSeasonItem.content(parent.getParent());
		StalkerItemId.Season season = new StalkerItemId.Season(content.sourceId(), content.type(),
				content.categoryId(), content.categoryName(), content.contentId(),
				parent.getSeasonId());
		String id = StalkerItemId.episode(SCHEME, season, episode.id());
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerEpisodeItem item = (StalkerEpisodeItem) cached;
				if (!item.isCatalogCurrent()) {
					lib.removeFromCache(item);
					return new StalkerEpisodeItem(id, parent, episode);
				}
				item.episode = episode;
				return item;
			}
			return new StalkerEpisodeItem(id, parent, episode);
		}
	}

	public static FutureSupplier<StalkerEpisodeItem> create(TvRootItem root, String id) {
		StalkerItemId.Episode parsed = StalkerItemId.parseEpisode(id, SCHEME);
		StalkerItemId.Content content = new StalkerItemId.Content(parsed.sourceId(), parsed.type(),
				parsed.categoryId(), parsed.categoryName(), parsed.contentId());
		String seasonId = StalkerItemId.season(StalkerSeasonItem.SCHEME, content,
				parsed.seasonId());
		FutureSupplier<? extends Item> seasonFuture = root.getItem(StalkerSeasonItem.SCHEME,
				seasonId);
		return (seasonFuture == null) ? completedNull() : seasonFuture.then(item -> {
			StalkerSeasonItem season = (StalkerSeasonItem) item;
			return (season == null) ? completedNull() : season.getEpisode(parsed.episodeId());
		});
	}

	public String getEpisodeId() {
		return episode.id();
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	@NonNull
	@Override
	public StalkerSeasonItem getParent() {
		return (StalkerSeasonItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		return episode.name();
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@Override
	public boolean isLiveStream() {
		return false;
	}

	@Override
	public boolean isSeekable() {
		return true;
	}

	@Override
	public boolean isLocationSensitive() {
		return true;
	}

	@Override
	public boolean isRecentEligible() {
		return isCatalogCurrent();
	}

	@NonNull
	@Override
	public VirtualResource getResource() {
		return GenericFileSystem.getInstance().create(getStalkerSource().getAccount().getPortal());
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
		if (episode.description() != null) {
			metadata.putString(METADATA_KEY_DISPLAY_DESCRIPTION, episode.description());
		}
		if (episode.logo() != null) metadata.setImageUri(episode.logo());
		return super.buildMeta(metadata);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat metadata, SharedTextBuilder builder) {
		return getParent().getParent().getName();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}

	@Override
	public String getOrigId() {
		return getId();
	}

	@Override
	public String getUserAgent() {
		return getStalkerSource().getAccount().getUserAgent();
	}

	@Override
	public PlaybackRequestProfile getPlaybackRequestProfile() {
		StalkerSourceItem source = getStalkerSource();
		return StalkerPlayback.profile(source.getAccount().getPortalUri(),
				"stalker-episode-capability:" + source.getSourceId());
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback() {
		if (!isCatalogCurrent()) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Stalker portal catalog has changed"));
		}
		StalkerSourceItem source = getStalkerSource();
		return StalkerPlayback.prepare(source.getApi().createVodLink(episode.command(),
				episode.seriesNumber()), "stalker-episode:" + source.getSourceId() + ':' +
				episode.id());
	}
}
