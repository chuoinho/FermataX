package me.aap.fermata.addon.audiobook.remote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
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

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.fermata.addon.audiobook.security.AudiobookCredential;
import me.aap.fermata.addon.audiobook.security.AudiobookCredentialStore;
import me.aap.fermata.addon.audiobook.util.AudiobookIds;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

/** Audiobookshelf v2 client with encrypted access/refresh token persistence. */
public final class AudiobookshelfClient {
	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 30_000;
	private static final int MAX_JSON_BYTES = 32 * 1024 * 1024;
	private static final int PAGE_SIZE = 200;
	private static final int MAX_PAGES = 20;
	private final AudiobookCredentialStore credentials;

	public AudiobookshelfClient(AudiobookCredentialStore credentials) {
		this.credentials = credentials;
	}

	public FutureSupplier<AudiobookSourceSnapshot> connect(String endpoint, String username,
			String password) {
		return App.get().execute(() -> {
			if (!credentials.isAvailable()) {
				throw new IOException("Encrypted credential storage is unavailable");
			}
			String normalized = normalizeEndpoint(endpoint);
			AudiobookCredential credential = login(normalized, username, password);
			String identity = normalized + '\n' + credential.username();
			String reference = AudiobookIds.source("abs-credential", identity);
			if (!credentials.save(reference, credential)) {
				throw new IOException("Could not save encrypted Audiobookshelf credentials");
			}
			AudiobookSource source = source(normalized, credential.username(), reference,
					System.currentTimeMillis());
			try {
				return snapshot(source, credential);
			} catch (Throwable error) {
				credentials.remove(reference);
				throw error;
			}
		});
	}

	public FutureSupplier<AudiobookSourceSnapshot> refresh(AudiobookSource source) {
		return App.get().execute(() -> snapshot(source, requireCredential(source)));
	}

	public FutureSupplier<AudiobookshelfBookDetails> loadBook(AudiobookSource source,
			AudiobookBook summary) {
		return App.get().execute(() -> {
			AudiobookCredential credential = requireCredential(source);
			String remoteId = summary.getRemoteId();
			if (remoteId == null) throw new IOException("Audiobookshelf book ID is missing");
			String json = authenticated(source, credential, "GET",
					"/api/items/" + path(remoteId) + "?expanded=1&include=progress", null);
			return parseDetails(json, source, summary, System.currentTimeMillis());
		});
	}

	public FutureSupplier<Void> updateProgress(AudiobookSource source, AudiobookBook book,
			AudiobookChapter chapter, long chapterPositionMs, boolean finished) {
		return App.get().execute(() -> {
			String remoteId = book.getRemoteId();
			if (remoteId == null) return null;
			AudiobookCredential credential = requireCredential(source);
			long globalMs = Math.max(chapter.getBookOffsetMs() + chapterPositionMs, 0);
			JSONObject body = new JSONObject();
			body.put("currentTime", globalMs / 1000D);
			body.put("duration", Math.max(book.getDurationMs(), 0) / 1000D);
			body.put("isFinished", finished);
			body.put("lastUpdate", System.currentTimeMillis());
			authenticated(source, credential, "PATCH",
					"/api/me/progress/" + path(remoteId), body.toString());
			return null;
		});
	}

	public Map<String, String> requestHeaders(AudiobookSource source) {
		AudiobookCredential credential = credentials.load(source.getCredentialRef());
		return (credential == null) ? Map.of() :
				Map.of("Authorization", credential.authorization());
	}

	public void removeCredentials(AudiobookSource source) {
		credentials.remove(source.getCredentialRef());
	}

