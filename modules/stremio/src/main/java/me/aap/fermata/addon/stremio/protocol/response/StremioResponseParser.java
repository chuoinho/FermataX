package me.aap.fermata.addon.stremio.protocol.response;

import static me.aap.fermata.addon.stremio.protocol.response.UnsupportedStreamTarget.Reason.INVALID_TARGET;
import static me.aap.fermata.addon.stremio.protocol.response.UnsupportedStreamTarget.Reason.MISSING_TARGET;
import static me.aap.fermata.addon.stremio.protocol.response.UnsupportedStreamTarget.Reason.MULTIPLE_TARGETS;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import me.aap.fermata.addon.stremio.protocol.ManifestValidator;

/** Converts bounded Stremio JSON responses into immutable values without retaining JSON objects. */
public final class StremioResponseParser {
	public static final int MAX_RESPONSE_BYTES = 1024 * 1024;
	public static final int MAX_ITEMS = 500;
	public static final int MAX_VIDEOS = 1000;
	public static final int MAX_STRING_BYTES = 16 * 1024;
	public static final int MAX_GENRES = 64;
	public static final int MAX_SOURCES = 128;
	public static final int MAX_HEADERS = 32;
	public static final int MAX_HEADER_NAME_BYTES = 128;
	public static final int MAX_HEADER_VALUE_BYTES = 4 * 1024;
	public static final int MAX_NESTING_DEPTH = 64;

	private static final Pattern INFO_HASH =
			Pattern.compile("(?i)(?:[0-9a-f]{40}|[a-z2-7]{32})");
	private static final Pattern HEADER_NAME =
			Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
	private static final Pattern SIMPLE_DURATION = Pattern.compile(
			"(?i)^([0-9]+(?:\\.[0-9]+)?)\\s*(ms|milliseconds?|s|sec(?:onds?)?|m|min(?:utes?)?|h|hours?)$");
	private static final Comparator<StremioVideo> VIDEO_ORDER = Comparator
			.comparing(StremioVideo::season, Comparator.nullsLast(Integer::compareTo))
			.thenComparing(StremioVideo::episode, Comparator.nullsLast(Integer::compareTo))
			.thenComparing(StremioVideo::id);

	private StremioResponseParser() {
	}

	public static CatalogResponse parseCatalog(byte[] body) {
		return parseCatalog(decode(body));
	}

	public static CatalogResponse parseCatalog(String body) {
		var root = root(body);
		var array = requiredArray(root, "metas", "$.metas", MAX_ITEMS);
		var metas = new ArrayList<StremioMeta>(array.length());
		var identities = new LinkedHashSet<String>();
		for (int i = 0; i < array.length(); i++) {
			var field = "$.metas[" + i + "]";
			var meta = parseMetaObject(objectAt(array, i, field), field);
			if (!identities.add(meta.type() + '\u001f' + meta.id())) {
				throw error(field, "Duplicate meta identity: " + meta.type() + "/" + meta.id());
			}
			metas.add(meta);
		}
		return new CatalogResponse(metas);
	}

	public static MetaResponse parseMeta(byte[] body) {
		return parseMeta(decode(body));
	}

	public static MetaResponse parseMeta(String body) {
		var root = root(body);
		return new MetaResponse(parseMetaObject(object(root, "meta", "$.meta"), "$.meta"));
	}

	public static StreamResponse parseStreams(byte[] body) {
		return parseStreams(decode(body));
	}

	public static StreamResponse parseStreams(String body) {
		var root = root(body);
		var array = requiredArray(root, "streams", "$.streams", MAX_ITEMS);
		var streams = new ArrayList<StremioStream>(array.length());
		for (int i = 0; i < array.length(); i++) {
			var field = "$.streams[" + i + "]";
			streams.add(parseStream(objectAt(array, i, field), field));
		}
		return new StreamResponse(streams);
	}

	public static SubtitleResponse parseSubtitles(byte[] body) {
		return parseSubtitles(decode(body));
	}

