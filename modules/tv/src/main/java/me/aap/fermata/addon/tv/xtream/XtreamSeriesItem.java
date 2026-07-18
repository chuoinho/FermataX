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
public class XtreamSeriesItem extends BrowsableItemBase implements TvItem, XtreamCatalogItem {
	public static final String SCHEME = "tvxsr";
	private XtreamSeries series;
	private final long catalogRevision;

	private XtreamSeriesItem(String id, XtreamSeriesCategoryItem parent, XtreamSeries series) {
		super(id, parent, null);
		this.series = series;
		catalogRevision = XtreamCatalogItem.revision(parent);
	}

	public static XtreamSeriesItem create(XtreamSeriesCategoryItem parent, XtreamSeries series) {
		String id = toId(parent, series);
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			if (i != null) {
				XtreamSeriesItem item = (XtreamSeriesItem) i;
				if (!item.isCatalogCurrent()) {
					lib.removeFromCache(item);
					return new XtreamSeriesItem(id, parent, series);
				}
				item.series = series;
				return item;
			}
			return new XtreamSeriesItem(id, parent, series);
		}
	}

	@Override
	public long getCatalogRevision() {
		return catalogRevision;
	}

	public static FutureSupplier<XtreamSeriesItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		ParsedId parsed = parseId(id);
		String categoryId = XtreamSeriesCategoryItem.toId(parsed.sourceId, parsed.categoryId,
				parsed.categoryName);
		FutureSupplier<? extends Item> f = root.getItem(XtreamSeriesCategoryItem.SCHEME, categoryId);
		return (f == null) ? completedNull() : f.then(i -> {
			XtreamSeriesCategoryItem category = (XtreamSeriesCategoryItem) i;
			return (category != null) ? category.getSeries(parsed.seriesId) : completedNull();
		});
	}

	public static String toId(XtreamSeriesCategoryItem parent, XtreamSeries series) {
		XtreamCategory c = parent.getCategory();
		return toId(parent.getParent().getParent().getSourceId(), c.getId(), c.getName(),
				series.getSeriesId(), series.getName());
	}

	public static String toId(int sourceId, String categoryId, String categoryName, int seriesId,
													 String seriesName) {
		return XtreamItemId.series(SCHEME, sourceId, categoryId, categoryName, seriesId,
				seriesName);
	}

	public int getSeriesId() {
		return series.getSeriesId();
	}

	@NonNull
	@Override
	public XtreamSeriesCategoryItem getParent() {
		return (XtreamSeriesCategoryItem) super.getParent();
	}

	public XtreamSeries getSeries() {
		return series;
	}

	public FutureSupplier<XtreamSeasonItem> getSeason(int seasonNumber) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if ((child instanceof XtreamSeasonItem) &&
						(((XtreamSeasonItem) child).getSeasonNumber() == seasonNumber)) {
					return (XtreamSeasonItem) child;
				}
			}
			return null;
		});
	}

	@NonNull
	@Override
	public String getName() {
		return series.getName();
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return getParent().getParent().getParent().getApi()
				.getSeriesSeasons(series.getSeriesId()).map(seasons -> {
					List<Item> children = new ArrayList<>(seasons.size());
					for (XtreamSeason s : seasons) children.add(XtreamSeasonItem.create(this, s));
					return children;
				});
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.sub_seasons, children.size());
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		String icon = series.getIcon();
		return (icon == null) || icon.isEmpty() ? completedNull() : completed(Uri.parse(icon));
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}

	static ParsedId parseId(String id) {
		XtreamItemId.Series parsed = XtreamItemId.series(id);
		return new ParsedId(parsed.sourceId, parsed.categoryId, parsed.categoryName,
				parsed.seriesId, parsed.seriesName);
	}

	static final class ParsedId {
		final int sourceId;
		final String categoryId;
		final String categoryName;
		final int seriesId;
		final String seriesName;

		ParsedId(int sourceId, String categoryId, String categoryName, int seriesId,
						 String seriesName) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.categoryName = categoryName;
			this.seriesId = seriesId;
			this.seriesName = seriesName;
		}
	}
}
