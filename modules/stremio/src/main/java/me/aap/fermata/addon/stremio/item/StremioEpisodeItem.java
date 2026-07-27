package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioEpisodeItem extends StremioBrowsableItem {
	private final BrowseMedia media;
	private final BrowseEpisode episode;

	public StremioEpisodeItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, BrowseMedia media, BrowseEpisode episode) {
		super(StremioItemIds.episode(episode), parent, root, gateway, episode.title(),
				"S" + episode.season() + " E" + episode.episode(), episode.overview(),
				preferredArtwork(media, episode));
		this.media = media;
		this.episode = episode;
		if (!media.sourceUuid().equals(episode.sourceUuid()) ||
				!media.type().equals(episode.seriesType()) ||
				!media.id().equals(episode.seriesId())) {
			throw new IllegalArgumentException("Episode does not belong to media");
		}
	}

	public BrowseEpisode episode() {
		return episode;
	}

	public BrowseMedia media() {
		return media;
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completed(List.of(new StremioStreamPickerItem(
				this, getRoot(), gateway(), media, episode)));
	}

	private static String preferredArtwork(BrowseMedia media, BrowseEpisode episode) {
		if ((episode.thumbnail() != null) && !episode.thumbnail().isBlank()) {
			return episode.thumbnail();
		}
		return StremioMetaItem.preferredArtwork(media);
	}
}
