package me.aap.fermata.addon.stremio.item;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioPageItem extends StremioBrowsableItem {
	private final CatalogDescriptor catalog;
	@Nullable private final String genre;
	private final int skip;

	public StremioPageItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, CatalogDescriptor catalog,
			@Nullable String genre, int skip) {
		super(StremioItemIds.page(catalog, genre, skip), parent, root, gateway,
				pageTitle(catalog, skip), genre, catalog.providerName(), null);
		if (skip < 0) throw new IllegalArgumentException("skip cannot be negative");
		this.catalog = catalog;
		this.genre = genre;
		this.skip = skip;
	}

	public int skip() {
		return skip;
	}

	@Nullable
	public String genre() {
		return genre;
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return gateway().catalog(catalog.route(), genre, skip).map(page -> {
			if (!page.catalog().route().equals(catalog.route()) || (page.skip() != skip) ||
					!java.util.Objects.equals(page.genre(), genre)) {
				throw new IllegalStateException("Catalog page route mismatch");
			}
			List<Item> items = new ArrayList<>(page.items().size() + 1);
			for (var media : page.items()) {
				items.add(new StremioMetaItem(this, getRoot(), gateway(), media));
			}
			if (page.hasNext() && (page.nextSkip() > skip)) {
				items.add(new StremioPageItem(this, getRoot(), gateway(), catalog,
						genre, page.nextSkip()));
			}
			return List.copyOf(items);
		});
	}

	private static String pageTitle(CatalogDescriptor catalog, int skip) {
		return (skip == 0) ? catalog.name() : catalog.name() + " - " + (skip + 1);
	}
}
