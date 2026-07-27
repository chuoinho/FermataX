package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioGenreItem extends StremioBrowsableItem {
	private final CatalogDescriptor catalog;
	private final String genre;

	public StremioGenreItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, CatalogDescriptor catalog, String genre) {
		super(StremioItemIds.genre(catalog, genre), parent, root, gateway, genre,
				catalog.name(), "", null);
		this.catalog = catalog;
		this.genre = java.util.Objects.requireNonNull(genre, "genre");
		if (genre.isBlank()) throw new IllegalArgumentException("genre cannot be blank");
	}

	public String genre() {
		return genre;
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completed(List.of(new StremioPageItem(
				this, getRoot(), gateway(), catalog, genre, 0)));
	}
}
