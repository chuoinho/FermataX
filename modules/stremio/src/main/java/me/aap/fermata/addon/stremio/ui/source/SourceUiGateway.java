package me.aap.fermata.addon.stremio.ui.source;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.ui.config.StremioConfigLaunch;
import me.aap.fermata.addon.stremio.ui.config.StremioConfigResult;

/** The only boundary between source-management UI and the Stremio runtime. */
public interface SourceUiGateway {
	CompletableFuture<SourceUiSnapshot> load();

	CompletableFuture<SourceUiDraft> loadDraft(String sourceUuid);

	CompletableFuture<StremioConfigLaunch> loadConfiguration(String sourceUuid);

	AutoCloseable observe(Consumer<SourceUiSnapshot> observer);

	/** Loads safe addon_catalog projections; raw transport URLs remain runtime-owned. */
	default CompletableFuture<List<SourceUiDiscoveryItem>> discover() {
		return CompletableFuture.completedFuture(List.of());
	}

	/** Installs a discovery result from the latest bounded snapshot. */
	default SourceUiOperation installDiscovered(String stableId) {
		return SourceUiOperation.of(CompletableFuture.completedFuture(
				SourceUiResult.failed(SourceUiError.NOT_FOUND)), () -> {
		});
	}

	SourceUiOperation add(SourceUiDraft draft);

	SourceUiOperation edit(String sourceUuid, SourceUiDraft draft);

	SourceUiOperation configure(String sourceUuid, StremioConfigResult result);

	SourceUiOperation setEnabled(String sourceUuid, boolean enabled);

	SourceUiOperation refresh(String sourceUuid);

	SourceUiOperation remove(String sourceUuid);

	SourceUiOperation reorder(List<String> orderedSourceUuids);
}
