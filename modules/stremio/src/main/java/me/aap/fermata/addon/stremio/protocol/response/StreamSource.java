package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

/** One bounded archive source from a Stremio stream response. */
public record StreamSource(String url, Long bytes) {
	public StreamSource {
		if (Objects.requireNonNull(url, "url").isBlank()) {
			throw new IllegalArgumentException("url cannot be blank");
		}
		if ((bytes != null) && (bytes < 0L)) {
			throw new IllegalArgumentException("bytes cannot be negative");
		}
	}

	@Override
	public String toString() {
		return "StreamSource[url=<redacted>, bytes=" + bytes + ']';
	}
}
