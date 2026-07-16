package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public class XtreamSeasonItem extends BrowsableItemBase implements TvItem {
	public static final String SCHEME = "tvxsn";
	private XtreamSeason season;

	private XtreamSeasonItem(String id, XtreamSeriesItem parent, XtreamSeason season) {
		super(id, parent, null);
		this.season = season;
	}

	public static XtreamSeasonItem create(XtreamSeriesItem parent, XtreamSeason season) {
		String id = toId(parent, season);
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			if (i != null) {
				XtreamSeasonItem item = (XtreamSeasonItem) i;
				item.season = season;
				return item;
			}
			return new XtreamSeasonItem(id, parent, season);
		}
	}

	public static FutureSupplier<XtreamSeasonItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		ParsedId parsed = parseId(id);
		String seriesId = XtreamSeriesItem.toId(parsed.sourceId, parsed.categoryId,
				parsed.categoryName, parsed.seriesId, parsed.seriesName);
		FutureSupplier<? extends Item> f = root.getItem(XtreamSeriesItem.SCHEME, seriesId);
		return (f == null) ? completedNull() : f.then(i -> {
			XtreamSeriesItem series = (XtreamSeriesItem) i;
			return (series != null) ? series.getSeason(parsed.seasonNumber) : completedNull();
		});
	}

	public static String toId(XtreamSeriesItem parent, XtreamSeason season) {
		XtreamSeriesCategoryItem category = parent.getParent();
		XtreamCategory c = category.getCategory();
		XtreamSeries series = parent.getSeries();
		return toId(category.getParent().getParent().getSourceId(), c.getId(), c.getName(),
				series.getSeriesId(), series.getName(), season.getSeasonNumber());
	}

	public static String toId(int sourceId, String categoryId, String categoryName,
													 int seriesId, String seriesName, int seasonNumber) {
		return XtreamItemId.season(SCHEME, sourceId, categoryId, categoryName, seriesId,
				seriesName, seasonNumber);
	}

	public int getSeasonNumber() {
		return season.getSeasonNumber();
	}

	@NonNull
	@Override
	public XtreamSeriesItem getParent() {
		return (XtreamSeriesItem) super.getParent();
	}

	public XtreamSeason getSeason() {
		return season;
	}

	public FutureSupplier<XtreamEpisodeItem> getEpisode(int episodeId) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if ((child instanceof XtreamEpisodeItem) &&
						(((XtreamEpisodeItem) child).getEpisodeId() == episodeId)) {
					return (XtreamEpisodeItem) child;
				}
			}
			return null;
		});
	}

	@NonNull
	@Override
	public String getName() {
		return season.getName();
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		List<XtreamEpisode> episodes = season.getEpisodes();
		List<Item> children = new ArrayList<>(episodes.size());
		for (XtreamEpisode e : episodes) children.add(XtreamEpisodeItem.create(this, e));
		return completed(children);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.sub_episodes, children.size());
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		String icon = season.getIcon();
		return (icon == null) || icon.isEmpty() ? completedNull() : completed(Uri.parse(icon));
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}

	static ParsedId parseId(String id) {
		XtreamItemId.Season parsed = XtreamItemId.season(id);
		return new ParsedId(parsed.sourceId, parsed.categoryId, parsed.categoryName,
				parsed.seriesId, parsed.seriesName, parsed.seasonNumber);
	}

	static final class ParsedId {
		final int sourceId;
		final String categoryId;
		final String categoryName;
		final int seriesId;
		final String seriesName;
		final int seasonNumber;

		ParsedId(int sourceId, String categoryId, String categoryName, int seriesId,
						 String seriesName, int seasonNumber) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.categoryName = categoryName;
			this.seriesId = seriesId;
			this.seriesName = seriesName;
			this.seasonNumber = seasonNumber;
		}
	}
}
