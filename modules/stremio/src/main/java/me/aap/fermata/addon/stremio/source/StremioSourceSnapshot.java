package me.aap.fermata.addon.stremio.source;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;

/** Immutable, ordered source-management state. */
public record StremioSourceSnapshot(
		long revision,
		List<StremioSourceRecord> sources,
		boolean cinemetaInstallHandled) {

	public StremioSourceSnapshot {
		if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
		sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
		Set<String> sourceIds = new HashSet<>();
		Set<String> fingerprints = new HashSet<>();
		for (int i = 0; i < sources.size(); i++) {
			StremioSourceRecord source = Objects.requireNonNull(sources.get(i), "source");
			if (source.position() != i) {
				throw new IllegalArgumentException("Source positions must be contiguous and ordered");
			}
			if (!sourceIds.add(source.sourceUuid())) {
				throw new IllegalArgumentException("Duplicate source UUID");
			}
			if (!fingerprints.add(source.transportFingerprint())) {
				throw new IllegalArgumentException("Duplicate transport fingerprint");
			}
		}
	}

	public static StremioSourceSnapshot empty() {
		return new StremioSourceSnapshot(0, List.of(), false);
	}

	public StremioSourceRecord source(String sourceUuid) {
		for (StremioSourceRecord source : sources) {
			if (source.sourceUuid().equals(sourceUuid)) return source;
		}
		return null;
	}

	public StremioSourceSnapshot next(
			List<StremioSourceRecord> replacement, boolean installHandled) {
		return new StremioSourceSnapshot(revision + 1, replacement, installHandled);
	}
}
