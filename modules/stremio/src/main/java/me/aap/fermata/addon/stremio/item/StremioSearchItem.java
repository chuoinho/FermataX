package me.aap.fermata.addon.stremio.item;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioSearchItem extends StremioBrowsableItem {
	private final String query;

	public StremioSearchItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, String query) {
		super(StremioItemIds.search(query), parent, root, gateway, query, "", "", null);
		this.query = java.util.Objects.requireNonNull(query, "query");
		if (query.isBlank()) throw new IllegalArgumentException("query cannot be blank");
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return gateway().search(query).map(results -> {
			List<Item> items = new ArrayList<>(results.items().size());
			for (var media : results.items()) {
				items.add(new StremioMetaItem(this, getRoot(), gateway(), media));
			}
			return List.copyOf(items);
		});
	}
}
