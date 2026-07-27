package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

public record DirectStreamTarget(String url) implements StreamTarget {
	public DirectStreamTarget {
		Objects.requireNonNull(url, "url");
		if (url.isBlank()) throw new IllegalArgumentException("url cannot be blank");
	}

	@Override
	public String toString() {
		return "DirectStreamTarget[url=<redacted>]";
	}
}
