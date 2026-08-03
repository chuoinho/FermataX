package me.aap.fermata.addon.stremio.security;

import me.aap.utils.net.NetUtils;

import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

	/** Prevents remote artwork from bypassing the Stremio network policy through the shared loader. */
public final class ArtworkUrlSanitizer {
	private static final Pattern IMDB_ID = Pattern.compile(
			"(?i)^(?:imdb:)?(tt[0-9]{5,12})$");
	private static final Set<String> SAFE_TRANSFORM_QUERY_KEYS = Set.of(
			"fit", "format", "h", "height", "q", "quality", "resize", "w", "width");
	private ArtworkUrlSanitizer() {
	}

	@Nullable
	public static String sanitize(@Nullable String value) {
		if ((value == null) || value.isBlank()) return null;
		final URI uri;
		try {
			uri = URI.create(value.trim()).normalize();
		} catch (IllegalArgumentException error) {
			return null;
		}
		String scheme = lower(uri.getScheme());
		if (!"https".equals(scheme) || uri.isOpaque() ||
				(uri.getHost() == null) || (uri.getRawUserInfo() != null) ||
				(uri.getRawFragment() != null)) return null;
		String path = uri.getRawPath();
		if ((path == null) || path.isBlank() || containsSecretPathMarker(path) ||
				containsOpaquePathToken(path) ||
				!hasOnlySafeTransformQuery(uri.getRawQuery())) return null;
		return uri.toASCIIString();
	}

	/**
	 * Returns a public artwork fallback for canonical IMDb content when a provider omits
	 * artwork or returns a URL that cannot be safely fetched.
	 */
	@Nullable
	public static String canonicalPoster(@Nullable String type, @Nullable String id) {
		if ((type == null) || (id == null)) return null;
		String normalizedType = type.strip().toLowerCase(Locale.ROOT);
		if (!normalizedType.equals("movie") && !normalizedType.equals("series") &&
				!normalizedType.equals("tv")) return null;
		Matcher matcher = IMDB_ID.matcher(id.strip());
		if (!matcher.matches()) return null;
		return "https://images.metahub.space/poster/medium/" +
				matcher.group(1).toLowerCase(Locale.ROOT) + "/img";
	}

	public static boolean containsSecretPathMarker(String path) {
		if ((path == null) || path.isEmpty()) return false;
		String normalized = path;
		for (int i = 0; i < 2; i++) {
			try {
				String decoded = NetUtils.urlDecode(normalized);
				if (decoded.equals(normalized)) break;
				normalized = decoded;
			} catch (IllegalArgumentException error) {
				return true;
			}
		}
		normalized = normalized.toLowerCase(Locale.ROOT).replace('\\', '/');
		return normalized.matches(".*(?:^|/)(?:access[_-]?token|api[_-]?key|auth|bearer|" +
				"credential|jwt|password|secret|session|signature|token)(?:/|=|%3d|$).*");
	}

	/** Detects credential-like opaque path segments, including short provider tokens. */
	public static boolean containsOpaquePathToken(String path) {
		if ((path == null) || path.isEmpty()) return false;
		String normalized = path;
		for (int i = 0; i < 2; i++) {
			try {
				String decoded = NetUtils.urlDecode(normalized);
				if (decoded.equals(normalized)) break;
				normalized = decoded;
			} catch (IllegalArgumentException error) {
				return true;
			}
		}
		for (String segment : normalized.replace('\\', '/').split("/")) {
			if ((segment.length() < 6) || segment.contains(".") ||
					!segment.matches("[A-Za-z0-9_-]+")) continue;
			// Public IMDb content IDs are routing identifiers, not bearer credentials.
			if (segment.matches("(?i)tt[0-9]{7,10}")) continue;
			boolean letter = false;
			boolean digit = false;
			for (int i = 0; i < segment.length(); i++) {
				char c = segment.charAt(i);
				letter |= Character.isLetter(c);
				digit |= Character.isDigit(c);
			}
			if (letter && digit) return true;
			if ((segment.length() >= 8) && segment.matches("(?i)[0-9a-f]+")) return true;
		}
		return false;
	}

	private static boolean hasOnlySafeTransformQuery(String query) {
		if ((query == null) || query.isEmpty()) return true;
		for (String field : query.split("&")) {
			int equals = field.indexOf('=');
			String key = ((equals < 0) ? field : field.substring(0, equals))
					.toLowerCase(Locale.ROOT);
			if (!SAFE_TRANSFORM_QUERY_KEYS.contains(key)) return false;
		}
		return true;
	}

	private static String lower(String value) {
		return (value == null) ? null : value.toLowerCase(Locale.ROOT);
	}
}