	public static SubtitleResponse parseSubtitles(String body) {
		var root = root(body);
		var array = requiredArray(root, "subtitles", "$.subtitles", MAX_ITEMS);
		var subtitles = new ArrayList<StremioSubtitle>(array.length());
		var identities = new LinkedHashSet<String>();
		for (int i = 0; i < array.length(); i++) {
			var field = "$.subtitles[" + i + "]";
			var object = objectAt(array, i, field);
			var subtitle = new StremioSubtitle(
					requiredText(object, "id", field + ".id"),
					requiredText(object, "url", field + ".url"),
					requiredText(object, "lang", field + ".lang"));
			if (!identities.add(subtitle.id())) {
				throw error(field, "Duplicate subtitle identity: " + subtitle.id());
			}
			subtitles.add(subtitle);
		}
		return new SubtitleResponse(subtitles);
	}

	public static AddonCatalogResponse parseAddonCatalog(byte[] body) {
		return parseAddonCatalog(decode(body));
	}

	public static AddonCatalogResponse parseAddonCatalog(String body) {
		JSONObject root = root(body);
		JSONArray array = requiredArray(root, "addons", "$.addons", MAX_ITEMS);
		var addons = new ArrayList<StremioAddonCatalogEntry>(array.length());
		var identities = new LinkedHashSet<String>();
		for (int i = 0; i < array.length(); i++) {
			String field = "$.addons[" + i + ']';
			JSONObject addon = objectAt(array, i, field);
			String transportName = requiredText(addon, "transportName",
					field + ".transportName");
			String transportUrl = requiredText(addon, "transportUrl",
					field + ".transportUrl");
			JSONObject manifest = object(addon, "manifest", field + ".manifest");
			var parsed = ManifestValidator.parse(manifest.toString());
			if (!identities.add(parsed.id())) {
				throw error(field, "Duplicate addon identity: " + parsed.id());
			}
			JSONObject flags = present(addon, "flags") ?
					object(addon, "flags", field + ".flags") : null;
			addons.add(new StremioAddonCatalogEntry(transportName, transportUrl, parsed,
					(flags != null) && optionalBoolean(flags, "official", false,
							field + ".flags.official"),
					(flags != null) && optionalBoolean(flags, "protected", false,
							field + ".flags.protected")));
		}
		return new AddonCatalogResponse(addons);
	}

	private static StremioMeta parseMetaObject(JSONObject object, String field) {
		var videos = parseVideos(object, field);
		return new StremioMeta(
				requiredText(object, "id", field + ".id"),
				requiredText(object, "type", field + ".type"),
				requiredText(object, "name", field + ".name"),
				optionalText(object, "poster", field + ".poster"),
				optionalText(object, "posterShape", field + ".posterShape"),
				optionalText(object, "background", field + ".background"),
				optionalText(object, "logo", field + ".logo"),
				optionalText(object, "description", field + ".description"),
				optionalText(object, "releaseInfo", field + ".releaseInfo"),
				optionalText(object, "imdbRating", field + ".imdbRating"),
				optionalDuration(object, "runtime", field + ".runtime"),
				optionalStringArray(object, "genres", field + ".genres", MAX_GENRES),
				optionalText(object, "language", field + ".language"),
				videos);
	}

	private static List<StremioVideo> parseVideos(JSONObject meta, String field) {
		if (!present(meta, "videos")) return List.of();
		var array = array(meta, "videos", field + ".videos", MAX_VIDEOS);
		var videos = new ArrayList<StremioVideo>(array.length());
		var ids = new LinkedHashSet<String>();
		for (int i = 0; i < array.length(); i++) {
			var videoField = field + ".videos[" + i + "]";
			var video = objectAt(array, i, videoField);
			var item = new StremioVideo(
					requiredText(video, "id", videoField + ".id"),
					requiredTextAlias(video, "title", "name", videoField + ".title"),
					optionalNonNegativeInt(video, "season", videoField + ".season"),
					optionalNonNegativeInt(video, "episode", videoField + ".episode"),
					optionalText(video, "released", videoField + ".released"),
					optionalText(video, "thumbnail", videoField + ".thumbnail"),
					optionalText(video, "overview", videoField + ".overview"),
					optionalDuration(video, "duration", videoField + ".duration"));
			if (!ids.add(item.id())) throw error(videoField, "Duplicate video identity: " + item.id());
			videos.add(item);
		}
		videos.sort(VIDEO_ORDER);
		return List.copyOf(videos);
	}

