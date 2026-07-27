package me.aap.fermata.addon.stremio.protocol.response;

import java.util.List;
import java.util.Objects;

/** Multi-volume archive target defined by the Stremio stream protocol. */
public record ArchiveStreamTarget(
		Kind kind,
		List<StreamSource> sources,
		Integer fileIndex,
		String fileMustInclude) implements StreamTarget {
	public ArchiveStreamTarget {
		Objects.requireNonNull(kind, "kind");
		sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
		if (sources.isEmpty()) throw new IllegalArgumentException("sources cannot be empty");
		if ((fileIndex != null) && (fileIndex < 0)) {
			throw new IllegalArgumentException("fileIndex cannot be negative");
		}
	}

	@Override
	public String toString() {
		return "ArchiveStreamTarget[kind=" + kind + ", sources=" + sources.size() +
				", fileIndex=" + fileIndex + ", fileMustInclude=<redacted>]";
	}

	public enum Kind { RAR, ZIP, SEVEN_ZIP, TGZ, TAR }
}
