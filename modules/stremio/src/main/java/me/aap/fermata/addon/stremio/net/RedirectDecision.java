package me.aap.fermata.addon.stremio.net;

import java.util.Map;
import java.util.Objects;

public record RedirectDecision(ValidatedEndpoint target, Map<String, String> requestHeaders) {
	public RedirectDecision {
		Objects.requireNonNull(target, "target");
		requestHeaders = Map.copyOf(requestHeaders);
	}

	@Override
	public String toString() {
		return "RedirectDecision[redacted]";
	}
}
