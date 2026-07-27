package me.aap.fermata.addon.stremio.protocol.response;

import java.util.List;
import java.util.Objects;

public record InfoHashStreamTarget(String infoHash, Integer fileIndex, List<String> sources)
		implements StreamTarget {
	public InfoHashStreamTarget {
		Objects.requireNonNull(infoHash, "infoHash");
		if (infoHash.isBlank()) throw new IllegalArgumentException("infoHash cannot be blank");
		if ((fileIndex != null) && (fileIndex < 0)) throw new IllegalArgumentException("Invalid fileIndex");
		sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
	}

	@Override
	public String toString() {
		return "InfoHashStreamTarget[infoHash=<redacted>, fileIndex=" + fileIndex +
				", sources=<redacted:" + sources.size() + ">]";
	}
}
