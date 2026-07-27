package me.aap.fermata.addon.stremio.net;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SensitiveHeaderPolicy {
	private static final Set<String> SENSITIVE = Set.of(
			"authorization", "proxy-authorization", "cookie", "cookie2", "origin", "referer",
			"x-api-key", "api-key", "x-auth-token", "if-none-match", "if-modified-since");

	private SensitiveHeaderPolicy() {
	}

	public static Map<String, String> forRedirect(
			NormalizedEndpoint source,
			NormalizedEndpoint target,
			Map<String, String> requestHeaders) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(requestHeaders, "requestHeaders");
		if (source.origin().equals(target.origin())) return Map.copyOf(requestHeaders);

		var filtered = new LinkedHashMap<String, String>(requestHeaders.size());
		requestHeaders.forEach((name, value) -> {
			Objects.requireNonNull(name, "header name");
			Objects.requireNonNull(value, "header value");
			if (!SENSITIVE.contains(name.toLowerCase(Locale.ROOT))) filtered.put(name, value);
		});
		return Map.copyOf(filtered);
	}

	public static boolean isSensitive(String name) {
		return SENSITIVE.contains(Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT));
	}
}