	private AudiobookSourceSnapshot snapshot(AudiobookSource source,
			AudiobookCredential credential) throws IOException, JSONException {
		String librariesJson = authenticated(source, credential, "GET", "/api/libraries", null);
		List<Library> libraries = parseLibraries(librariesJson);
		List<AudiobookBook> books = new ArrayList<>();
		for (Library library : libraries) {
			if (!library.mediaType.equalsIgnoreCase("book")) continue;
			for (int page = 0; page < MAX_PAGES; page++) {
				String path = "/api/libraries/" + path(library.id) + "/items?limit=" + PAGE_SIZE +
						"&page=" + page + "&sort=media.metadata.title&desc=0&minified=1";
				BookPage parsed = parseBookPage(authenticated(source, credential, "GET", path,
						null), source, System.currentTimeMillis());
				books.addAll(parsed.books);
				if (((page + 1) * PAGE_SIZE >= parsed.total) || parsed.books.isEmpty()) break;
			}
		}
		return new AudiobookSourceSnapshot(source, books);
	}

	private AudiobookCredential requireCredential(AudiobookSource source) throws IOException {
		AudiobookCredential credential = credentials.load(source.getCredentialRef());
		if (credential == null) throw new IOException("Audiobookshelf credentials are unavailable");
		return credential;
	}

	private AudiobookCredential login(String endpoint, String username, String password)
			throws IOException, JSONException {
		JSONObject body = new JSONObject();
		body.put("username", username == null ? "" : username.trim());
		body.put("password", password == null ? "" : password);
		Map<String, String> headers = Map.of("X-Return-Tokens", "true");
		String response = request(endpoint, "POST", "/login", body.toString(), null, headers);
		return parseLogin(response, endpoint, username);
	}

	private String authenticated(AudiobookSource source, AudiobookCredential credential,
			String method, String path, String body) throws IOException, JSONException {
		try {
			return request(source.getEndpoint(), method, path, body, credential, Map.of());
		} catch (HttpStatusException error) {
			if ((error.status != 401) || (credential.refreshToken() == null)) throw error;
			AudiobookCredential refreshed = refreshToken(credential);
			if (!credentials.save(source.getCredentialRef(), refreshed)) {
				throw new IOException("Could not update encrypted Audiobookshelf credentials");
			}
			return request(source.getEndpoint(), method, path, body, refreshed, Map.of());
		}
	}

	private AudiobookCredential refreshToken(AudiobookCredential credential)
			throws IOException, JSONException {
		String response = request(credential.endpoint(), "POST", "/auth/refresh", null, null,
				Map.of("X-Refresh-Token", credential.refreshToken(), "X-Return-Tokens", "true"));
		return parseLogin(response, credential.endpoint(), credential.username());
	}

