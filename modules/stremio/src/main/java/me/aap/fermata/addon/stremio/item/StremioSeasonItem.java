package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public final class StremioSeasonItem extends StremioBrowsableItem {
	private final BrowseMedia media;
	private final BrowseSeason season;

	public StremioSeasonItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, BrowseMedia media, BrowseSeason season) {
		super(StremioItemIds.season(media, season.number()), parent, root, gateway,
				"S" + season.number(), media.title(), "", StremioMetaItem.preferredArtwork(media));
		this.media = media;
		this.season = season;
	}

	public int seasonNumber() {
		return season.number();
	}

	public BrowseMedia media() {
		return media;
	}

	public BrowseSeason season() {
		return season;
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		var episodes = new ArrayList<>(season.episodes());
		episodes.sort(Comparator.comparingInt(BrowseEpisode::episode)
				.thenComparing(BrowseEpisode::videoId));
		List<Item> items = new ArrayList<>(episodes.size());
		for (var episode : episodes) {
			items.add(new StremioEpisodeItem(this, getRoot(), gateway(), media, episode));
		}
		return completed(List.copyOf(items));
	}
}
