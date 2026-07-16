package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completedNull;

import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public class XtreamVodCategoryItem extends XtreamCategoryItemBase {
	public static final String SCHEME = "tvxvc";

	private XtreamVodCategoryItem(String id, XtreamSectionItem parent, XtreamCategory category) {
		super(id, parent, category);
	}

	public static XtreamVodCategoryItem create(XtreamSectionItem parent, XtreamCategory category) {
		String id = toId(parent.getParent().getSourceId(), category.getId(), category.getName());
		return cached(parent, id, () -> new XtreamVodCategoryItem(id, parent, category));
	}

	public static FutureSupplier<XtreamVodCategoryItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		ParsedId parsed = parseId(id);
		FutureSupplier<? extends Item> f = root.getItem(XtreamSourceItem.SCHEME,
				XtreamSourceItem.toId(parsed.sourceId));
		return (f == null) ? completedNull() : f.then(i -> {
			XtreamSourceItem source = (XtreamSourceItem) i;
			if (source == null) return completedNull();
			return source.getSection(XtreamSectionItem.TYPE_VOD).map(section ->
					create(section, new XtreamCategory(parsed.categoryId, parsed.name, null)));
		});
	}

	public static String toId(int sourceId, String categoryId, String name) {
		return XtreamItemId.category(SCHEME, sourceId, categoryId, name);
	}

	public FutureSupplier<XtreamMovieItem> getMovie(int streamId) {
		return findChild(XtreamMovieItem.class, item -> item.getStreamId() == streamId);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return listChildren(getParent().getParent().getApi().getVodStreams(getXtreamCategory().getId()),
				movie -> XtreamMovieItem.create(this, movie));
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.sub_movies, children.size());
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}

	static ParsedId parseId(String id) {
		return parseCategoryId(id);
	}
}
