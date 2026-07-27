package me.aap.fermata.addon.stremio.net.http;

import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.stremio.net.ValidatedEndpoint;

public record TransportRequest(
		ValidatedEndpoint endpoint,
		Map<String, String> headers,
		HttpDeadlines deadlines,
		long maxBodyBytes) {
	public TransportRequest {
		Objects.requireNonNull(endpoint, "endpoint");
		headers = Map.copyOf(headers);
		Objects.requireNonNull(deadlines, "deadlines");
		if (maxBodyBytes <= 0) throw new IllegalArgumentException("maxBodyBytes must be positive");
	}

	@Override
	public String toString() {
		return "TransportRequest[redacted, maxBodyBytes=" + maxBodyBytes + ']';
	}
}
