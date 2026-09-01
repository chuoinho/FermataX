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

public final class StalkerVodItem extends PlayableItemBase implements StreamItem,
		StreamItemPrefs, TvItem, StalkerCatalogItem, RemotePlaybackItem {
	public static final String SCHEME = "tvsv";
	private final StalkerVod vod;
	private final long catalogRevision;

	private StalkerVodItem(String id, StalkerContentCategoryItem parent, StalkerVod vod) {
		super(id, parent, GenericFileSystem.getInstance().create(
				parent.getStalkerSource().getAccount().getPortal()));
		this.vod = vod;
		catalogRevision = parent.getCatalogRevision();
	}

	public static StalkerVodItem create(StalkerContentCategoryItem parent, StalkerVod vod) {
		StalkerCategory category = parent.getCategory();
		String id = StalkerItemId.content(SCHEME, parent.getStalkerSource().getSourceId(),
				parent.getParent().getType(), category.id(), category.name(), vod.id());
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		synchronized (lib.cacheLock()) {
			MediaLib.Item cached = lib.getFromCache(id);
			if (cached != null) {
				StalkerVodItem item = (StalkerVodItem) cached;
				if (item.isCatalogCurrent()) return item;
				lib.removeFromCache(item);
			}
			return new StalkerVodItem(id, parent, vod);
		}
	}

	public static FutureSupplier<StalkerVodItem> create(TvRootItem root, String id) {
		StalkerItemId.Content parsed = StalkerItemId.parseContent(id, SCHEME);
		String categoryId = StalkerContentCategoryItem.toId(parsed.sourceId(), parsed.type(),
				parsed.categoryId(), parsed.categoryName());
		FutureSupplier<? extends Item> categoryFuture = root.getItem(
				StalkerContentCategoryItem.SCHEME, categoryId);
		return (categoryFuture == null) ? completedNull() : categoryFuture.then(item -> {
			StalkerContentCategoryItem category = (StalkerContentCategoryItem) item;
			return (category == null) ? completedNull() : category.getVod(parsed.contentId());
		});
	}

	public String getVodId() {
		return vod.id();
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	@NonNull
	@Override
	public StalkerContentCategoryItem getParent() {
		return (StalkerContentCategoryItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		return vod.name();
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
		if (vod.description() != null) {
			metadata.putString(METADATA_KEY_DISPLAY_DESCRIPTION, vod.description());
		}
		if (vod.logo() != null) metadata.setImageUri(vod.logo());
		return super.buildMeta(metadata);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat metadata, SharedTextBuilder builder) {
		return getParent().getName();
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
				"stalker-vod-capability:" + source.getSourceId());
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback() {
		if (!isCatalogCurrent()) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Stalker portal catalog has changed"));
		}
		StalkerSourceItem source = getStalkerSource();
		return StalkerPlayback.prepare(source.getApi().createVodLink(vod.command(), "0"),
				"stalker-vod:" + source.getSourceId() + ':' + vod.id());
	}
}
