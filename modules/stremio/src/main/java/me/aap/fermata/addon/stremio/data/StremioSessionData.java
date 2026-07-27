package me.aap.fermata.addon.stremio.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fixed-query DB snapshot for a batch of stable movie or episode IDs. */
public record StremioSessionData(
		Map<String, StremioVideoRecord> videos,
		Map<String, StremioMetaRecord> metadata,
		Map<String, List<StremioMetaProviderRecord>> metadataProviders,
		Map<String, StremioProgressRecord> progress,
		StremioRepository.SourceState sourceState) {

	public StremioSessionData {
		videos = Map.copyOf(Objects.requireNonNull(videos, "videos"));
		metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
		Objects.requireNonNull(metadataProviders, "metadataProviders");
		Map<String, List<StremioMetaProviderRecord>> providers = new LinkedHashMap<>();
		metadataProviders.forEach((key, value) -> providers.put(key, List.copyOf(value)));
		metadataProviders = Map.copyOf(providers);
		progress = Map.copyOf(Objects.requireNonNull(progress, "progress"));
		Objects.requireNonNull(sourceState, "sourceState");
	}
}
