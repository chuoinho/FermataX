package me.aap.fermata.addon.tv.stalker;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class StalkerJsonParser {
	String parseToken(InputStream input) throws IOException {
		Object js = unwrap(read(input));
		if (js instanceof Map<?, ?> map) {
			String token = string(map.get("token"));
			if (!blank(token)) return token;
		}
		throw invalid("Portal handshake did not return a token");
	}

	void requireProfile(InputStream input) throws IOException {
		Object root = read(input);
		Object js = unwrap(root);
		if (js == null || Boolean.FALSE.equals(js)) throw invalid(errorMessage(root));
		if ((js instanceof Map<?, ?> map) && rejected(map)) {
			throw invalid(errorMessage(map));
		}
	}

	List<StalkerCategory> parseCategories(InputStream input) throws IOException {
		Object root = read(input);
		List<?> values = array(unwrap(root));
		List<StalkerCategory> result = new ArrayList<>(values.size());
		for (Object value : values) {
			if (!(value instanceof Map<?, ?> map)) continue;
			String id = first(map, "id", "genre_id");
			if (blank(id)) continue;
			result.add(new StalkerCategory(id, first(map, "title", "name")));
		}
		return result;
	}

	List<StalkerChannel> parseChannels(InputStream input) throws IOException {
		Object root = read(input);
		List<?> values = array(unwrap(root));
		List<StalkerChannel> result = new ArrayList<>(values.size());
		for (Object value : values) {
			if (!(value instanceof Map<?, ?> map)) continue;
			String id = first(map, "id", "ch_id", "channel_id");
			String command = first(map, "cmd", "command");
			if (blank(id) || blank(command)) continue;
			result.add(new StalkerChannel(id, first(map, "name", "title"),
					first(map, "logo", "icon"), first(map, "tv_genre_id", "genre_id"),
					command));
		}
		return result;
	}

	StalkerPlaybackLink parseLink(InputStream input, Map<String, String> baseHeaders)
			throws IOException {
		Object root = read(input);
		Object js = unwrap(root);
		String command = null;
		if (js instanceof Map<?, ?> map) command = first(map, "cmd", "url", "link");
		else if (js instanceof String value) command = value;
		if (blank(command)) throw invalid(errorMessage(root));
		return parseCommand(command, baseHeaders);
	}

	static StalkerPlaybackLink parseCommand(String command, Map<String, String> baseHeaders)
			throws IOException {
		String value = command.trim();
		for (String prefix : new String[]{"ffmpeg ", "auto "}) {
			if (value.regionMatches(true, 0, prefix, 0, prefix.length())) {
				value = value.substring(prefix.length()).trim();
			}
		}
		int http = indexOfHttp(value);
		if (http > 0) value = value.substring(http);
		if ((value.length() > 1) && ((value.charAt(0) == '"' && value.endsWith("\"")) ||
				(value.charAt(0) == '\'' && value.endsWith("'")))) {
			value = value.substring(1, value.length() - 1);
		}

		Map<String, String> headers = new LinkedHashMap<>(baseHeaders);
		int pipe = value.indexOf('|');
		if (pipe >= 0) {
			parseHeaders(value.substring(pipe + 1), headers);
			value = value.substring(0, pipe);
		}
		value = value.trim();
		try {
			URI uri = URI.create(value);
			String scheme = uri.getScheme();
			if ((uri.getHost() == null) || (!"http".equalsIgnoreCase(scheme) &&
					!"https".equalsIgnoreCase(scheme))) {
				throw invalid("Stalker portal returned an unsupported stream URL");
			}
			return new StalkerPlaybackLink(uri, headers);
		} catch (IllegalArgumentException ex) {
			throw new IOException("Stalker portal returned an invalid stream URL", ex);
		}
	}

	private static void parseHeaders(String value, Map<String, String> result) {
		for (String part : value.split("&")) {
			int separator = part.indexOf('=');
			if (separator <= 0) continue;
			String name = decode(part.substring(0, separator)).trim();
			String headerValue = decode(part.substring(separator + 1)).trim();
			String normalized = switch (name.toLowerCase(Locale.ROOT)) {
				case "user-agent" -> "User-Agent";
				case "referer" -> "Referer";
				case "origin" -> "Origin";
				case "cookie" -> "Cookie";
				case "authorization" -> "Authorization";
				case "accept" -> "Accept";
				case "accept-language" -> "Accept-Language";
				default -> null;
			};
			if ((normalized != null) && !headerValue.isEmpty()) result.put(normalized, headerValue);
		}
	}

	private static int indexOfHttp(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		int http = lower.indexOf("http://");
		int https = lower.indexOf("https://");
		if (http < 0) return https;
		if (https < 0) return http;
		return Math.min(http, https);
	}

	private static String decode(String value) {
		try {
			return URLDecoder.decode(value, UTF_8.name());
		} catch (Exception ex) {
			return value;
		}
	}

	private static Object unwrap(Object root) throws IOException {
		Object value = root;
		if (value instanceof Map<?, ?> map) {
			if (map.containsKey("js")) value = map.get("js");
			else if (map.containsKey("data")) value = map.get("data");
		}
		if (value instanceof Map<?, ?> map) {
			if (rejected(map)) throw invalid(errorMessage(map));
			if (map.containsKey("data")) value = map.get("data");
			else if (map.containsKey("results")) value = map.get("results");
		}
		return value;
	}

	private static boolean rejected(Map<?, ?> map) {
		String status = first(map, "status", "result");
		return Boolean.FALSE.equals(map.get("success")) || Boolean.FALSE.equals(map.get("valid")) ||
				"error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status) ||
				map.containsKey("not_valid") || map.containsKey("not_valid_token");
	}

	private static List<?> array(Object value) throws IOException {
		if (value instanceof List<?> list) return list;
		if (value == null) return List.of();
		throw invalid("Stalker portal returned an unexpected response");
	}

	private static Object read(InputStream input) throws IOException {
		try (JsonReader reader = new JsonReader(new InputStreamReader(prepare(input), UTF_8))) {
			return readValue(reader);
		} catch (IllegalStateException ex) {
			throw new IOException("Invalid Stalker response: expected JSON", ex);
		}
	}

	private static Object readValue(JsonReader reader) throws IOException {
		return switch (reader.peek()) {
			case BEGIN_OBJECT -> readObject(reader);
			case BEGIN_ARRAY -> readArray(reader);
			case STRING, NUMBER -> reader.nextString();
			case BOOLEAN -> reader.nextBoolean();
			case NULL -> {
				reader.nextNull();
				yield null;
			}
			default -> throw invalid("Stalker portal returned malformed JSON");
		};
	}

	private static Map<String, Object> readObject(JsonReader reader) throws IOException {
		Map<String, Object> result = new LinkedHashMap<>();
		reader.beginObject();
		while (reader.hasNext()) result.put(reader.nextName(), readValue(reader));
		reader.endObject();
		return result;
	}

	private static List<Object> readArray(JsonReader reader) throws IOException {
		List<Object> result = new ArrayList<>();
		reader.beginArray();
		while (reader.hasNext()) result.add(readValue(reader));
		reader.endArray();
		return result;
	}

	private static InputStream prepare(InputStream input) throws IOException {
		PushbackInputStream stream = new PushbackInputStream(input, 1);
		int value;
		do value = stream.read(); while ((value != -1) && Character.isWhitespace(value));
		if (value == -1) throw invalid("Stalker portal returned an empty response");
		if (value == '<') throw invalid("Stalker portal returned HTML instead of JSON");
		if ((value != '{') && (value != '[')) {
			throw invalid("Stalker portal returned an invalid response");
		}
		stream.unread(value);
		return stream;
	}

	private static String first(Map<?, ?> map, String... keys) {
		for (String key : keys) {
			String value = string(map.get(key));
			if (!blank(value)) return value;
		}
		return null;
	}

	private static String string(Object value) {
		return (value == null) ? null : String.valueOf(value);
	}

	private static String errorMessage(Object value) {
		if (value instanceof Map<?, ?> map) {
			if (map.containsKey("not_valid_token")) return "Stalker portal token is no longer valid";
			String message = first(map, "message", "error", "not_valid", "msg");
			if (!blank(message)) return "Stalker portal rejected the request: " + message;
			Object js = map.get("js");
			if ((js != null) && (js != value)) return errorMessage(js);
		}
		return "Stalker portal rejected the request. Check the portal URL and MAC address";
	}

	private static boolean blank(String value) {
		return (value == null) || value.isBlank() || "null".equalsIgnoreCase(value);
	}

	private static IOException invalid(String message) {
		return new IOException(message);
	}
}
