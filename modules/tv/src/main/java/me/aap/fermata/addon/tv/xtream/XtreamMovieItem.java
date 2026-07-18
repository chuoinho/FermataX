package me.aap.fermata.addon.tv.xtream;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.utils.async.Completed.completedNull;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class XtreamMovieItem extends PlayableItemBase implements StreamItem, StreamItemPrefs, TvItem,
		XtreamCatalogItem {
	public static final String SCHEME = "tvxm";
	private final XtreamMovie movie;
	private final long catalogRevision;

	private XtreamMovieItem(String id, XtreamVodCategoryItem parent, XtreamMovie movie) {
		super(id, parent,
				GenericFileSystem.getInstance().create(parent.getParent().getParent().getAccount()
						.buildMovieStreamUrl(movie.getStreamId(), movie.getContainerExtension())));
		this.movie = movie;
		catalogRevision = XtreamCatalogItem.revision(parent);
	}

	public static XtreamMovieItem create(XtreamVodCategoryItem parent, XtreamMovie movie) {
		String id = toId(parent, movie);
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			if (i != null) {
				XtreamMovieItem item = (XtreamMovieItem) i;
				if (item.isCatalogCurrent()) return item;
				lib.removeFromCache(item);
			}
			return new XtreamMovieItem(id, parent, movie);
		}
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	public static FutureSupplier<XtreamMovieItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		ParsedId parsed = parseId(id);
		String categoryId = XtreamVodCategoryItem.toId(parsed.sourceId, parsed.categoryId,
				parsed.categoryName);
		FutureSupplier<? extends Item> f = root.getItem(XtreamVodCategoryItem.SCHEME, categoryId);
		return (f == null) ? completedNull() : f.then(i -> {
			XtreamVodCategoryItem category = (XtreamVodCategoryItem) i;
			return (category != null) ? category.getMovie(parsed.streamId) : completedNull();
		});
	}

	public static String toId(XtreamVodCategoryItem parent, XtreamMovie movie) {
		XtreamCategory c = parent.getCategory();
		return XtreamItemId.stream(SCHEME, parent.getParent().getParent().getSourceId(),
				c.getId(), c.getName(), movie.getStreamId());
	}

	public int getStreamId() {
		return movie.getStreamId();
	}

	@Override
	public boolean isLocationSensitive() {
		return true;
	}

	@NonNull
	@Override
	public VirtualResource getResource() {
		return GenericFileSystem.getInstance().create(requireCurrentAccount()
				.buildMovieStreamUrl(getStreamId(), movie.getContainerExtension()));
	}

	@Override
	public boolean isRecentEligible() {
		return isCatalogCurrent();
	}

	@NonNull
	@Override
	public XtreamVodCategoryItem getParent() {
		return (XtreamVodCategoryItem) super.getParent();
	}

	@NonNull
	@Override
	public StreamItemPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public String getName() {
		return movie.getName();
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return buildMeta(new MetadataBuilder());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		meta.putString(METADATA_KEY_TITLE, getName());
		if (movie.getIcon() != null) meta.setImageUri(movie.getIcon());
		return super.buildMeta(meta);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		return getParent().getName();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}

	@Override
	public String getOrigId() {
		return XtreamItemId.orig(getId(), SCHEME);
	}

	@Nullable
	@Override
	public String getUserAgent() {
		return requireCurrentAccount().getUserAgent();
	}

	static ParsedId parseId(String id) {
		XtreamItemId.Stream parsed = XtreamItemId.stream(id);
		return new ParsedId(parsed.sourceId, parsed.categoryId, parsed.categoryName,
				parsed.streamId);
	}

	static final class ParsedId {
		final int sourceId;
		final String categoryId;
		final String categoryName;
		final int streamId;

		ParsedId(int sourceId, String categoryId, String categoryName, int streamId) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.categoryName = categoryName;
			this.streamId = streamId;
		}
	}
}
