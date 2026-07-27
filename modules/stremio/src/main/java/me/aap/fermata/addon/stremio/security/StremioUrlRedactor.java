package me.aap.fermata.addon.stremio.security;

import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Creates a storage/log-safe endpoint identity without retaining provider configuration. */
public final class StremioUrlRedactor {
	private static final String MANIFEST_PATH = "/manifest.json";
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private StremioUrlRedactor() {
	}

	@Nullable
	public static String forStorage(@Nullable String value) {
		if (value == null) return null;
		final URI uri;
		try {
			uri = new URI(value.trim()).normalize();
		} catch (URISyntaxException ex) {
			return null;
		}
		if (!uri.isAbsolute() || uri.isOpaque() || (uri.getHost() == null)) return null;

		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) return null;
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		StringBuilder result = new StringBuilder(scheme).append("://");
		if (host.indexOf(':') >= 0) result.append('[').append(host).append(']');
		else result.append(host);
		if (uri.getPort() >= 0) result.append(':').append(uri.getPort());

		String path = uri.getRawPath();
		if (isPlainManifestPath(path)) result.append(MANIFEST_PATH);
		else result.append("/.stremio/").append(digestIdentity(uri));
		return result.toString();
	}

	public static String forMessage(@Nullable String value) {
		String redacted = forStorage(value);
		return (redacted == null) ? "<invalid Stremio URL>" : redacted;
	}

	private static boolean isPlainManifestPath(@Nullable String path) {
		if ((path == null) || path.isEmpty()) return false;
		int slash = path.lastIndexOf('/');
		String terminal = (slash < 0) ? path : path.substring(slash + 1);
		return terminal.equalsIgnoreCase(MANIFEST_PATH.substring(1));
	}

	private static String digestIdentity(URI uri) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String identity = String.valueOf(uri.getRawUserInfo()) + '\n' +
					String.valueOf(uri.getRawPath()) + '\n' +
					String.valueOf(uri.getRawQuery()) + '\n' +
					String.valueOf(uri.getRawFragment());
			byte[] bytes = digest.digest(identity.getBytes(StandardCharsets.UTF_8));
			char[] result = new char[24];
			for (int i = 0; i < 12; i++) {
				result[i * 2] = HEX[(bytes[i] >>> 4) & 0x0F];
				result[(i * 2) + 1] = HEX[bytes[i] & 0x0F];
			}
			return new String(result);
		} catch (NoSuchAlgorithmException ex) {
			throw new AssertionError("SHA-256 is unavailable", ex);
		}
	}
}
