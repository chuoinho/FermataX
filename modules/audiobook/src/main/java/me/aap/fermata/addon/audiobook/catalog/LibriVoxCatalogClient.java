package me.aap.fermata.addon.audiobook.catalog;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.fermata.addon.audiobook.util.AudiobookIds;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

/** Key-free LibriVox catalog backed by Internet Archive's official LibriVox collection. */
public final class LibriVoxCatalogClient {
	private static final String SEARCH = "https://archive.org/advancedsearch.php";
	private static final String METADATA = "https://archive.org/metadata/";
	private static final String DOWNLOAD = "https://archive.org/download/";
	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 30_000;
	private static final int MAX_JSON_BYTES = 32 * 1024 * 1024;
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");
	private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");

	public FutureSupplier<List<LibriVoxBook>> search(String query, Sort sort, int limit) {
		return App.get().execute(() -> request(buildSearchUrl(query, sort, limit),
				LibriVoxCatalogClient::parseSearch));
	}

	public FutureSupplier<LibriVoxImport> load(String identifier) {
		String safe = safeIdentifier(identifier);
		return App.get().execute(() -> request(METADATA + safe,
				input -> parseMetadata(input, safe, System.currentTimeMillis())));
	}

	private static <T> T request(String url, Parser<T> parser) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
		connection.setRequestProperty("User-Agent", "FermataX/" + BuildConfig.VERSION_NAME);
		try {
			int status = connection.getResponseCode();
			if ((status < 200) || (status >= 300)) {
				throw new IOException("LibriVox catalog returned HTTP " + status);
			}
			long length = connection.getContentLengthLong();
			if (length > MAX_JSON_BYTES) throw new IOException("LibriVox response is too large");
			try (InputStream raw = new LimitedInputStream(connection.getInputStream(),
					MAX_JSON_BYTES); InputStream decoded = decode(connection, raw)) {
				return parser.parse(decoded);
			}
		} finally {
			connection.disconnect();
		}
	}

	static String buildSearchUrl(String query, Sort sort, int limit) {
		String value = (query == null) ? "" : query.trim();
		String q = "collection:librivoxaudio";
		if (!value.isEmpty()) q += " AND (title:(" + escapeQuery(value) + ") OR creator:(" +
				escapeQuery(value) + "))";
		StringBuilder url = new StringBuilder(SEARCH).append("?q=").append(encode(q));
		for (String field : new String[]{"identifier", "title", "creator", "description",
				"language", "downloads"}) {
			url.append("&fl%5B%5D=").append(field);
		}
		url.append("&rows=").append(Math.max(1, Math.min(limit, 50)))
				.append("&page=1&output=json&sort%5B%5D=")
				.append(encode(sort.query));
		return url.toString();
	}

	static List<LibriVoxBook> parseSearch(InputStream input) throws IOException {
		List<LibriVoxBook> result = new ArrayList<>();
		try (JsonReader json = reader(input)) {
			json.beginObject();
			while (json.hasNext()) {
				if (!json.nextName().equals("response")) {
					json.skipValue();
					continue;
				}
				json.beginObject();
				while (json.hasNext()) {
					if (!json.nextName().equals("docs")) {
						json.skipValue();
						continue;
					}
					json.beginArray();
					while (json.hasNext()) {
						LibriVoxBook book = readSearchBook(json);
						if (book != null) result.add(book);
					}
					json.endArray();
				}
				json.endObject();
			}
			json.endObject();
		}
		return result;
	}

	private static LibriVoxBook readSearchBook(JsonReader json) throws IOException {
		String id = "";
		String title = "";
		String author = "";
		String description = "";
		String language = "";
		long downloads = 0;
		json.beginObject();
		while (json.hasNext()) {
			switch (json.nextName()) {
				case "identifier" -> id = nextString(json);
				case "title" -> title = nextString(json);
				case "creator" -> author = nextString(json);
				case "description" -> description = nextString(json);
				case "language" -> language = nextString(json);
				case "downloads" -> downloads = nextLong(json);
				default -> json.skipValue();
			}
		}
		json.endObject();
		if (id.isEmpty() || title.isEmpty()) return null;
		return new LibriVoxBook(id, title, author, plainText(description), language, downloads);
	}

	static LibriVoxImport parseMetadata(InputStream input, String identifier, long now)
			throws IOException {
		Metadata metadata = new Metadata(identifier);
		List<FileEntry> files = new ArrayList<>();
		try (JsonReader json = reader(input)) {
			json.beginObject();
			while (json.hasNext()) {
				switch (json.nextName()) {
					case "metadata" -> readMetadataObject(json, metadata);
					case "files" -> readFiles(json, files);
					default -> json.skipValue();
				}
			}
			json.endObject();
		}
		List<FileEntry> selected = selectFiles(files);
		if (selected.isEmpty()) throw new IOException("This LibriVox book has no playable audio");

		String sourceId = AudiobookIds.source("librivox", "archive.org/librivoxaudio");
		String bookId = AudiobookIds.book("librivox", identifier);
		AudiobookSource source = new AudiobookSource(sourceId, AudiobookSourceType.LIBRIVOX,
				"LibriVox", "https://archive.org/details", null, now, now);
		List<AudiobookChapter> chapters = new ArrayList<>(selected.size());
		long totalDuration = 0;
		for (int index = 0; index < selected.size(); index++) {
			FileEntry file = selected.get(index);
			long duration = Math.max(Math.round(file.seconds * 1000D), 0);
			long bookOffset = totalDuration;
			totalDuration += duration;
			String url = DOWNLOAD + encodePath(identifier) + '/' + encodePath(file.name);
			String title = file.title.isEmpty() ? "Chapter " + (index + 1) : file.title;
			chapters.add(new AudiobookChapter(bookId,
					AudiobookIds.chapter(identifier + '/' + file.name), index, title, url,
					file.name.toLowerCase(Locale.ROOT).endsWith(".m4b") ? "audio/mp4" :
							"audio/mpeg", 0, bookOffset, duration, false, null, 0));
		}
		String title = metadata.title.isEmpty() ? identifier : metadata.title;
		AudiobookBook book = new AudiobookBook(bookId, sourceId, identifier, title,
				metadata.author, "", plainText(metadata.description),
				"https://archive.org/services/img/" + encodePath(identifier), metadata.language,
				totalDuration, null, 0, 0, false, now, now);
		return new LibriVoxImport(source, book, chapters);
	}

	private static void readMetadataObject(JsonReader json, Metadata metadata) throws IOException {
		json.beginObject();
		while (json.hasNext()) {
			switch (json.nextName()) {
				case "title" -> metadata.title = nextString(json);
				case "creator" -> metadata.author = nextString(json);
				case "description" -> metadata.description = nextString(json);
				case "language" -> metadata.language = nextString(json);
				default -> json.skipValue();
			}
		}
		json.endObject();
	}

	private static void readFiles(JsonReader json, List<FileEntry> result) throws IOException {
		json.beginArray();
		while (json.hasNext()) {
			String name = "";
			String title = "";
			String format = "";
			double seconds = 0;
			json.beginObject();
			while (json.hasNext()) {
				switch (json.nextName()) {
					case "name" -> name = nextString(json);
					case "title" -> title = nextString(json);
					case "format" -> format = nextString(json);
					case "length" -> seconds = nextDouble(json);
					default -> json.skipValue();
				}
			}
			json.endObject();
			int rank = audioRank(name, format);
			if (rank >= 0) result.add(new FileEntry(name, title, format, seconds, rank));
		}
		json.endArray();
	}

	private static List<FileEntry> selectFiles(List<FileEntry> files) {
		boolean hasMp3 = files.stream().anyMatch(file -> file.name.toLowerCase(Locale.ROOT)
				.endsWith(".mp3"));
		Map<String, FileEntry> selected = new HashMap<>();
		for (FileEntry file : files) {
			if (hasMp3 && file.name.toLowerCase(Locale.ROOT).endsWith(".m4b")) continue;
			String key = file.title.isEmpty() ? normalizeFileKey(file.name) :
					file.title.toLowerCase(Locale.ROOT);
			FileEntry previous = selected.get(key);
			if ((previous == null) || (file.rank < previous.rank)) selected.put(key, file);
		}
		List<FileEntry> result = new ArrayList<>(selected.values());
		result.sort(Comparator.comparing(file -> file.name, String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	private static int audioRank(String name, String format) {
		String lowerName = name.toLowerCase(Locale.ROOT);
		if (lowerName.endsWith(".m4b")) return 10;
		if (!lowerName.endsWith(".mp3")) return -1;
		String lowerFormat = format.toLowerCase(Locale.ROOT);
		if (lowerFormat.contains("64kbps")) return 0;
		if (lowerFormat.contains("128kbps")) return 1;
		if (lowerFormat.contains("vbr mp3")) return 2;
		return 3;
	}

	private static String normalizeFileKey(String name) {
		return name.toLowerCase(Locale.ROOT)
				.replaceFirst("_(64|128)kb(?=\\.mp3$)", "")
				.replaceFirst("\\.(mp3|m4b)$", "");
	}

	private static JsonReader reader(InputStream input) {
		return new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8));
	}

	private static String nextString(JsonReader json) throws IOException {
		JsonToken token = json.peek();
		if (token == JsonToken.NULL) {
			json.nextNull();
			return "";
		}
		if (token == JsonToken.BEGIN_ARRAY) {
			String value = "";
			json.beginArray();
			while (json.hasNext()) {
				String next = nextString(json);
				if (value.isEmpty()) value = next;
			}
			json.endArray();
			return value;
		}
		if ((token == JsonToken.STRING) || (token == JsonToken.NUMBER)) {
			return json.nextString().trim();
		}
		json.skipValue();
		return "";
	}

	private static long nextLong(JsonReader json) throws IOException {
		try {
			return Long.parseLong(nextString(json));
		} catch (NumberFormatException ignore) {
			return 0;
		}
	}

	private static double nextDouble(JsonReader json) throws IOException {
		try {
			return Double.parseDouble(nextString(json));
		} catch (NumberFormatException ignore) {
			return 0;
		}
	}

	private static InputStream decode(HttpURLConnection connection, InputStream source)
			throws IOException {
		InputStream buffered = new BufferedInputStream(source);
		String encoding = connection.getContentEncoding();
		if ("gzip".equalsIgnoreCase(encoding)) return new GZIPInputStream(buffered);
		if ("deflate".equalsIgnoreCase(encoding)) return new InflaterInputStream(buffered);
		return buffered;
	}

	private static String plainText(String html) {
		if ((html == null) || html.isEmpty()) return "";
		String value = HTML_BREAK.matcher(html).replaceAll("\n");
		value = HTML_TAG.matcher(value).replaceAll("");
		value = value.replace("&amp;", "&").replace("&lt;", "<")
				.replace("&gt;", ">").replace("&quot;", "\"")
				.replace("&#39;", "'").replace("&apos;", "'")
				.replace("&nbsp;", " ");
		Matcher matcher = NUMERIC_ENTITY.matcher(value);
		StringBuffer decoded = new StringBuffer(value.length());
		while (matcher.find()) {
			String number = matcher.group(1);
			try {
				int radix = (number.charAt(0) == 'x') ? 16 : 10;
				int offset = (radix == 16) ? 1 : 0;
				String replacement = new String(Character.toChars(
						Integer.parseInt(number.substring(offset), radix)));
				matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
			} catch (IllegalArgumentException ex) {
				matcher.appendReplacement(decoded, Matcher.quoteReplacement(matcher.group()));
			}
		}
		matcher.appendTail(decoded);
		return decoded.toString().replaceAll("[ \\t]+", " ")
				.replaceAll("\\n{3,}", "\n\n").trim();
	}

	private static String safeIdentifier(String identifier) {
		if ((identifier == null) || !identifier.matches("[A-Za-z0-9._-]{1,200}")) {
			throw new IllegalArgumentException("Invalid LibriVox book identifier");
		}
		return identifier;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String encodePath(String value) {
		return encode(value).replace("+", "%20").replace("%2F", "/");
	}

	private static String escapeQuery(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("(", "\\(").replace(")", "\\)");
	}

	public enum Sort {
		POPULAR("downloads desc"),
		LATEST("date desc"),
		RELEVANCE("downloads desc");

		private final String query;

		Sort(String query) {
			this.query = query;
		}
	}

	private interface Parser<T> {
		T parse(InputStream input) throws IOException;
	}

	private static final class Metadata {
		private final String identifier;
		private String title = "";
		private String author = "";
		private String description = "";
		private String language = "";

		private Metadata(String identifier) {
			this.identifier = identifier;
		}
	}

	private record FileEntry(String name, String title, String format, double seconds, int rank) {
	}

	private static final class LimitedInputStream extends FilterInputStream {
		private long remaining;

		private LimitedInputStream(InputStream input, long limit) {
			super(input);
			remaining = limit;
		}

		@Override
		public int read() throws IOException {
			checkCancelled();
			if (remaining == 0) return overflow();
			int value = super.read();
			if (value != -1) remaining--;
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			if (length == 0) return 0;
			checkCancelled();
			if (remaining == 0) return overflow();
			int read = super.read(buffer, offset, (int) Math.min(length, remaining));
			if (read > 0) remaining -= read;
			return read;
		}

		private int overflow() throws IOException {
			if (super.read() == -1) return -1;
			throw new IOException("LibriVox response is too large");
		}

		private static void checkCancelled() {
			if (Thread.currentThread().isInterrupted()) throw new CancellationException();
		}
	}
}
