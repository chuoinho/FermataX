package me.aap.fermata.addon.stremio.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamProvider;
import me.aap.fermata.addon.stremio.protocol.CapabilityMatcher;
import me.aap.fermata.addon.stremio.protocol.ManifestValidator;
import me.aap.fermata.addon.stremio.runtime.StremioRuntime;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;

/** Converts committed source snapshots into enabled domain provider views off the caller thread. */
public final class StremioProviderCatalog implements AutoCloseable {
	private final StremioRuntimeAccess runtime;
	private final Executor executor;
	private final AtomicReference<StremioSourceSnapshot> current = new AtomicReference<>();
	private final AutoCloseable sourceObserver;

	public StremioProviderCatalog(StremioRuntime runtime, Executor executor) {
		this(StremioRuntimeAccess.from(runtime), executor);
	}

	StremioProviderCatalog(StremioRuntimeAccess runtime, Executor executor) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.executor = Objects.requireNonNull(executor, "executor");
		sourceObserver = runtime.observeSources(current::set);
	}

	public CompletionStage<List<BrowseProvider>> browseProviders() {
		return snapshot().thenApplyAsync(state -> {
			var providers = new ArrayList<BrowseProvider>();
			for (var source : state.sources()) {
				if (!source.enabled()) continue;
				try {
					var manifest = ManifestValidator.parse(source.manifestJson());
					if (!manifest.id().equals(source.addonId()) || isYoutubeAddon(manifest)) continue;
					providers.add(new BrowseProvider(source.sourceUuid(), source.name(), manifest,
							true, source.position()));
				} catch (RuntimeException ignored) {
					// A broken addon is isolated from healthy providers in the same snapshot.
				}
			}
			return List.copyOf(providers);
		}, executor);
	}

	public CompletionStage<List<StreamProvider>> streamProviders(
			StreamAggregationRequest request) {
		Objects.requireNonNull(request, "request");
		return snapshot().thenApplyAsync(state -> {
			var providers = new ArrayList<StreamProvider>();
			for (var source : state.sources()) {
				if (!source.enabled()) continue;
				try {
					var manifest = ManifestValidator.parse(source.manifestJson());
					if (!manifest.id().equals(source.addonId()) || isYoutubeAddon(manifest)) continue;
					String requestId = request.identities()
							.select(manifest, source.sourceUuid(), "stream").orElse(null);
					if (requestId == null) continue;
					providers.add(new StreamProvider(source.sourceUuid(), source.addonId(),
							source.name(), source.position(), true, source.networkConsent(),
							state.revision(), source.updatedMs(), source.transportFingerprint(),
							StremioSourceLease.bound(state.revision(), source, current::get),
							requestId));
				} catch (RuntimeException ignored) {
					// A broken addon is isolated from healthy providers in the same snapshot.
				}
			}
			return List.copyOf(providers);
		}, executor);
	}

	private static boolean isYoutubeAddon(
			me.aap.fermata.addon.stremio.protocol.model.StremioManifest manifest) {
		String identity = (manifest.id() + ' ' + manifest.name()).toLowerCase(
				java.util.Locale.ROOT);
		return identity.contains("youtube") || manifest.types().stream()
				.allMatch(type -> type.equalsIgnoreCase("youtube"));
	}

	public CompletionStage<Boolean> isCurrent(StreamProvider expected) {
		Objects.requireNonNull(expected, "expected");
		if (!expected.hasSourceBinding()) return CompletableFuture.completedFuture(false);
		return snapshot().thenApplyAsync(state -> {
			var source = state.source(expected.sourceUuid());
			return (source != null) && source.enabled() &&
					source.addonId().equals(expected.addonId()) &&
					(source.updatedMs() == expected.sourceUpdatedMs()) &&
					source.transportFingerprint().equals(expected.transportFingerprint()) &&
					source.networkConsent().equals(expected.networkConsent());
		}, executor);
	}

	private CompletionStage<StremioSourceSnapshot> snapshot() {
		return CompletableFuture.supplyAsync(runtime::sources, executor).thenCompose(stage -> stage)
				.thenApply(snapshot -> {
					current.set(snapshot);
					return snapshot;
				});
	}

	@Override
	public void close() {
		try {
			sourceObserver.close();
		} catch (Exception ignored) {
		}
	}
}
