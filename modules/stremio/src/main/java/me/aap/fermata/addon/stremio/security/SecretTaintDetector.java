package me.aap.fermata.addon.stremio.security;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative guard against persisting credentials hidden in provider JSON or URLs. */
public final class SecretTaintDetector {
	private static final Pattern URL = Pattern.compile(
			"(?i)(?:https?|stremio)://[^\\s\\\"'<>]+", Pattern.UNICODE_CASE);
	private static final Pattern INLINE_SECRET = Pattern.compile(
			"(?i)(?:^|[\\s?&;,])(?:access[_-]?token|api[_-]?key|auth(?:orization)?|" +
					"cookie|credential|jwt|password|passwd|refresh[_-]?token|secret|" +
					"session(?:id)?|signature|token)\\s*=\\s*[^\\s&,;]+", Pattern.UNICODE_CASE);
	private static final Pattern BEARER = Pattern.compile(
			"(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]{8,}", Pattern.UNICODE_CASE);
	private static final Set<String> SECRET_KEYS = Set.of(
			"accesstoken", "apikey", "auth", "authorization", "bearer", "cookie",
			"credential", "jwt", "password", "passwd", "refreshtoken", "secret",
			"session", "sessionid", "signature", "token");

	private SecretTaintDetector() {
	}

	public static boolean isTainted(@Nullable String value, Collection<String> knownSecrets) {
		if ((value == null) || value.isEmpty()) return false;
		if (containsKnownSecret(value, knownSecrets)) return true;
		if (INLINE_SECRET.matcher(value).find() || BEARER.matcher(value).find()) return true;
		String trimmed = value.trim();
		if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
			try {
				Object json = trimmed.startsWith("{") ? new JSONObject(trimmed) : new JSONArray(trimmed);
				if (containsJsonSecret(json, knownSecrets)) return true;
			} catch (JSONException ignored) {
				// Malformed provider data still receives the URL and known-secret scans below.
			}
		}

