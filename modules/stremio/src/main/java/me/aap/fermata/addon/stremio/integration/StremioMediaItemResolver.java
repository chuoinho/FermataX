package me.aap.fermata.addon.stremio.integration;

import static me.aap.fermata.addon.stremio.integration.StremioFutureBridge.toCompletable;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import me.aap.fermata.addon.stremio.StremioRootItem;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.item.StremioDirectPlayableItem;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioMetaItem;
import me.aap.fermata.addon.stremio.item.StremioNavigationTarget;
import me.aap.fermata.addon.stremio.item.StremioSeasonItem;
import me.aap.fermata.addon.stremio.item.StremioStreamPickerItem;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.session.StremioProviderState;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

/** Reconstructs MediaLib items from durable Stremio projection identities. */
final class StremioMediaItemResolver {
	private final StremioRepository repository;
	private final StremioProjectionStore projections;
	private final StremioItemGateway gateway;
	private final Function<String, CompletionStage<StremioProviderState>> providerState;
	private final BooleanSupplier closed;

	StremioMediaItemResolver(StremioRepository repository,
			StremioProjectionStore projections, StremioItemGateway gateway,
			Function<String, CompletionStage<StremioProviderState>> providerState,
			BooleanSupplier closed) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.projections = Objects.requireNonNull(projections, "projections");
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.providerState = Objects.requireNonNull(providerState, "providerState");
		this.closed = Objects.requireNonNull(closed, "closed");
	}

	FutureSupplier<Item> resolve(DefaultMediaLib lib, StremioRootItem root,
			String stableId, @Nullable PlaybackDescriptor preferred) {
		CompletableFuture<Item> result = projections.load(stableId).thenCompose(projection -> {
			if (projection == null) return CompletableFuture.completedFuture(null);
			return providerState.apply(projection.item().sourceUuid()).thenCompose(state -> {
				if (state != StremioProviderState.ENABLED) {
					return CompletableFuture.completedFuture(null);
				}
				if ((projection.videoId() == null) ||
						(StremioNavigationTarget.forContent(projection.media(), projection.episode()) ==
								StremioNavigationTarget.DETAILS)) {
					return CompletableFuture.completedFuture(new StremioMetaItem(
							root, root, gateway, projection.media()));
				}
				return projections.refreshEpisode(projection).thenCompose(resolved -> {
					BrowsableItem backDestination = restoreBackDestination(root, resolved);
					StremioStreamPickerItem picker = new StremioStreamPickerItem(
							root, root, gateway, resolved.media(), resolved.episode());
					return toCompletable(picker.getUnsortedChildren()).thenCompose(children -> {
						PlayableItem selected = null;
						PlayableItem fallback = null;
						for (Item child : children) {
							if (!(child instanceof PlayableItem playable)) continue;
							if (fallback == null) fallback = playable;
							if ((preferred != null) &&
									(playable instanceof StremioDirectPlayableItem direct) &&
									direct.descriptor().providerSourceUuid().equals(
											preferred.providerSourceUuid()) &&
									direct.descriptor().selectionFingerprint().equals(
											preferred.selectionFingerprint())) {
								selected = playable;
								break;
							}
						}
						if (selected == null) selected = fallback;
						if (selected == null) return CompletableFuture.<Item>completedFuture(null);
						PlayableItem selectedItem = selected;
						return open(repository.getProgress(stableId)).thenApply(progress -> {
							long resume = (progress == null || progress.completed()) ? 0L :
									Math.max(progress.positionMs(), 0L);
							if (selectedItem instanceof StremioDirectPlayableItem direct) {
								return (Item) new StremioDirectPlayableItem(backDestination,
										gateway, direct.descriptor(), picker.request(), resume);
							}
							return (Item) selectedItem;
						});
					});
				});
			});
		}).toCompletableFuture();
		return StremioFutureBridge.from(result);
	}

	private BrowsableItem restoreBackDestination(
			StremioRootItem root, StremioPersistedItem projection) {
		StremioMetaItem meta = new StremioMetaItem(root, root, gateway, projection.media());
		BrowsableItem destination = meta;
		if (projection.episode() != null) {
			BrowseSeason season = new BrowseSeason(projection.item().seasonNumber(),
					projection.siblings().isEmpty() ? List.of(projection.episode()) :
							projection.siblings());
			destination = new StremioSeasonItem(meta, root, gateway, projection.media(), season);
		}
		if (!projection.item().backToListId().equals(destination.getId())) {
			throw new IllegalStateException("Restored Stremio back destination mismatch");
		}
		return destination;
	}

	private <T> CompletableFuture<T> open(CompletableFuture<T> future) {
		return closed.getAsBoolean() ? CompletableFuture.failedFuture(
				new IllegalStateException("Stremio runtime is closed")) : future;
	}
}
