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
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class XtreamEpisodeItem extends PlayableItemBase implements StreamItem, StreamItemPrefs, TvItem {
	public static final String SCHEME = "tvxe";
	private XtreamEpisode episode;

	private XtreamEpisodeItem(String id, XtreamSeasonItem parent, XtreamEpisode episode) {
		super(id, parent,
				GenericFileSystem.getInstance().create(parent.getParent().getParent().getParent()
						.getParent().getAccount().buildSeriesStreamUrl(episode.getEpisodeId(),
								episode.getContainerExtension())));
		this.episode = episode;
	}

	public static XtreamEpisodeItem create(XtreamSeasonItem parent, XtreamEpisode episode) {
		String id = toId(parent, episode);
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			if (i != null) {
				XtreamEpisodeItem item = (XtreamEpisodeItem) i;
				item.episode = episode;
				return item;
			}
			return new XtreamEpisodeItem(id, parent, episode);
		}
	}

	public static FutureSupplier<XtreamEpisodeItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		ParsedId parsed = parseId(id);
		String seasonId = XtreamSeasonItem.toId(parsed.sourceId, parsed.categoryId,
				parsed.categoryName, parsed.seriesId, parsed.seriesName, parsed.seasonNumber);
		FutureSupplier<? extends Item> f = root.getItem(XtreamSeasonItem.SCHEME, seasonId);
		return (f == null) ? completedNull() : f.then(i -> {
			XtreamSeasonItem season = (XtreamSeasonItem) i;
			return (season != null) ? season.getEpisode(parsed.episodeId) : completedNull();
		});
	}

	public static String toId(XtreamSeasonItem parent, XtreamEpisode episode) {
		XtreamSeriesItem seriesItem = parent.getParent();
		XtreamSeriesCategoryItem category = seriesItem.getParent();
		XtreamCategory c = category.getCategory();
		XtreamSeries series = seriesItem.getSeries();
		return XtreamItemId.episode(SCHEME, category.getParent().getParent().getSourceId(),
				c.getId(), c.getName(), series.getSeriesId(), series.getName(),
				parent.getSeasonNumber(), episode.getEpisodeId());
	}

	public int getEpisodeId() {
		return episode.getEpisodeId();
	}

	@NonNull
	@Override
	public XtreamSeasonItem getParent() {
		return (XtreamSeasonItem) super.getParent();
	}

	@NonNull
	@Override
	public StreamItemPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public String getName() {
		return episode.getName();
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
		if (episode.getIcon() != null) meta.setImageUri(episode.getIcon());
		return super.buildMeta(meta);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		return getParent().getParent().getName();
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
		return getParent().getParent().getParent().getParent().getParent().getAccount()
				.getUserAgent();
	}

	static ParsedId parseId(String id) {
		XtreamItemId.Episode parsed = XtreamItemId.episode(id);
		return new ParsedId(parsed.sourceId, parsed.categoryId, parsed.categoryName,
				parsed.seriesId, parsed.seriesName, parsed.seasonNumber, parsed.episodeId);
	}

	static final class ParsedId {
		final int sourceId;
		final String categoryId;
		final String categoryName;
		final int seriesId;
		final String seriesName;
		final int seasonNumber;
		final int episodeId;

		ParsedId(int sourceId, String categoryId, String categoryName, int seriesId,
						 String seriesName, int seasonNumber, int episodeId) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.categoryName = categoryName;
			this.seriesId = seriesId;
			this.seriesName = seriesName;
			this.seasonNumber = seasonNumber;
			this.episodeId = episodeId;
		}
	}
}
