package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completedEmptyList;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/** Visible terminal state for content without a supported stream choice. */
final class StremioNoStreamsItem extends StremioBrowsableItem {
	StremioNoStreamsItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, StreamAggregationRequest request) {
		super(parent.getId() + ":no-streams", parent, root, gateway,
				text(root, me.aap.fermata.addon.stremio.R.string.stremio_no_streams,
						"No playable streams"),
				text(root, me.aap.fermata.addon.stremio.R.string.stremio_no_streams_hint,
						"Install or enable a compatible Stremio provider"),
				"", request.metadata().artwork());
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completedEmptyList();
	}
}
