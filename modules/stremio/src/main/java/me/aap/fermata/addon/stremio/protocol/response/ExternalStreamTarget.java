package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

public record ExternalStreamTarget(String url) implements StreamTarget {
	public ExternalStreamTarget {
		Objects.requireNonNull(url, "url");
		if (url.isBlank()) throw new IllegalArgumentException("url cannot be blank");
	}

	@Override
	public String toString() {
		return "ExternalStreamTarget[url=<redacted>]";
	}
}