	private static StremioStream parseStream(JSONObject object, String field) {
		return new StremioStream(
				optionalText(object, "name", field + ".name"),
				optionalText(object, "title", field + ".title"),
				optionalText(object, "description", field + ".description"),
				parseTarget(object, field),
				parseEmbeddedSubtitles(object, field),
				parseBehaviorHints(object, field));
	}

	private static StreamTarget parseTarget(JSONObject object, String field) {
		var keys = List.of("url", "ytId", "externalUrl", "infoHash", "nzbUrl",
				"rarUrls", "zipUrls", "7zipUrls", "tgzUrls", "tarUrls");
		int count = 0;
		String selected = null;
		for (var key : keys) {
			if (present(object, key)) {
				count++;
				selected = key;
			}
		}
		if (count == 0) return new UnsupportedStreamTarget(MISSING_TARGET);
		if (count > 1) return new UnsupportedStreamTarget(MULTIPLE_TARGETS);

		return switch (selected) {
			case "url" -> new DirectStreamTarget(requiredText(object, selected, field + ".url"));
			case "ytId" -> new YoutubeStreamTarget(requiredText(object, selected, field + ".ytId"));
			case "externalUrl" -> new ExternalStreamTarget(
					requiredText(object, selected, field + ".externalUrl"));
			case "infoHash" -> parseInfoHashTarget(object, field);
			case "nzbUrl" -> new NzbStreamTarget(
					requiredText(object, selected, field + ".nzbUrl"),
					optionalStringArray(object, "servers", field + ".servers", MAX_SOURCES),
					optionalNonNegativeInt(object, "fileIdx", field + ".fileIdx"),
					optionalText(object, "fileMustInclude", field + ".fileMustInclude"));
			case "rarUrls" -> parseArchiveTarget(object, selected, field,
					ArchiveStreamTarget.Kind.RAR);
			case "zipUrls" -> parseArchiveTarget(object, selected, field,
					ArchiveStreamTarget.Kind.ZIP);
			case "7zipUrls" -> parseArchiveTarget(object, selected, field,
					ArchiveStreamTarget.Kind.SEVEN_ZIP);
			case "tgzUrls" -> parseArchiveTarget(object, selected, field,
					ArchiveStreamTarget.Kind.TGZ);
			case "tarUrls" -> parseArchiveTarget(object, selected, field,
					ArchiveStreamTarget.Kind.TAR);
			default -> new UnsupportedStreamTarget(MISSING_TARGET);
		};
	}

	private static ArchiveStreamTarget parseArchiveTarget(JSONObject object, String key,
			String field, ArchiveStreamTarget.Kind kind) {
		String sourcesField = field + '.' + key;
		JSONArray array = array(object, key, sourcesField, MAX_SOURCES);
		if (array.length() == 0) throw error(sourcesField, "Source list cannot be empty");
		var sources = new ArrayList<StreamSource>(array.length());
		for (int i = 0; i < array.length(); i++) {
			String sourceField = sourcesField + '[' + i + ']';
			JSONObject source = objectAt(array, i, sourceField);
			sources.add(new StreamSource(
					requiredText(source, "url", sourceField + ".url"),
					optionalNonNegativeLong(source, "bytes", sourceField + ".bytes")));
		}
		return new ArchiveStreamTarget(kind, sources,
				optionalNonNegativeInt(object, "fileIdx", field + ".fileIdx"),
				optionalText(object, "fileMustInclude", field + ".fileMustInclude"));
	}

