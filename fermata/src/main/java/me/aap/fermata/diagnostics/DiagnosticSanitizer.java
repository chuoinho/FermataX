package me.aap.fermata.diagnostics;

import java.io.File;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes credentials and private user data before an event reaches memory-backed diagnostics. */
public final class DiagnosticSanitizer {
	public static final String REDACTED = "[redacted]";
	private static final int MAX_DEPTH = 5;
	private static final int MAX_COLLECTION_ITEMS = 64;
	private static final int MAX_THROWABLE_CAUSES = 8;
	private static final int MAX_STACK_FRAMES = 64;
	private static final int MAX_EVENT_VALUE_CHARS = 8192;
	private static final Pattern URI_PATTERN = Pattern.compile(
			"(?i)\\b(?:[a-z][a-z0-9+.-]*://|magnet:\\?)[^\\s\\\"'<>]+");
	private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
			"(?i)\\b(?:proxy-)?authorization\\s*[:=]\\s*(?:(?:bearer|basic)\\s+)?" +
					"[^\\s,;]+");
	private static final Pattern COOKIE_PATTERN = Pattern.compile(
			"(?i)\\b(?:set-cookie|cookie)\\s*[:=]\\s*[^\\r\\n]+");
	private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
			"(?i)\\b(?:access[_-]?token|refresh[_-]?token|id[_-]?token|api[_-]?key|" +
					"password|passwd|credential|client[_-]?secret|secret)\\s*[:=]\\s*" +
					"(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}&]+)");
	private static final Pattern BEARER_PATTERN = Pattern.compile(
			"(?i)\\b(?:bearer|basic)\\s+[a-z0-9._~+/=-]+");
	private static final Pattern WINDOWS_PATH_PATTERN = Pattern.compile(
			"(?i)(?:[a-z]:[\\\\/]|\\\\\\\\)[^\\s\\\"'<>]+");
	private static final Pattern UNIX_PATH_PATTERN = Pattern.compile(
			"(?i)(?<![a-z0-9:])/(?:data|storage|sdcard|mnt|home|users|private|var|tmp|" +
					"cache)(?:/[^\\s\\\"'<>]*)?");
	private static final Pattern URI_SECRET_PATH_PATTERN = Pattern.compile(
			"(?i)/(?:token|password|passwd|secret|credential|api[_-]?key)(?:/[^/?#]*)?");
	private static final Pattern OPAQUE_SECRET_PATTERN = Pattern.compile(
			"^[A-Za-z0-9_+/=-]{40,}$");
	private static final Pattern METADATA_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
	private static final Pattern CORRELATION_IDENTIFIER =
			Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
	private static final Set<String> EVENT_ATTRIBUTE_KEYS = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList(
					"accepted", "activity_id", "adaptive_allowed", "addon_id", "allowed", "app_fullscreen",
					"attached", "attempt", "auto", "automotive", "bound", "browser_fullscreen", "byte_count",
					"command_type", "count", "current_engine_class", "current_engine_id",
					"duration_ms", "endpoint", "engine_class", "engine_fingerprint", "engine_id", "epoch",
					"error", "error_class", "error_code", "failure", "failure_class_id",
					"connection", "enabled", "failure_code", "format", "from_id", "generation", "genre", "has_binder",
					"has_callback", "height", "importance", "input_action", "input_origin",
					"input_source", "item", "item_class", "key_code", "mapped_key",
					"item_fingerprint", "language_code", "locale", "main_frame", "media_action",
					"missed_probes", "mute_known", "muted", "operation", "owns_playback", "p2p",
					"playback_revision", "playback_state",
					"peer_count", "phase", "playing", "position", "process_hash", "process_name",
					"provider_class", "pss_kb", "query",
					"provider_error", "pss_bytes", "rate_bucket", "reason", "remote", "repeat_count", "request",
					"reason_code", "request_type", "result_count", "revision", "rss_bytes", "rss_kb",
					"sdk", "seed_count",
					"scan_code", "device_id", "selected_engine_class", "selected_engine_id",
					"session_active", "skip", "stack", "stale", "stalled_for_ms", "supported_actions",
					"source", "state", "status", "surface_known", "surface_valid", "thread_dump", "title",
					"target_id", "timestamp", "token_generation", "type", "url", "video", "visible", "web_attached",
					"uri", "web_height", "web_visible", "web_width", "width", "_truncated")));
	private static final Set<String> FINGERPRINT_VALUE_KEYS = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList("endpoint", "genre", "item", "process_name",
					"provider_error", "query", "request", "source", "title", "url", "uri")));
	private static final Object UNSUPPORTED_EVENT_VALUE = new Object();

	private final int maxStringChars;
	private final String fingerprintSalt;

	public DiagnosticSanitizer() {
		this(DiagnosticConfig.DEFAULT_MAX_STRING_CHARS, "diagnostics");
	}

	public DiagnosticSanitizer(int maxStringChars) {
		this(maxStringChars, "diagnostics");
	}

	DiagnosticSanitizer(int maxStringChars, String fingerprintSalt) {
		if (maxStringChars <= 0) throw new IllegalArgumentException("maxStringChars must be positive");
		this.maxStringChars = maxStringChars;
		this.fingerprintSalt = (fingerprintSalt == null) ? "diagnostics" : fingerprintSalt;
	}

	public String sanitize(String value) {
		return sanitize(null, value);
	}

	public String sanitize(String key, String value) {
		if (value == null) return null;
		if (isSensitiveKey(key)) return REDACTED;

		List<String> sanitizedUris = new ArrayList<>();
		Matcher uriMatcher = URI_PATTERN.matcher(value);
		StringBuffer protectedText = new StringBuffer(value.length());
		while (uriMatcher.find()) {
			int index = sanitizedUris.size();
			sanitizedUris.add(sanitizeUri(uriMatcher.group()));
			uriMatcher.appendReplacement(protectedText, Matcher.quoteReplacement(uriMarker(index)));
		}
		uriMatcher.appendTail(protectedText);

		String result = AUTH_HEADER_PATTERN.matcher(protectedText).replaceAll("authorization=" + REDACTED);
		result = COOKIE_PATTERN.matcher(result).replaceAll("cookie=" + REDACTED);
		result = SECRET_ASSIGNMENT_PATTERN.matcher(result).replaceAll(REDACTED);
		result = BEARER_PATTERN.matcher(result).replaceAll(REDACTED);
		result = WINDOWS_PATH_PATTERN.matcher(result).replaceAll("[path]");
		result = UNIX_PATH_PATTERN.matcher(result).replaceAll("[path]");

		for (int i = 0; i < sanitizedUris.size(); i++) {
			result = result.replace(uriMarker(i), sanitizedUris.get(i));
		}
		return truncate(result);
	}

	public Map<String, Object> sanitizeAttributes(Map<String, ?> attributes) {
		if ((attributes == null) || attributes.isEmpty()) return Collections.emptyMap();
		Object value = sanitizeValue(null, attributes, 0, new IdentityHashMap<>(),
				new Budget(MAX_EVENT_VALUE_CHARS));
		if (!(value instanceof Map)) return Collections.emptyMap();
		@SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) value;
		return result;
	}

	/** Fail-closed event schema: unknown fields are dropped and private values are fingerprinted. */
	Map<String, Object> sanitizeEventAttributes(String category, String eventName,
			Map<String, ?> attributes) {
		if ((attributes == null) || attributes.isEmpty()) return Collections.emptyMap();
		Map<String, Object> result = new LinkedHashMap<>();
		Budget budget = new Budget(MAX_EVENT_VALUE_CHARS);
		IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
		for (Map.Entry<String, ?> entry : attributes.entrySet()) {
			String key = sanitizeKey(entry.getKey());
			if (!EVENT_ATTRIBUTE_KEYS.contains(key) || budget.exhausted()) continue;
			Object value = entry.getValue();
			if ("operation".equals(key) && (value != null)) {
				result.put(key, budget.take(sanitizeOperationIdentifier(String.valueOf(value))));
			} else if (FINGERPRINT_VALUE_KEYS.contains(key) && (value != null)) {
				result.put(key, budget.take(fingerprint(category + ':' + eventName + ':' + key,
						String.valueOf(value))));
			} else {
				Object clean = sanitizeEventValue(key, value, seen, budget);
				if (clean != UNSUPPORTED_EVENT_VALUE) result.put(key, clean);
			}
		}
		if (budget.exhausted()) result.put("_truncated", true);
		return Collections.unmodifiableMap(result);
	}

	String sanitizeMetadataIdentifier(String value) {
		return ((value != null) && METADATA_IDENTIFIER.matcher(value).matches()) ?
				value : "invalid";
	}

	String sanitizeCategoryIdentifier(String value) {
		value = sanitizeMetadataIdentifier(value);
		return DiagnosticSchema.isCategory(value) ? value : "invalid";
	}

	String sanitizeEventIdentifier(String value) {
		value = sanitizeMetadataIdentifier(value);
		return DiagnosticSchema.isEvent(value) ? value : "invalid";
	}

	String sanitizeOperationIdentifier(String value) {
		value = sanitizeMetadataIdentifier(value);
		return DiagnosticSchema.isOperation(value) ? value : "invalid";
	}

	String sanitizeCorrelationIdentifier(String value) {
		if (value == null) return null;
		return CORRELATION_IDENTIFIER.matcher(value).matches() ? value : "invalid";
	}

	private Object sanitizeEventValue(String key, Object value,
			IdentityHashMap<Object, Boolean> seen, Budget budget) {
		if (value == null) return null;
		if (value instanceof Throwable) {
			return sanitizeValue(key, sanitizeThrowable((Throwable) value), 0, seen, budget);
		}
		if ((value instanceof CharSequence) || (value instanceof Number) ||
				(value instanceof Boolean) || (value instanceof Enum<?>) ||
				(value instanceof File) || (value instanceof URI) || (value instanceof URL)) {
			return sanitizeValue(key, value, 0, seen, budget);
		}
		// Event payloads are intentionally flat. Arbitrary nested objects are not part of
		// the export schema and could otherwise smuggle private fields through an allowed key.
		return UNSUPPORTED_EVENT_VALUE;
	}

	public Map<String, Object> sanitizeThrowable(Throwable throwable) {
		if (throwable == null) return Collections.emptyMap();
		Map<String, Object> result = new LinkedHashMap<>(2);
		result.put("type", throwable.getClass().getName());
		result.put("stack", boundedStack(throwable));
		return Collections.unmodifiableMap(result);
	}

	private Object sanitizeValue(String key, Object value, int depth,
			IdentityHashMap<Object, Boolean> seen, Budget budget) {
		if (value == null) return null;
		if (isSensitiveKey(key)) return budget.take(REDACTED);
		if (value instanceof CharSequence) {
			String text = value.toString();
			if (looksOpaqueSecret(text)) {
				return budget.take(fingerprint("opaque:" + key, text));
			}
			return budget.take(sanitize(key, text));
		}
		if ((value instanceof Number) || (value instanceof Boolean)) return value;
		if (value instanceof Enum<?>) return ((Enum<?>) value).name();
		if (value instanceof Throwable) return sanitizeValue(key, sanitizeThrowable((Throwable) value),
				depth + 1, seen, budget);
		if ((value instanceof File) || (value instanceof URI) || (value instanceof URL)) {
			return budget.take(sanitize(key, value.toString()));
		}
		if (depth >= MAX_DEPTH) return budget.take("[depth-limited]");
		if (seen.put(value, Boolean.TRUE) != null) return budget.take("[cycle]");

		try {
			if (value instanceof Map<?, ?>) {
				Map<String, Object> result = new LinkedHashMap<>();
				int count = 0;
				for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
					if ((count++ >= MAX_COLLECTION_ITEMS) || budget.exhausted()) {
						result.put("_truncated", true);
						break;
					}
					String entryKey = budget.take(sanitizeKey(String.valueOf(entry.getKey())));
					result.put(entryKey,
							sanitizeValue(entryKey, entry.getValue(), depth + 1, seen, budget));
				}
				return Collections.unmodifiableMap(result);
			}

			if (value instanceof Iterable<?>) {
				List<Object> result = new ArrayList<>();
				for (Object item : (Iterable<?>) value) {
					if ((result.size() >= MAX_COLLECTION_ITEMS) || budget.exhausted()) {
						result.add("[truncated]");
						break;
					}
					result.add(sanitizeValue(key, item, depth + 1, seen, budget));
				}
				return Collections.unmodifiableList(result);
			}

			if (value.getClass().isArray()) {
				List<Object> result = new ArrayList<>();
				int length = Math.min(Array.getLength(value), MAX_COLLECTION_ITEMS);
				for (int i = 0; (i < length) && !budget.exhausted(); i++) {
					result.add(sanitizeValue(key, Array.get(value, i), depth + 1, seen, budget));
				}
				if (Array.getLength(value) > length) result.add("[truncated]");
				return Collections.unmodifiableList(result);
			}
			return budget.take("[object:" + value.getClass().getName() + ']');
		} finally {
			seen.remove(value);
		}
	}

	private String sanitizeUri(String raw) {
		return fingerprint("uri", raw);
	}

	private String fingerprint(String domain, String value) {
		if (value == null) return "[fingerprint:null]";
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(fingerprintSalt.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(domain.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder(26).append("[fingerprint:");
			for (int i = 0; i < 6; i++) out.append(String.format(Locale.US, "%02x", hash[i]));
			return out.append(']').toString();
		} catch (NoSuchAlgorithmException impossible) {
			return "[fingerprint]";
		}
	}

	private static boolean looksOpaqueSecret(String value) {
		if (!OPAQUE_SECRET_PATTERN.matcher(value).matches()) return false;
		boolean[] seen = new boolean[128];
		int distinct = 0;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c < seen.length) && !seen[c]) {
				seen[c] = true;
				if (++distinct >= 8) return true;
			}
		}
		return false;
	}

	private String boundedStack(Throwable throwable) {
		StringBuilder text = new StringBuilder(2048);
		IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
		Throwable current = throwable;
		for (int cause = 0; (current != null) && (cause < MAX_THROWABLE_CAUSES); cause++) {
			if (seen.put(current, Boolean.TRUE) != null) {
				text.append("[cause-cycle]");
				break;
			}
			if (cause > 0) text.append("\nCaused by: ");
			text.append(current.getClass().getName());
			StackTraceElement[] stack = current.getStackTrace();
			int count = Math.min(stack.length, MAX_STACK_FRAMES);
			for (int i = 0; i < count; i++) text.append("\n\tat ").append(stack[i]);
			if (stack.length > count) text.append("\n\t...").append(stack.length - count).append(" more");
			current = current.getCause();
		}
		if (current != null) text.append("\n...[causes-truncated]");
		return truncate(text.toString());
	}

	private String sanitizeKey(String key) {
		return truncate(key.replace('\n', '_').replace('\r', '_'));
	}

	private boolean isSensitiveKey(String key) {
		if (key == null) return false;
		String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		String compact = normalized.replace("_", "");
		return normalized.contains("password") || normalized.contains("passwd") ||
				normalized.contains("cookie") || normalized.contains("credential") ||
				normalized.contains("authorization") || normalized.equals("auth") ||
				normalized.endsWith("_auth") || compact.contains("authheader") ||
				normalized.contains("secret") ||
				normalized.equals("token") || normalized.endsWith("_token") || compact.endsWith("token") ||
				normalized.contains("access_token") || normalized.contains("refresh_token") ||
				normalized.contains("api_key") || normalized.contains("apikey") ||
				compact.contains("rssurl") || compact.contains("xtreamurl") ||
				compact.contains("voicetranscript") || compact.contains("transcript") ||
				compact.contains("recognizedtext") || compact.contains("voicetext") ||
				normalized.equals("utterance");
	}

	private String truncate(String value) {
		if (value.length() <= maxStringChars) return value;
		return value.substring(0, maxStringChars) + "...[truncated]";
	}

	private static String uriMarker(int index) {
		return "__FX_DIAGNOSTIC_URI_" + index + "__";
	}

	private static final class Budget {
		private int remaining;

		Budget(int remaining) {
			this.remaining = remaining;
		}

		boolean exhausted() {
			return remaining <= 0;
		}

		String take(String value) {
			if (value == null) return null;
			if (remaining <= 0) return "[event-truncated]";
			if (value.length() <= remaining) {
				remaining -= value.length();
				return value;
			}
			String result = value.substring(0, remaining) + "...[event-truncated]";
			remaining = 0;
			return result;
		}
	}
}
