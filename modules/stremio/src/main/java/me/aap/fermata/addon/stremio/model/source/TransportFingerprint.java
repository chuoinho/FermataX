package me.aap.fermata.addon.stremio.model.source;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/** Secret-aware transport hash used only to detect duplicate source installations. */
public final class TransportFingerprint {
	private TransportFingerprint() {
	}

	public static String create(String transportIdentity) {
		String normalized = normalize(transportIdentity);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	static String normalize(String transportIdentity) {
		Objects.requireNonNull(transportIdentity, "transportIdentity");
		String value = transportIdentity.trim();
		if (value.isEmpty()) throw new IllegalArgumentException("transportIdentity cannot be blank");

		final URI uri;
		try {
			uri = new URI(value).normalize();
		} catch (URISyntaxException ex) {
			throw new IllegalArgumentException("transportIdentity must be a valid URI", ex);
		}
		if (!uri.isAbsolute()) {
			throw new IllegalArgumentException("transportIdentity must be an absolute URI");
		}

		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		if (uri.isOpaque()) {
			return scheme + ':' + uri.getRawSchemeSpecificPart();
		}

		String host = uri.getHost();
		if (host == null) {
			// Non-server transports retain their normalized authority/path without the fragment.
			return stripFragment(uri.toASCIIString()).replaceFirst("^[^:]+", scheme);
		}

		int port = uri.getPort();
		if (((port == 80) && scheme.equals("http")) ||
				((port == 443) && scheme.equals("https"))) port = -1;
		StringBuilder result = new StringBuilder(scheme).append("://");
		if (uri.getRawUserInfo() != null) result.append(uri.getRawUserInfo()).append('@');
		if (host.indexOf(':') >= 0) result.append('[').append(host.toLowerCase(Locale.ROOT)).append(']');
		else result.append(host.toLowerCase(Locale.ROOT));
		if (port >= 0) result.append(':').append(port);
		String path = uri.getRawPath();
		result.append((path == null) || path.isEmpty() ? "/" : path);
		if (uri.getRawQuery() != null) result.append('?').append(uri.getRawQuery());
		return result.toString();
	}

	private static String stripFragment(String value) {
		int fragment = value.indexOf('#');
		return (fragment < 0) ? value : value.substring(0, fragment);
	}
}
