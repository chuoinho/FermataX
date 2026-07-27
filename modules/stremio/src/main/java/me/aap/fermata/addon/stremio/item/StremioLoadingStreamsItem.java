package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completedEmptyList;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/** Non-terminal empty state while at least one stream provider is still running. */
final class StremioLoadingStreamsItem extends StremioBrowsableItem {
	StremioLoadingStreamsItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, StreamAggregationRequest request) {
		super(parent.getId() + ":loading-streams", parent, root, gateway,
				text(root, me.aap.fermata.addon.stremio.R.string.stremio_loading_streams,
						"Loading streams..."),
				text(root, me.aap.fermata.addon.stremio.R.string.stremio_loading_streams_hint,
						"Waiting for providers"), "",
				request.metadata().artwork());
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completedEmptyList();
	}
}