	private static StreamTarget parseInfoHashTarget(JSONObject object, String field) {
		var hash = requiredText(object, "infoHash", field + ".infoHash");
		if (!INFO_HASH.matcher(hash).matches()) return new UnsupportedStreamTarget(INVALID_TARGET);
		return new InfoHashStreamTarget(hash,
				optionalNonNegativeInt(object, "fileIdx", field + ".fileIdx"),
				optionalStringArray(object, "sources", field + ".sources", MAX_SOURCES));
	}

	private static StreamBehaviorHints parseBehaviorHints(JSONObject stream, String field) {
		if (!present(stream, "behaviorHints")) return StreamBehaviorHints.EMPTY;
		var hintsField = field + ".behaviorHints";
		var hints = object(stream, "behaviorHints", hintsField);
		var proxy = ProxyHeaders.EMPTY;
		if (present(hints, "proxyHeaders")) {
			var proxyField = hintsField + ".proxyHeaders";
			var proxyObject = object(hints, "proxyHeaders", proxyField);
			proxy = new ProxyHeaders(
					parseHeaders(proxyObject, "request", proxyField + ".request"),
					parseHeaders(proxyObject, "response", proxyField + ".response"));
		}
		return new StreamBehaviorHints(
				optionalBoolean(hints, "notWebReady", false, hintsField + ".notWebReady"),
				optionalText(hints, "bingeGroup", hintsField + ".bingeGroup"),
				optionalText(hints, "videoHash", hintsField + ".videoHash"),
				optionalNonNegativeLong(hints, "videoSize", hintsField + ".videoSize"),
				optionalText(hints, "filename", hintsField + ".filename"),
				optionalStringArray(hints, "countryWhitelist",
						hintsField + ".countryWhitelist", MAX_GENRES),
				proxy);
	}

	private static List<StremioSubtitle> parseEmbeddedSubtitles(
			JSONObject stream, String field) {
		if (!present(stream, "subtitles")) return List.of();
		String subtitlesField = field + ".subtitles";
		JSONArray array = array(stream, "subtitles", subtitlesField, MAX_ITEMS);
		var subtitles = new ArrayList<StremioSubtitle>(array.length());
		var identities = new LinkedHashSet<String>();
		for (int i = 0; i < array.length(); i++) {
			String subtitleField = subtitlesField + '[' + i + ']';
			JSONObject subtitle = objectAt(array, i, subtitleField);
			StremioSubtitle value = new StremioSubtitle(
					requiredText(subtitle, "id", subtitleField + ".id"),
					requiredText(subtitle, "url", subtitleField + ".url"),
					requiredText(subtitle, "lang", subtitleField + ".lang"));
			if (!identities.add(value.id())) {
				throw error(subtitleField, "Duplicate embedded subtitle identity: " + value.id());
			}
			subtitles.add(value);
		}
		return List.copyOf(subtitles);
	}

