package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

abstract class XtreamCategoryItemBase extends BrowsableItemBase implements TvItem {
	private final XtreamCategory category;

	XtreamCategoryItemBase(String id, XtreamSectionItem parent, XtreamCategory category) {
		super(id, parent, null);
		this.category = category;
	}

	@SuppressWarnings("unchecked")
	static <T extends XtreamCategoryItemBase> T cached(XtreamSectionItem parent, String id,
																										 Supplier<T> factory) {
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			return (i != null) ? (T) i : factory.get();
		}
	}

	static ParsedId parseCategoryId(String id) {
		XtreamItemId.Category parsed = XtreamItemId.category(id);
		return new ParsedId(parsed.sourceId, parsed.categoryId, parsed.categoryName);
	}

	@NonNull
	@Override
	public XtreamSectionItem getParent() {
		return (XtreamSectionItem) super.getParent();
	}

	public XtreamCategory getCategory() {
		return category;
	}

	@NonNull
	@Override
	public String getName() {
		return category.getName();
	}

	protected XtreamCategory getXtreamCategory() {
		return category;
	}

	protected <T extends Item> FutureSupplier<T> findChild(Class<T> type, Predicate<T> predicate) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if (type.isInstance(child)) {
					T item = type.cast(child);
					if (predicate.test(item)) return item;
				}
			}
			return null;
		});
	}

	protected <T> FutureSupplier<List<Item>> listChildren(FutureSupplier<List<T>> source,
																												Function<T, Item> mapper) {
		return source.map(items -> {
			List<Item> children = new ArrayList<>(items.size());
			for (T item : items) children.add(mapper.apply(item));
			return children;
		});
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	static final class ParsedId {
		final int sourceId;
		final String categoryId;
		final String name;

		ParsedId(int sourceId, String categoryId, String name) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.name = name;
		}
	}
}
