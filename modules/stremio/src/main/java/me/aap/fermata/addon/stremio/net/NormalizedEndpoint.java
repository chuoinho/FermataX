package me.aap.fermata.addon.stremio.net;

import java.net.URI;
import java.util.Objects;

public record NormalizedEndpoint(URI uri, String scheme, String host, int port, String origin) {
	public NormalizedEndpoint {
		Objects.requireNonNull(uri, "uri");
		Objects.requireNonNull(scheme, "scheme");
		Objects.requireNonNull(host, "host");
		Objects.requireNonNull(origin, "origin");
	}
}
