package me.aap.fermata.addon.stremio.ui.source;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable ordered state exposed by {@link SourceUiGateway}. */
public record SourceUiSnapshot(long revision, List<SourceUiItem> sources) {
	public SourceUiSnapshot {
		if (revision < 0) throw new IllegalArgumentException("revision is negative");
		sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < sources.size(); i++) {
			SourceUiItem source = Objects.requireNonNull(sources.get(i), "source");
			if (source.position() != i) {
				throw new IllegalArgumentException("Source positions must be contiguous");
			}
			if (!ids.add(source.sourceUuid())) {
				throw new IllegalArgumentException("Duplicate source UUID");
			}
		}
	}

	public static SourceUiSnapshot empty() {
		return new SourceUiSnapshot(0, List.of());
	}

	public SourceUiItem source(String sourceUuid) {
		for (SourceUiItem source : sources) {
			if (source.sourceUuid().equals(sourceUuid)) return source;
		}
		return null;
	}
}
