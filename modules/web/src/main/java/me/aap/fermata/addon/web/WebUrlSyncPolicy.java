package me.aap.fermata.addon.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public final class WebUrlSyncPolicy {
	private static final int MAX_URL_BYTES = 16 * 1024;

	private WebUrlSyncPolicy() {
	}

	public static boolean accepts(String value) {
		if ((value == null) || value.isEmpty() || (value.length() > MAX_URL_BYTES)) return false;
		if (value.codePoints().anyMatch(Character::isISOControl)) return false;
		if (value.getBytes(StandardCharsets.UTF_8).length > MAX_URL_BYTES) return false;

		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme();
			return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) &&
					(uri.getHost() != null) && !uri.getHost().isEmpty() &&
					(uri.getRawUserInfo() == null);
		} catch (URISyntaxException invalid) {
			return false;
		}
	}
}
