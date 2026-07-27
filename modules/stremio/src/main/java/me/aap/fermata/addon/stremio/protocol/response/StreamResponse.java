package me.aap.fermata.addon.stremio.protocol.response;

import java.util.List;
import java.util.Objects;

public record StreamResponse(List<StremioStream> streams) {
	public StreamResponse {
		streams = List.copyOf(Objects.requireNonNull(streams, "streams"));
	}

	@Override
	public String toString() {
		return "StreamResponse[streams=" + streams.size() + "]";
	}
}
