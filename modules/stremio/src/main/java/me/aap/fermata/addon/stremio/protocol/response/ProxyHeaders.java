package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ProxyHeaders(Map<String, String> request, Map<String, String> response) {
	public static final ProxyHeaders EMPTY = new ProxyHeaders(Map.of(), Map.of());

	public ProxyHeaders {
		request = immutableCopy(request, "request");
		response = immutableCopy(response, "response");
	}

	private static Map<String, String> immutableCopy(Map<String, String> source, String name) {
		Objects.requireNonNull(source, name);
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}

	@Override
	public String toString() {
		return "ProxyHeaders[request=<redacted:" + request.size() + ">, response=<redacted:" +
				response.size() + ">]";
	}
}
