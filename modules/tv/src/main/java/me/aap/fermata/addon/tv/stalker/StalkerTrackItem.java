package me.aap.fermata.addon.tv.stalker;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.utils.async.Completed.completedNull;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import java.net.URI;
import java.util.Map;

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
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

public final class StalkerTrackItem extends PlayableItemBase implements StreamItem,
		StreamItemPrefs, TvItem, StalkerCatalogItem, RemotePlaybackItem {
	public static final String SCHEME = "tvst";
	private final StalkerChannel channel;
	private final long catalogRevision;

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
		return profile(account.getPortalUri(), "stalker-capability:" + account.getSourceId());
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback() {
		StalkerSourceItem source = getParent().getParent();
		if (!isCatalogCurrent()) {
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Stalker portal catalog has changed"));
		}
		return source.getApi().createLink(channel.command()).map(link -> {
			PlaybackRequestProfile profile = profile(link.uri(),
					"stalker:" + source.getSourceId() + ':' + channel.id());
			Map<String, String> headers = link.headers();
			return new RemotePlaybackRequest(link.uri(), profile, ignored -> headers);
		});
	}

	private static PlaybackRequestProfile profile(URI target, String diagnostic) {
		return PlaybackRequestProfile.builder(target, diagnostic)
				.headerReference(PlaybackRequestProfile.HeaderReference.of("stalker-playback"))
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.SAME_ORIGIN)
				.build();
	}
}