	private static Map<String, String> parseHeaders(JSONObject proxy, String key, String field) {
		if (!present(proxy, key)) return Map.of();
		var object = object(proxy, key, field);
		if (object.length() > MAX_HEADERS) throw error(field, "Too many headers; limit is " + MAX_HEADERS);
		var headers = new LinkedHashMap<String, String>();
		var normalizedNames = new LinkedHashSet<String>();
		var keys = object.keys();
		while (keys.hasNext()) {
			var name = keys.next();
			bounded(name, field + ".<name>", MAX_HEADER_NAME_BYTES);
			if (!HEADER_NAME.matcher(name).matches()) {
				throw error(field + ".<name>", "Invalid HTTP header name");
			}
			var normalized = name.toLowerCase(Locale.ROOT);
			if (!normalizedNames.add(normalized)) {
				throw error(field, "Case-insensitive header collision: " + name);
			}
			var value = object.opt(name);
			if (!(value instanceof String text)) throw error(field + "." + name, "Expected a string");
			text = boundedText(text, field + "." + name, MAX_HEADER_VALUE_BYTES);
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				if ((c == '\r') || (c == '\n') || (c == 0) || ((c < 0x20) && (c != '\t'))) {
					throw error(field + "." + name, "Invalid control character in header value");
				}
			}
			headers.put(name, text);
		}
		return headers;
	}

	private static StremioDuration optionalDuration(JSONObject object, String key, String field) {
		if (!present(object, key)) return null;
		var value = object.opt(key);
		if (value instanceof Number number) {
			var millis = exactNonNegativeLong(number, field);
			return new StremioDuration(number.toString(), millis);
		}
		if (!(value instanceof String text)) throw error(field, "Expected a string or integer");
		text = boundedText(text, field, MAX_STRING_BYTES);
		return new StremioDuration(text, parseDurationMillis(text));
	}

	private static long parseDurationMillis(String text) {
		var value = text.trim();
		var matcher = SIMPLE_DURATION.matcher(value);
		if (matcher.matches()) {
			try {
				var amount = Double.parseDouble(matcher.group(1));
				var unit = matcher.group(2).toLowerCase(Locale.ROOT);
				var multiplier = unit.startsWith("h") ? 3_600_000D :
						unit.startsWith("m") && !unit.equals("ms") && !unit.startsWith("milli") ? 60_000D :
						unit.startsWith("s") ? 1_000D : 1D;
				var result = amount * multiplier;
				if ((result >= 0D) && (result <= Long.MAX_VALUE)) return Math.round(result);
			} catch (NumberFormatException ignored) {
				// Preserve an unknown provider format without guessing its playback duration.
			}
		}
		var parts = value.split(":", -1);
		if ((parts.length == 2) || (parts.length == 3)) {
			try {
				long hours = parts.length == 3 ? Long.parseLong(parts[0]) : 0L;
				long minutes = Long.parseLong(parts[parts.length - 2]);
				long seconds = Long.parseLong(parts[parts.length - 1]);
				if ((hours >= 0) && (minutes >= 0) && (minutes < 60) && (seconds >= 0) && (seconds < 60)) {
					var totalMinutes = Math.addExact(Math.multiplyExact(hours, 60L), minutes);
					return Math.addExact(Math.multiplyExact(totalMinutes, 60_000L),
							seconds * 1_000L);
				}
			} catch (NumberFormatException | ArithmeticException ignored) {
				// Preserve an unknown provider format without guessing its playback duration.
			}
		}
		return StremioDuration.UNKNOWN;
	}

	private static JSONObject root(String body) {
		Objects.requireNonNull(body, "body");
		bounded(body, "$", MAX_RESPONSE_BYTES);
		var trimmed = body.trim();
		if (trimmed.isEmpty()) throw error("$", "Response is empty");
		if (trimmed.startsWith("<")) {
			throw error("$", "Expected JSON but received HTML-like content");
		}
		validateNesting(trimmed);
		try {
			return new JSONObject(trimmed);
		} catch (JSONException e) {
			throw new StremioResponseException("$", "Malformed response JSON", e);
		}
	}

	private static String decode(byte[] body) {
		Objects.requireNonNull(body, "body");
		if (body.length > MAX_RESPONSE_BYTES) {
			throw error("$", "Response exceeds " + MAX_RESPONSE_BYTES + " bytes");
		}
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(body)).toString();
		} catch (CharacterCodingException e) {
			throw new StremioResponseException("$", "Response is not valid UTF-8", e);
		}
	}

	private static void validateNesting(String json) {
		int depth = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);
			if (quoted) {
				if (escaped) escaped = false;
				else if (c == '\\') escaped = true;
				else if (c == '"') quoted = false;
				continue;
			}
			if (c == '"') quoted = true;
			else if ((c == '{') || (c == '[')) {
				if (++depth > MAX_NESTING_DEPTH) {
					throw error("$", "JSON nesting exceeds " + MAX_NESTING_DEPTH);
				}
			} else if (((c == '}') || (c == ']')) && (depth > 0)) {
				depth--;
			}
		}
	}

	private static JSONArray requiredArray(
			JSONObject object, String key, String field, int maxItems) {
		if (!present(object, key)) throw error(field, "Required field is missing");
		return array(object, key, field, maxItems);
	}

	private static JSONArray array(JSONObject object, String key, String field, int maxItems) {
		var value = object.opt(key);
		if (!(value instanceof JSONArray array)) throw error(field, "Expected an array");
		if (array.length() > maxItems) throw error(field, "Too many items; limit is " + maxItems);
		return array;
	}

	private static JSONObject object(JSONObject owner, String key, String field) {
		var value = owner.opt(key);
		if (!(value instanceof JSONObject object)) throw error(field, "Expected an object");
		return object;
	}

	private static JSONObject objectAt(JSONArray array, int index, String field) {
		var value = array.opt(index);
		if (!(value instanceof JSONObject object)) throw error(field, "Expected an object");
		return object;
	}

	private static List<String> optionalStringArray(
			JSONObject object, String key, String field, int maxItems) {
		if (!present(object, key)) return List.of();
		var array = array(object, key, field, maxItems);
		var result = new ArrayList<String>(array.length());
		for (int i = 0; i < array.length(); i++) {
			var value = array.opt(i);
			if (!(value instanceof String text)) throw error(field + "[" + i + "]", "Expected a string");
			result.add(boundedText(text, field + "[" + i + "]", MAX_STRING_BYTES));
		}
		return List.copyOf(result);
	}

	private static String requiredText(JSONObject object, String key, String field) {
		if (!present(object, key)) throw error(field, "Required field is missing");
		var value = object.opt(key);
		if (!(value instanceof String text)) throw error(field, "Expected a string");
		return boundedText(text, field, MAX_STRING_BYTES);
	}

	private static String requiredTextAlias(JSONObject object, String key, String alias,
			String field) {
		if (present(object, key)) return requiredText(object, key, field);
		if (present(object, alias)) return requiredText(object, alias, field);
		throw error(field, "Required field is missing");
	}

	private static String optionalText(JSONObject object, String key, String field) {
		if (!present(object, key)) return null;
		var value = object.opt(key);
		if (!(value instanceof String text)) throw error(field, "Expected a string");
		bounded(text, field, MAX_STRING_BYTES);
		return text.isBlank() ? null : text;
	}

	private static String boundedText(String value, String field, int maxBytes) {
		bounded(value, field, maxBytes);
		if (value.isBlank()) throw error(field, "String cannot be blank");
		return value;
	}

	private static void bounded(String value, String field, int maxBytes) {
		if ((value.length() > maxBytes) || (value.getBytes(StandardCharsets.UTF_8).length > maxBytes)) {
			throw error(field, "String exceeds " + maxBytes + " UTF-8 bytes");
		}
	}

	private static Integer optionalNonNegativeInt(JSONObject object, String key, String field) {
		if (!present(object, key)) return null;
		var value = object.opt(key);
		if (!(value instanceof Number number)) throw error(field, "Expected an integer");
		long result = exactNonNegativeLong(number, field);
		if (result > Integer.MAX_VALUE) throw error(field, "Integer is too large");
		return (int) result;
	}

	private static Long optionalNonNegativeLong(JSONObject object, String key, String field) {
		if (!present(object, key)) return null;
		var value = object.opt(key);
		if (!(value instanceof Number number)) throw error(field, "Expected an integer");
		return exactNonNegativeLong(number, field);
	}

	private static long exactNonNegativeLong(Number number, String field) {
		var value = number.longValue();
		if ((value < 0L) || !Double.isFinite(number.doubleValue()) ||
				(number.doubleValue() != (double) value)) {
			throw error(field, "Expected a non-negative integer");
		}
		return value;
	}

	private static boolean optionalBoolean(
			JSONObject object, String key, boolean fallback, String field) {
		if (!present(object, key)) return fallback;
		var value = object.opt(key);
		if (!(value instanceof Boolean bool)) throw error(field, "Expected a boolean");
		return bool;
	}

	private static boolean present(JSONObject object, String key) {
		return object.has(key) && !object.isNull(key);
	}

	private static StremioResponseException error(String field, String message) {
		return new StremioResponseException(field, message);
	}
}
