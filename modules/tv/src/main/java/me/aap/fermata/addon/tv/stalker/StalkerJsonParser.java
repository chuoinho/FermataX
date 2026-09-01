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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

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
			int catchupDays = firstInt(map, 0, "tv_archive_duration", "archive_duration");
			if ((catchupDays == 0) && truthy(map.get("tv_archive"))) catchupDays = 1;
			result.add(new StalkerChannel(id, first(map, "name", "title"),
					first(map, "logo", "icon"), first(map, "tv_genre_id", "genre_id"),
					command, catchupDays));
		}
		return result;
	}

	StalkerPage<StalkerVod> parseVodPage(InputStream input) throws IOException {
		return parsePage(input, map -> {
			if (truthy(map.get("is_series"))) return null;
			String id = first(map, "id", "movie_id", "video_id");
			String command = first(map, "cmd", "command", "url");
			if (blank(id) || blank(command)) return null;
			return new StalkerVod(id, first(map, "name", "title"),
					first(map, "screenshot_uri", "stream_icon", "poster", "cover", "logo"),
					first(map, "description", "plot"), first(map, "category_id", "category"),
					command);
		});
	}

	StalkerPage<StalkerSeries> parseSeriesPage(InputStream input) throws IOException {
		return parsePage(input, map -> {
			String id = first(map, "id", "movie_id", "series_id");
			if (blank(id)) return null;
			return new StalkerSeries(id, first(map, "name", "title"),
					first(map, "screenshot_uri", "stream_icon", "poster", "cover", "logo"),
					first(map, "description", "plot"), first(map, "category_id", "category"));
		});
	}

	StalkerPage<StalkerSeason> parseSeasonPage(InputStream input) throws IOException {
		return parsePage(input, new PageMapper<>() {
			private int index;

			@Override
			public StalkerSeason map(Map<?, ?> map) {
				index++;
				String id = first(map, "id", "season_id");
				String name = first(map, "name", "title");
				int number = firstInt(map, 0, "season", "season_number");
				if (number <= 0) number = numberFrom(id, name, index);
				if (blank(id)) id = String.valueOf(number);
				String command = first(map, "cmd", "command", "url");
				if (blank(command)) return null;
				List<StalkerEpisode> episodes = episodes(map.get("series"), id, command,
						first(map, "screenshot_uri", "stream_icon", "poster", "cover", "logo"),
						first(map, "description", "plot"));
				return episodes.isEmpty() ? null : new StalkerSeason(id, number, name,
						first(map, "screenshot_uri", "stream_icon", "poster", "cover", "logo"),
						first(map, "description", "plot"), episodes);
			}
		});
	}

	StalkerPage<StalkerEpgProgram> parseEpgPage(InputStream input, String channelId)
			throws IOException {
		return parsePage(input, map -> {
			String id = first(map, "id", "real_id", "program_id");
			long start = parseTime(first(map, "start_timestamp", "time", "start"));
			long end = parseTime(first(map, "stop_timestamp", "time_to", "end", "stop"));
			StalkerEpgProgram program = new StalkerEpgProgram(id,
					firstOr(map, channelId, "ch_id", "channel_id"), start, end,
					first(map, "name", "title"), first(map, "descr", "description", "plot"),
					first(map, "icon", "logo"), truthy(map.get("mark_archive")) ||
					truthy(map.get("archive")));
			return program.isValid() ? program : null;
		});
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

	private <T> StalkerPage<T> parsePage(InputStream input, PageMapper<T> mapper)
			throws IOException {
		Object value = envelope(read(input));
		int total = 0;
		int pageSize = 0;
		Object data = value;
		if (value instanceof Map<?, ?> map) {
			if (rejected(map)) throw invalid(errorMessage(map));
			total = firstInt(map, 0, "total_items", "total", "count");
			pageSize = firstInt(map, 0, "max_page_items", "page_size", "per_page");
			if (map.containsKey("data")) data = map.get("data");
			else if (map.containsKey("results")) data = map.get("results");
		}
		List<?> values = array(data);
		List<T> items = new ArrayList<>(values.size());
		for (Object raw : values) {
			if (!(raw instanceof Map<?, ?> map)) continue;
			T item = mapper.map(map);
			if (item != null) items.add(item);
		}
		if (pageSize <= 0) pageSize = values.size();
		return new StalkerPage<>(items, total, pageSize);
	}

	private static Object envelope(Object root) throws IOException {
		Object value = root;
		if (value instanceof Map<?, ?> map) {
			if (rejected(map)) throw invalid(errorMessage(map));
			if (map.containsKey("js")) value = map.get("js");
			else if (map.containsKey("data")) value = map.get("data");
		}
		return value;
	}

	private static List<StalkerEpisode> episodes(Object raw, String seasonId, String command,
			String seasonLogo, String seasonDescription) {
		List<?> values;
		if (raw instanceof List<?> list) values = list;
		else if (raw instanceof String text) values = List.of(text.split(","));
		else return List.of();
		List<StalkerEpisode> result = new ArrayList<>(values.size());
		for (int index = 0; index < values.size(); index++) {
			Object value = values.get(index);
			String seriesNumber;
			String id;
			String name = null;
			String episodeCommand = command;
			String logo = seasonLogo;
			String description = seasonDescription;
			if (value instanceof Map<?, ?> map) {
				seriesNumber = first(map, "series", "episode", "episode_number", "number", "id");
				id = first(map, "id", "episode_id");
				name = first(map, "name", "title");
				String override = first(map, "cmd", "command", "url");
				if (!blank(override)) episodeCommand = override;
				String image = first(map, "screenshot_uri", "stream_icon", "poster", "cover", "logo");
				if (!blank(image)) logo = image;
				String plot = first(map, "description", "plot");
				if (!blank(plot)) description = plot;
			} else {
				seriesNumber = string(value);
				id = seriesNumber;
			}
			if (blank(seriesNumber)) seriesNumber = String.valueOf(index + 1);
			if (blank(id)) id = seriesNumber;
			int number = positiveInt(seriesNumber, index + 1);
			result.add(new StalkerEpisode(seasonId + ':' + id, number, name, episodeCommand,
					seriesNumber, logo, description));
		}
		return result;
	}

	private static int numberFrom(String id, String name, int fallback) {
		for (String value : new String[]{id, name}) {
			if (value == null) continue;
			String[] parts = value.split("[^0-9]+");
			for (int i = parts.length - 1; i >= 0; i--) {
				if (!parts[i].isEmpty()) return positiveInt(parts[i], fallback);
			}
		}
		return fallback;
	}

	private static long parseTime(String value) {
		if (blank(value)) return 0;
		try {
			double epoch = Double.parseDouble(value.trim());
			if (epoch > 100000000000L) return (long) epoch;
			if (epoch > 0) return (long) (epoch * 1000L);
		} catch (NumberFormatException ignore) {
		}
		for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
				"yyyy-MM-dd'T'HH:mm:ss'Z'"}) {
			try {
				SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
				format.setTimeZone(TimeZone.getTimeZone("UTC"));
				Date date = format.parse(value.trim());
				if (date != null) return date.getTime();
			} catch (ParseException ignore) {
			}
		}
		return 0;
	}

	private static boolean truthy(Object value) {
		if (value instanceof Boolean bool) return bool;
		String text = string(value);
		return "1".equals(text) || "true".equalsIgnoreCase(text) ||
				"yes".equalsIgnoreCase(text);
	}

	private static int firstInt(Map<?, ?> map, int fallback, String... keys) {
		for (String key : keys) {
			String value = string(map.get(key));
			if (!blank(value)) return positiveInt(value, fallback);
		}
		return fallback;
	}

	private static int positiveInt(String value, int fallback) {
		try {
			int parsed = Integer.parseInt(value.trim());
			return (parsed >= 0) ? parsed : fallback;
		} catch (RuntimeException ignore) {
			return fallback;
		}
	}

	private static String firstOr(Map<?, ?> map, String fallback, String... keys) {
		String value = first(map, keys);
		return blank(value) ? fallback : value;
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

	@FunctionalInterface
	private interface PageMapper<T> {
		T map(Map<?, ?> value);
	}
}
