package me.aap.fermata.addon.stremio.item;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioMetaItem extends StremioBrowsableItem {
	private final BrowseMedia media;

	public StremioMetaItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, BrowseMedia media) {
		super(StremioItemIds.meta(media), parent, root, gateway, media.title(),
				media.releaseInfo(), media.description(), preferredArtwork(media));
		this.media = media;
	}

	public BrowseMedia media() {
		return media;
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return gateway().meta(media).map(details -> {
			verifyIdentity(details);
			if (!details.series()) {
				return List.of(new StremioStreamPickerItem(this, getRoot(), gateway(),
						details.media(), null));
			}

			var seasons = new ArrayList<>(details.seasons());
			seasons.sort(Comparator.comparingInt(season -> season.number()));
			List<Item> items = new ArrayList<>(seasons.size());
			for (var season : seasons) {
				items.add(new StremioSeasonItem(this, getRoot(), gateway(),
						details.media(), season));
			}
			return List.copyOf(items);
		});
	}

	private void verifyIdentity(BrowseDetails details) {
		BrowseMedia resolved = details.media();
		if (!media.sourceUuid().equals(resolved.sourceUuid()) ||
				!media.type().equals(resolved.type()) || !media.id().equals(resolved.id())) {
			throw new IllegalStateException("Stremio metadata identity mismatch");
		}
	}

	static String preferredArtwork(BrowseMedia media) {
		if ((media.poster() != null) && !media.poster().isBlank()) return media.poster();
		return media.background();
	}
}