		Matcher matcher = URL.matcher(value);
		while (matcher.find()) {
			if (isSecretBearingUrl(trimTrailingPunctuation(matcher.group()))) return true;
		}
		return false;
	}

	public static boolean isTainted(@Nullable String value) {
		return isTainted(value, Set.of());
	}

	/**
	 * Manifest-aware guard. Public artwork CDNs often use opaque path IDs; those are allowed only
	 * in known artwork fields. Credential-bearing URLs and all other JSON fields stay strict.
	 */
	public static boolean isManifestTainted(@Nullable String value,
			Collection<String> knownSecrets) {
		if ((value == null) || value.isEmpty()) return false;
		if (containsKnownSecret(value, knownSecrets) ||
				INLINE_SECRET.matcher(value).find() || BEARER.matcher(value).find()) return true;
		String trimmed = value.trim();
		if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
			return isTainted(value, knownSecrets);
		}
		try {
			Object json = trimmed.startsWith("{") ? new JSONObject(trimmed) : new JSONArray(trimmed);
			return containsManifestSecret(json, knownSecrets, null);
		} catch (JSONException ignored) {
			return isTainted(value, knownSecrets);
		}
	}

	public static boolean isManifestTainted(@Nullable String value) {
		return isManifestTainted(value, Set.of());
	}

	/**
	 * Extracts credential values that may be reflected independently by a provider. Host names,
	 * ordinary path words and non-sensitive query parameters are deliberately excluded.
	 */
	public static Collection<String> extractTransportSecrets(@Nullable String transportUrl) {
		if ((transportUrl == null) || transportUrl.isBlank()) return Set.of();
		final URI uri;
		try {
			uri = new URI(transportUrl.trim());
		} catch (URISyntaxException error) {
			return Set.of();
		}

		Set<String> secrets = new LinkedHashSet<>();
		String userInfo = uri.getRawUserInfo();
		if (userInfo != null) {
			addSecretForms(secrets, userInfo, false);
			for (String part : userInfo.split(":", -1)) addSecretForms(secrets, part, false);
		}

		String query = uri.getRawQuery();
		if (query != null) {
			for (String field : query.split("&")) {
				int equals = field.indexOf('=');
				if (equals <= 0) continue;
				String key = decode(field.substring(0, equals), true);
				if (isTransportSecretKey(key)) {
					addSecretForms(secrets, field.substring(equals + 1), true);
				}
			}
		}

		extractPathSecrets(secrets, uri.getRawPath());
		return Set.copyOf(secrets);
	}

	private static boolean containsJsonSecret(Object value, Collection<String> knownSecrets) {
		if ((value == null) || (value == JSONObject.NULL)) return false;
		if (value instanceof JSONObject object) {
			Iterator<String> keys = object.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				Object child = object.opt(key);
				if (isSecretKey(key) && hasValue(child)) return true;
				if (containsJsonSecret(child, knownSecrets)) return true;
			}
			return false;
		}
		if (value instanceof JSONArray array) {
			for (int i = 0; i < array.length(); i++) {
				if (containsJsonSecret(array.opt(i), knownSecrets)) return true;
			}
			return false;
		}
		if (!(value instanceof String text)) return false;
		return containsKnownSecret(text, knownSecrets) || isSecretBearingUrl(text);
	}

	private static boolean containsManifestSecret(Object value, Collection<String> knownSecrets,
			String fieldName) {
		if ((value == null) || (value == JSONObject.NULL)) return false;
		if (value instanceof JSONObject object) {
			Iterator<String> keys = object.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				Object child = object.opt(key);
				if (isSecretKey(key) && hasValue(child)) return true;
				if (containsManifestSecret(child, knownSecrets, key)) return true;
			}
			return false;
		}
		if (value instanceof JSONArray array) {
			for (int i = 0; i < array.length(); i++) {
				if (containsManifestSecret(array.opt(i), knownSecrets, fieldName)) return true;
			}
			return false;
		}
		if (!(value instanceof String text)) return false;
		if (containsKnownSecret(text, knownSecrets) || INLINE_SECRET.matcher(text).find() ||
				BEARER.matcher(text).find()) return true;
		Matcher matcher = URL.matcher(text);
		while (matcher.find()) {
			String url = trimTrailingPunctuation(matcher.group());
			if (isSecretBearingUrl(url, !isPublicArtworkField(fieldName))) return true;
		}
		return false;
	}

	private static boolean isPublicArtworkField(String fieldName) {
		if (fieldName == null) return false;
		return switch (fieldName.toLowerCase(Locale.ROOT)) {
			case "art", "background", "banner", "cover", "fanart", "icon", "logo",
					"poster", "thumbnail" -> true;
			default -> false;
		};
	}

	private static boolean containsKnownSecret(String value, Collection<String> secrets) {
		for (String secret : secrets) {
			if ((secret == null) || secret.isEmpty()) continue;
			if (value.contains(secret)) return true;
			String encoded = URLEncoder.encode(secret, StandardCharsets.UTF_8);
			if (value.contains(encoded) || value.contains(encoded.replace("+", "%20"))) return true;
			String base64 = Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
			if (value.contains(base64)) return true;
			String base64Url = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(secret.getBytes(StandardCharsets.UTF_8));
			if (value.contains(base64Url)) return true;
		}
		return false;
	}

	private static boolean isSecretKey(String key) {
		String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
		for (String secret : SECRET_KEYS) {
			if (normalized.equals(secret) || normalized.endsWith(secret)) return true;
		}
		return false;
	}

	private static boolean isTransportSecretKey(String key) {
		String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
		return normalized.equals("key") || isSecretKey(key);
	}

	private static void extractPathSecrets(Set<String> secrets, String rawPath) {
		if ((rawPath == null) || rawPath.isEmpty()) return;
		String[] segments = rawPath.replace('\\', '/').split("/");
		boolean previousWasMarker = false;
		for (String rawSegment : segments) {
			if (rawSegment.isEmpty()) continue;
			String segment = decode(rawSegment, false);
			int rawEquals = rawSegment.indexOf('=');
			if ((rawEquals > 0) && isTransportSecretKey(
					decode(rawSegment.substring(0, rawEquals), false))) {
				addSecretForms(secrets, rawSegment.substring(rawEquals + 1), false);
				previousWasMarker = false;
				continue;
			}
			int equals = segment.indexOf('=');
			if ((equals > 0) && isTransportSecretKey(segment.substring(0, equals))) {
				addSecretForms(secrets, segment.substring(equals + 1), false);
				previousWasMarker = false;
				continue;
			}
			if (previousWasMarker) {
				if (!isOrdinaryResourceName(segment)) {
					addSecretForms(secrets, rawSegment, false);
				}
				previousWasMarker = false;
				continue;
			}
			if (isTransportSecretKey(segment)) {
				previousWasMarker = true;
				continue;
			}
			if (isOpaqueCredentialSegment(segment)) addSecretForms(secrets, rawSegment, false);
		}
	}

	private static boolean isOpaqueCredentialSegment(String value) {
		if ((value.length() < 8) || value.contains(".") ||
				!value.matches("[A-Za-z0-9_-]+")) return false;
		int letters = 0;
		int digits = 0;
		int transitions = 0;
		int previousKind = 0;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			int kind = 0;
			if (Character.isLetter(c)) {
				letters++;
				kind = 1;
			} else if (Character.isDigit(c)) {
				digits++;
				kind = 2;
			}
			if ((kind != 0) && (previousKind != 0) && (kind != previousKind)) transitions++;
			if (kind != 0) previousKind = kind;
		}
		return ((letters >= 2) && (digits >= 2) && (transitions >= 3)) ||
				((value.length() >= 12) && value.matches("(?i)[0-9a-f]+"));
	}

	private static boolean isOrdinaryResourceName(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		return lower.equals("manifest.json") || lower.equals("index.html") ||
				lower.equals("configure") || lower.equals("configure.html");
	}

	private static void addSecretForms(Set<String> secrets, String raw, boolean queryComponent) {
		String value = raw;
		for (int i = 0; i < 3; i++) {
			if (value.length() >= 4) secrets.add(value);
			String decoded = decode(value, queryComponent);
			if (decoded.equals(value)) break;
			value = decoded;
		}
	}

	private static String decode(String value, boolean queryComponent) {
		try {
			return URLDecoder.decode(queryComponent ? value : value.replace("+", "%2B"),
					StandardCharsets.UTF_8);
		} catch (IllegalArgumentException error) {
			return value;
		}
	}

	private static boolean hasValue(Object value) {
		return (value != null) && (value != JSONObject.NULL) &&
				(!(value instanceof String text) || !text.isEmpty());
	}

	private static boolean isSecretBearingUrl(String value) {
		return isSecretBearingUrl(value, true);
	}

	private static boolean isSecretBearingUrl(String value, boolean rejectOpaquePath) {
		try {
			URI uri = new URI(value);
			if (!uri.isAbsolute()) return false;
			if ((uri.getRawUserInfo() != null) && !uri.getRawUserInfo().isEmpty()) return true;
			String query = uri.getRawQuery();
			if (ArtworkUrlSanitizer.containsSecretPathMarker(uri.getRawPath()) ||
					(rejectOpaquePath &&
							ArtworkUrlSanitizer.containsOpaquePathToken(uri.getRawPath()))) return true;
			if ((query == null) || query.isEmpty()) return false;
			for (String field : query.split("&")) {
				int equals = field.indexOf('=');
				if ((equals >= 0) && (equals < field.length() - 1)) return true;
			}
			return false;
		} catch (URISyntaxException ex) {
			return false;
		}
	}

	private static String trimTrailingPunctuation(String value) {
		int end = value.length();
		while ((end > 0) && ".,;!)]}".indexOf(value.charAt(end - 1)) >= 0) end--;
		return value.substring(0, end);
	}
}
