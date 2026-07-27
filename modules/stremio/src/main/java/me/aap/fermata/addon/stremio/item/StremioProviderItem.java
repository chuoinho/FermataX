package me.aap.fermata.addon.stremio.item;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioProviderItem extends StremioBrowsableItem {
	private final BrowseProvider provider;

	public StremioProviderItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, BrowseProvider provider) {
		super(StremioItemIds.provider(provider.sourceUuid()), parent, root, gateway,
				provider.displayName(), "", "", null);
		this.provider = provider;
	}

	public BrowseProvider provider() {
		return provider;
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		if (!provider.enabled()) return me.aap.utils.async.Completed.completedEmptyList();
		return gateway().catalogs(provider.sourceUuid()).map(catalogs -> {
			List<Item> items = new ArrayList<>(catalogs.size());
			for (var catalog : catalogs) {
				if (catalog.route().sourceUuid().equals(provider.sourceUuid())) {
					items.add(new StremioCatalogItem(this, getRoot(), gateway(), catalog));
				}
			}
			return List.copyOf(items);
		});
	}
}
