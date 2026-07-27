package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completedEmptyList;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/** Visible non-playable choice used when the required target addon is disabled or unavailable. */
public final class StremioUnavailableStreamItem extends StremioBrowsableItem {
	StremioUnavailableStreamItem(BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, PlaybackDescriptor descriptor) {
		super(StremioItemIds.stream(descriptor) + ":unavailable", parent, root, gateway,
				descriptor.metadata().title(), subtitle(descriptor), "",
				descriptor.metadata().artwork());
	}

	@NonNull
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completedEmptyList();
	}

	private static String subtitle(PlaybackDescriptor descriptor) {
		if (descriptor.unsupportedReason() != null) {
			return switch (descriptor.unsupportedReason()) {
				case INFO_HASH_HANDLER_UNAVAILABLE -> "Torrent playback is not installed";
				case EXTERNAL_URL_HANDLER_UNAVAILABLE ->
						"External Web playback is unavailable for security";
				case NETWORK_POLICY_REJECTED -> "Stream blocked by source network policy";
				default -> "Unsupported stream";
			};
		}
		return "Playback addon unavailable";
	}
}
