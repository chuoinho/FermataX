package me.aap.fermata.addon.stremio.net.http;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.RequestGeneration;

public record HttpRequestSpec(
		URI uri,
		Map<String, String> headers,
		long maxBodyBytes,
		NetworkConsent consent,
		HttpDeadlines deadlines,
		RequestGeneration.Token generation,
		BooleanSupplier validity) {
	public HttpRequestSpec(URI uri, Map<String, String> headers, long maxBodyBytes,
			NetworkConsent consent, HttpDeadlines deadlines,
			RequestGeneration.Token generation) {
		this(uri, headers, maxBodyBytes, consent, deadlines, generation, () -> true);
	}

	public HttpRequestSpec {
		Objects.requireNonNull(uri, "uri");
		Objects.requireNonNull(headers, "headers");
		Objects.requireNonNull(consent, "consent");
		Objects.requireNonNull(deadlines, "deadlines");
		Objects.requireNonNull(validity, "validity");
		if (maxBodyBytes <= 0) throw new IllegalArgumentException("maxBodyBytes must be positive");
		var copy = new LinkedHashMap<String, String>(headers.size());
		headers.forEach((name, value) -> {
			Objects.requireNonNull(name, "header name");
			Objects.requireNonNull(value, "header value");
			String normalized = name.trim().toLowerCase(Locale.ROOT);
			if (normalized.isEmpty() || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0 ||
					value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
				throw new IllegalArgumentException("Invalid HTTP header");
			}
			copy.put(normalized, value);
		});
		headers = Map.copyOf(copy);
	}

	public static HttpRequestSpec get(URI uri, long maxBodyBytes, NetworkConsent consent) {
		return new HttpRequestSpec(uri, Map.of("accept", "application/json"), maxBodyBytes,
				consent, HttpDeadlines.DEFAULT, null);
	}

	public HttpRequestSpec withHeaders(Map<String, String> replacement) {
		return new HttpRequestSpec(uri, replacement, maxBodyBytes, consent, deadlines, generation,
				validity);
	}

	public boolean isCurrent() {
		return ((generation == null) || generation.isCurrent()) && validity.getAsBoolean();
	}

	@Override
	public String toString() {
		return "HttpRequestSpec[redacted, maxBodyBytes=" + maxBodyBytes + ']';
	}
}
