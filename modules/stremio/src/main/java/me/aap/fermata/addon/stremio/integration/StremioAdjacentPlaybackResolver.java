package me.aap.fermata.addon.stremio.integration;

import static me.aap.fermata.addon.stremio.integration.StremioFutureBridge.toCompletable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.StremioRootItem;
import me.aap.fermata.addon.stremio.item.StremioDirectPlayableItem;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.session.StremioAdjacentDirection;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

/** Resolves next/previous episode playback while preserving provider preference. */
final class StremioAdjacentPlaybackResolver {
	private final StremioSessionCoordinator sessions;
	private final StremioMediaItemResolver mediaResolver;

	StremioAdjacentPlaybackResolver(StremioSessionCoordinator sessions,
			StremioMediaItemResolver mediaResolver) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.mediaResolver = Objects.requireNonNull(mediaResolver, "mediaResolver");
	}

	FutureSupplier<PlayableItem> resolve(StremioDirectPlayableItem currentItem, boolean next) {
		if (!(currentItem.getLib() instanceof DefaultMediaLib lib) ||
				!(currentItem.getRoot() instanceof StremioRootItem root)) {
			return me.aap.utils.async.Completed.completedNull();
		}
		PlaybackDescriptor current = currentItem.descriptor();
		CompletableFuture<PlayableItem> result = sessions.adjacentEpisode(
				current.identity().videoKey(), next ? StremioAdjacentDirection.NEXT :
						StremioAdjacentDirection.PREVIOUS).thenCompose(resolution -> {
			if (!resolution.isAvailable()) return CompletableFuture.completedFuture(null);
			return toCompletable(mediaResolver.resolve(lib, root,
					resolution.item().stableId(), current)).thenApply(item ->
					(item instanceof PlayableItem playable) ? playable : null);
		}).toCompletableFuture();
		return StremioFutureBridge.from(result);
	}
}
