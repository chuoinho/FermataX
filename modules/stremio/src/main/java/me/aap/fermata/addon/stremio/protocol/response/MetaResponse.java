package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

public record MetaResponse(StremioMeta meta) {
	public MetaResponse {
		Objects.requireNonNull(meta, "meta");
	}

	@Override
	public String toString() {
		return "MetaResponse[meta=<redacted>]";
	}
}
