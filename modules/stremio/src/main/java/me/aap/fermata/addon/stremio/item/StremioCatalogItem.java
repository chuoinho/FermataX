package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioCatalogItem extends StremioBrowsableItem {
	private final CatalogDescriptor catalog;

	public StremioCatalogItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, CatalogDescriptor catalog) {
		super(StremioItemIds.catalog(catalog), parent, root, gateway, catalog.name(),
				catalogSubtitle(catalog), "", null);
		this.catalog = catalog;
	}

	public CatalogDescriptor catalog() {
		return catalog;
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		if (catalog.genres().isEmpty()) {
			return completed(List.of(new StremioPageItem(
					this, getRoot(), gateway(), catalog, null, 0)));
		}

		List<Item> items = new ArrayList<>(catalog.genres().size());
		for (String genre : catalog.genres()) {
			items.add(new StremioGenreItem(this, getRoot(), gateway(), catalog, genre));
		}
		return completed(List.copyOf(items));
	}

	private static String catalogSubtitle(CatalogDescriptor catalog) {
		String type = switch (catalog.route().type()) {
			case "movie" -> "Movies";
			case "series" -> "Series";
			default -> catalog.route().type();
		};
		return type + " - " + catalog.providerName();
	}
}