	private static String request(String endpoint, String method, String path, String body,
			AudiobookCredential credential, Map<String, String> extraHeaders) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(endpoint + path).openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestMethod(method);
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
		connection.setRequestProperty("User-Agent", "FermataX/" + BuildConfig.VERSION_NAME);
		if (credential != null) {
			connection.setRequestProperty("Authorization", credential.authorization());
		}
		for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
			connection.setRequestProperty(header.getKey(), header.getValue());
		}
		if (body != null) {
			byte[] data = body.getBytes(StandardCharsets.UTF_8);
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			connection.setFixedLengthStreamingMode(data.length);
			try (BufferedOutputStream output = new BufferedOutputStream(
					connection.getOutputStream())) {
				output.write(data);
			}
		}
		try {
			int status = connection.getResponseCode();
			if ((status < 200) || (status >= 300)) throw new HttpStatusException(status);
			if ((status == 204) || (connection.getContentLengthLong() == 0)) return "{}";
			long length = connection.getContentLengthLong();
			if (length > MAX_JSON_BYTES) throw new IOException("Audiobookshelf response is too large");
			try (InputStream decoded = decode(connection, connection.getInputStream())) {
				return readBounded(decoded);
			}
		} finally {
			connection.disconnect();
		}
	}

	static AudiobookCredential parseLogin(String json, String endpoint, String fallbackUsername)
			throws JSONException, IOException {
		JSONObject user;
		try {
			user = new JSONObject(json).getJSONObject("user");
		} catch (JSONException error) {
			throw new IOException("Audiobookshelf login response has no user", error);
		}
		String access = user.optString("accessToken", "");
		if (access.isEmpty()) access = user.optString("token", "");
		if (access.isEmpty()) throw new IOException("Audiobookshelf login returned no token");
		String username = user.optString("username", fallbackUsername == null ? "" :
				fallbackUsername.trim());
		String refresh = user.optString("refreshToken", "");
		return new AudiobookCredential(endpoint, username, access, refresh);
	}

	static List<Library> parseLibraries(String json) throws JSONException {
		JSONArray array = new JSONArray(json);
		List<Library> result = new ArrayList<>(array.length());
		for (int index = 0; index < array.length(); index++) {
			JSONObject item;
			try {
				item = array.getJSONObject(index);
			} catch (JSONException ignore) {
				continue;
			}
			String id = item.optString("id", "");
			if (!id.isEmpty()) result.add(new Library(id, item.optString("name", ""),
					item.optString("mediaType", "")));
		}
		return result;
	}

	static BookPage parseBookPage(String json, AudiobookSource source, long now)
			throws JSONException {
		JSONObject root = new JSONObject(json);
		JSONArray results = root.optJSONArray("results");
		List<AudiobookBook> books = new ArrayList<>((results == null) ? 0 : results.length());
		if (results != null) for (int index = 0; index < results.length(); index++) {
			JSONObject item;
			try {
				item = results.getJSONObject(index);
			} catch (JSONException ignore) {
				continue;
			}
			String remoteId = item.optString("id", "");
			JSONObject media;
			try {
				media = item.getJSONObject("media");
			} catch (JSONException ignore) {
				continue;
			}
			JSONObject metadata = media.optJSONObject("metadata");
			String title = string(metadata, "title");
			if (remoteId.isEmpty() || title.isEmpty()) continue;
			long duration = Math.max(Math.round(number(media, "duration") * 1000D), 0);
			books.add(new AudiobookBook(AudiobookIds.book("audiobookshelf",
					source.getId() + '/' + remoteId), source.getId(), remoteId, title,
					string(metadata, "authorName"), string(metadata, "narratorName"),
					string(metadata, "descriptionPlain"), "", string(metadata, "language"),
					duration, null, 0, 0, false, item.optLong("addedAt", now),
					item.optLong("updatedAt", now)));
		}
		return new BookPage(books, root.optInt("total", books.size()));
	}

	static AudiobookshelfBookDetails parseDetails(String json, AudiobookSource source,
			AudiobookBook summary, long now) throws JSONException, IOException {
		JSONObject item = new JSONObject(json);
		JSONObject media;
		try {
			media = item.getJSONObject("media");
		} catch (JSONException error) {
			throw new IOException("Audiobookshelf book has no media", error);
		}
		JSONObject metadata = media.optJSONObject("metadata");
		List<Track> tracks = readTracks(media.optJSONArray("tracks"));
		if (tracks.isEmpty()) throw new IOException("Audiobookshelf book has no audio tracks");
		List<RemoteChapter> remoteChapters = readChapters(media.optJSONArray("chapters"));
		long durationMs = Math.max(Math.round(number(media, "duration") * 1000D), 0);
		if (durationMs == 0) {
			for (Track track : tracks) durationMs = Math.max(durationMs,
					track.startMs + track.durationMs);
		}
		List<AudiobookChapter> chapters = buildChapters(source, summary, tracks,
				remoteChapters, durationMs);
		JSONObject progress = item.optJSONObject("userMediaProgress");
		long remoteUpdated = (progress == null) ? 0 : progress.optLong("lastUpdate", 0);
		long currentMs = (progress == null) ? 0 :
				Math.max(Math.round(progress.optDouble("currentTime", 0) * 1000D), 0);
		boolean finished = (progress != null) && progress.optBoolean("isFinished", false);
		AudiobookChapter progressChapter = chapterAt(chapters, currentMs);
		String progressId = (progressChapter == null) ? null : progressChapter.getId();
		long chapterPosition = (progressChapter == null) ? 0 :
				Math.max(currentMs - progressChapter.getBookOffsetMs(), 0);
		String title = value(string(metadata, "title"), summary.getTitle());
		AudiobookBook book = new AudiobookBook(summary.getId(), source.getId(),
				summary.getRemoteId(), title, value(string(metadata, "authorName"),
				summary.getAuthor()), value(string(metadata, "narratorName"),
				summary.getNarrator()), value(string(metadata, "descriptionPlain"),
				summary.getDescription()), "", value(string(metadata, "language"),
				summary.getLanguage()), durationMs, progressId, chapterPosition, remoteUpdated,
				finished, summary.getAddedMs(), item.optLong("updatedAt", now));
		return new AudiobookshelfBookDetails(source, book, chapters, remoteUpdated);
	}

	private static List<AudiobookChapter> buildChapters(AudiobookSource source,
			AudiobookBook book, List<Track> tracks, List<RemoteChapter> marks, long durationMs) {
		List<AudiobookChapter> result = new ArrayList<>();
		if (marks.isEmpty()) {
			for (int index = 0; index < tracks.size(); index++) {
				Track track = tracks.get(index);
				result.add(chapter(source, book, index, track.title, track, 0,
						track.startMs, track.durationMs, false));
			}
			return result;
		}
		for (int index = 0; index < marks.size(); index++) {
			RemoteChapter mark = marks.get(index);
			long end = mark.endMs;
			if (end <= mark.startMs) {
				end = (index + 1 < marks.size()) ? marks.get(index + 1).startMs : durationMs;
			}
			Track track = trackAt(tracks, mark.startMs);
			if (track == null) continue;
			long mediaOffset = Math.max(mark.startMs - track.startMs, 0);
			long maxDuration = Math.max(track.durationMs - mediaOffset, 0);
			long chapterDuration = Math.min(Math.max(end - mark.startMs, 0), maxDuration);
			result.add(chapter(source, book, result.size(), mark.title, track, mediaOffset,
					mark.startMs, chapterDuration, true));
		}
		return result;
	}

	private static AudiobookChapter chapter(AudiobookSource source, AudiobookBook book, int index,
			String title, Track track, long mediaOffset, long bookOffset, long duration,
			boolean segment) {
		String remoteId = book.getRemoteId();
		String key = remoteId + "/" + index + '/' + bookOffset;
		String url = source.getEndpoint() + track.contentUrl;
		return new AudiobookChapter(book.getId(), AudiobookIds.chapter(key), index,
				title.isEmpty() ? "Chapter " + (index + 1) : title, url,
				track.mimeType.isEmpty() ? "audio/mpeg" : track.mimeType, mediaOffset,
				bookOffset, duration, segment, null, 0);
	}

	private static List<Track> readTracks(JSONArray array) {
		List<Track> result = new ArrayList<>((array == null) ? 0 : array.length());
		if (array != null) for (int index = 0; index < array.length(); index++) {
			JSONObject track;
			try {
				track = array.getJSONObject(index);
			} catch (JSONException ignore) {
				continue;
			}
			String content = track.optString("contentUrl", "");
			if (content.isEmpty()) continue;
			result.add(new Track(track.optInt("index", index + 1),
					Math.max(Math.round(track.optDouble("startOffset", 0) * 1000D), 0),
					Math.max(Math.round(track.optDouble("duration", 0) * 1000D), 0),
					track.optString("title", ""), content,
					track.optString("mimeType", "")));
		}
		result.sort(Comparator.comparingInt(Track::index));
		return result;
	}

	private static List<RemoteChapter> readChapters(JSONArray array) {
		List<RemoteChapter> result = new ArrayList<>((array == null) ? 0 : array.length());
		if (array != null) for (int index = 0; index < array.length(); index++) {
			JSONObject chapter;
			try {
				chapter = array.getJSONObject(index);
			} catch (JSONException ignore) {
				continue;
			}
			result.add(new RemoteChapter(
					Math.max(Math.round(chapter.optDouble("start", 0) * 1000D), 0),
					Math.max(Math.round(chapter.optDouble("end", 0) * 1000D), 0),
					chapter.optString("title", "")));
		}
		result.sort(Comparator.comparingLong(RemoteChapter::startMs));
		return result;
	}

	private static Track trackAt(List<Track> tracks, long positionMs) {
		Track candidate = null;
		for (Track track : tracks) {
			if (track.startMs > positionMs) break;
			candidate = track;
		}
		return candidate;
	}

	private static AudiobookChapter chapterAt(List<AudiobookChapter> chapters, long positionMs) {
		AudiobookChapter candidate = null;
		for (AudiobookChapter chapter : chapters) {
			if (chapter.getBookOffsetMs() > positionMs) break;
			candidate = chapter;
		}
		return candidate;
	}

	private static AudiobookSource source(String endpoint, String username, String reference,
			long now) {
		String id = AudiobookIds.source("audiobookshelf", endpoint + '\n' + username);
		String name = username.isEmpty() ? "Audiobookshelf" : "Audiobookshelf - " + username;
		return new AudiobookSource(id, AudiobookSourceType.AUDIOBOOKSHELF, name, endpoint,
				reference, now, now);
	}

	static String normalizeEndpoint(String endpoint) throws IOException {
		try {
			URI uri = new URI(endpoint == null ? "" : endpoint.trim());
			String scheme = uri.getScheme();
			if ((scheme == null) || (!scheme.equalsIgnoreCase("http") &&
					!scheme.equalsIgnoreCase("https")) || (uri.getHost() == null) ||
					(uri.getQuery() != null) || (uri.getFragment() != null)) {
				throw new IOException("Enter a valid Audiobookshelf HTTP or HTTPS URL");
			}
			String path = uri.getPath();
			if ((path == null) || path.equals("/")) path = "";
			else while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
			return new URI(scheme.toLowerCase(Locale.ROOT), uri.getUserInfo(),
					uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(), path, null, null)
					.toString();
		} catch (URISyntaxException error) {
			throw new IOException("Enter a valid Audiobookshelf HTTP or HTTPS URL", error);
		}
	}

	private static String path(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String readBounded(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[16 * 1024];
		int total = 0;
		for (int read; (read = input.read(buffer)) != -1; ) {
			if (Thread.currentThread().isInterrupted()) throw new CancellationException();
			total += read;
			if (total > MAX_JSON_BYTES) throw new IOException("Audiobookshelf response is too large");
			output.write(buffer, 0, read);
		}
		return output.toString(StandardCharsets.UTF_8);
	}

	private static InputStream decode(HttpURLConnection connection, InputStream source)
			throws IOException {
		InputStream buffered = new BufferedInputStream(source);
		String encoding = connection.getContentEncoding();
		if ("gzip".equalsIgnoreCase(encoding)) return new GZIPInputStream(buffered);
		if ("deflate".equalsIgnoreCase(encoding)) return new InflaterInputStream(buffered);
		return buffered;
	}

	private static String string(JSONObject object, String key) {
		if (object == null) return "";
		Object value = object.opt(key);
		if (value instanceof JSONArray array) return array.optString(0, "").trim();
		return (value == null) ? "" : String.valueOf(value).trim();
	}

	private static double number(JSONObject object, String key) {
		return (object == null) ? 0 : object.optDouble(key, 0);
	}

	private static String value(String preferred, String fallback) {
		return preferred.isEmpty() ? fallback : preferred;
	}

	public record Library(String id, String name, String mediaType) {
	}

	public record BookPage(List<AudiobookBook> books, int total) {
	}

	private record Track(int index, long startMs, long durationMs, String title,
			String contentUrl, String mimeType) {
	}

	private record RemoteChapter(long startMs, long endMs, String title) {
	}

	private static final class HttpStatusException extends IOException {
		private final int status;

		private HttpStatusException(int status) {
			super("Audiobookshelf returned HTTP " + status);
			this.status = status;
		}
	}
}
