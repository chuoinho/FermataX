package me.aap.fermata.addon.stremio.net.http;

import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.stremio.net.ValidatedEndpoint;
import me.aap.utils.net.TlsTrustPolicy;

public record TransportRequest(
		ValidatedEndpoint endpoint,
		Map<String, String> headers,
		HttpDeadlines deadlines,
		long maxBodyBytes,
		TlsTrustPolicy tlsTrustPolicy) {
	public TransportRequest {
		Objects.requireNonNull(endpoint, "endpoint");
		headers = Map.copyOf(headers);
		Objects.requireNonNull(deadlines, "deadlines");
		Objects.requireNonNull(tlsTrustPolicy, "tlsTrustPolicy");
		if (maxBodyBytes <= 0) throw new IllegalArgumentException("maxBodyBytes must be positive");
	}

	public TransportRequest(ValidatedEndpoint endpoint, Map<String, String> headers,
			HttpDeadlines deadlines, long maxBodyBytes) {
		this(endpoint, headers, deadlines, maxBodyBytes, TlsTrustPolicy.STRICT);
	}

	@Override
	public String toString() {
		return "TransportRequest[redacted, maxBodyBytes=" + maxBodyBytes + ']';
	}
}
