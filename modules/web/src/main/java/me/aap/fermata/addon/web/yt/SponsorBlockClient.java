package me.aap.fermata.addon.web.yt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.utils.net.http.HttpContentDecoder.decode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Read-only native client for SponsorBlock skip segments. */
public final class SponsorBlockClient {
	static final String API_BASE = "https://sponsor.ajay.app";
	static final int HASH_PREFIX_LENGTH = 4;
	static final int REQUEST_TIMEOUT_MS = 5_000;
	static final int MAX_ATTEMPTS = 1;
	static final int MAX_RESPONSE_BYTES = 512 * 1024;
	static final double MERGE_GAP_SECONDS = 0.25d;
	private final Transport transport;

	public SponsorBlockClient() {
		this(new UrlConnectionTransport(App.get().getExecutor()));
	}

	SponsorBlockClient(Transport transport) {
		this.transport = Objects.requireNonNull(transport);
	}

	public FutureSupplier<List<Segment>> getSegments(Request request) {
		Objects.requireNonNull(request);
		if (request.categories().isEmpty()) return completed(List.of());
		return attempt(request, buildUrl(request), 1);
	}

	private FutureSupplier<List<Segment>> attempt(Request request, String url, int attempt) {
		return transport.get(url, REQUEST_TIMEOUT_MS).then(response -> {
			int status = response.status();
			if (status == HttpURLConnection.HTTP_NOT_FOUND) return completed(List.of());
			if ((status < 200) || (status >= 300)) {
				if ((attempt < MAX_ATTEMPTS) && isRetryableStatus(status)) {
					return attempt(request, url, attempt + 1);
				}
				return failed(new HttpException(status));
			}

			try {
				return completed(parseResponse(response.body(), request));
			} catch (IOException ex) {
				return failed(ex);
			}
		}, error -> {
			if (isCancellation(error)) return failed(error);
		if ((attempt < MAX_ATTEMPTS) && isRetryableTransportFailure(error)) {
				return attempt(request, url, attempt + 1);
			}
			return failed(error);
		});
	}

	static String buildUrl(Request request) {
		String categories = categoryJson(request.categories());
		return API_BASE + "/api/skipSegments/" + hashPrefix(request.videoId()) +
				"?categories=" + encode(categories) +
				"&actionTypes=" + encode("[\"skip\"]") +
				"&service=YouTube&trimUUIDs=true";
	}

	static String hashPrefix(String videoId) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(videoId.getBytes(UTF_8));
			StringBuilder value = new StringBuilder(HASH_PREFIX_LENGTH);
			for (int i = 0; value.length() < HASH_PREFIX_LENGTH; i++) {
				value.append(Character.forDigit((hash[i] >>> 4) & 0x0f, 16));
				value.append(Character.forDigit(hash[i] & 0x0f, 16));
			}
			return value.substring(0, HASH_PREFIX_LENGTH);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	static List<Segment> parseResponse(String json, Request request) throws IOException {
		Object root = new JsonParser(json).parse();
		if (!(root instanceof List<?> buckets)) throw protocolError("Root must be an array");

		Map<String, Segment> unique = new LinkedHashMap<>();
		for (Object value : buckets) {
			if (!(value instanceof Map<?, ?> bucket)) continue;
			if (!request.videoId().equals(string(bucket.get("videoID")))) continue;
			Object rawSegments = bucket.get("segments");
			if (!(rawSegments instanceof List<?> segments)) continue;

			for (Object raw : segments) {
				Segment segment = normalizeSegment(raw, request.categories());
				if (segment == null) continue;
				String key = segment.uuid().isEmpty() ? segment.category().apiName + ':' +
						Double.toString(segment.startSeconds()) + ':' +
						Double.toString(segment.endSeconds()) : segment.uuid();
				unique.putIfAbsent(key, segment);
			}
		}

		List<Segment> result = new ArrayList<>(unique.values());
		result.sort(Comparator.comparingDouble(Segment::startSeconds)
				.thenComparingDouble(Segment::endSeconds)
				.thenComparing(segment -> segment.category().apiName)
				.thenComparing(Segment::uuid));
		return mergeSegments(result);
	}

	private static List<Segment> mergeSegments(List<Segment> sorted) {
		if (sorted.isEmpty()) return List.of();
		List<Segment> merged = new ArrayList<>(sorted.size());
		Segment current = sorted.get(0);
		for (int i = 1; i < sorted.size(); i++) {
			Segment next = sorted.get(i);
			if (next.startSeconds() <= (current.endSeconds() + MERGE_GAP_SECONDS)) {
				double end = Math.max(current.endSeconds(), next.endSeconds());
				String uuid = current.uuid().equals(next.uuid()) ? current.uuid() : "";
				current = new Segment(current.startSeconds(), end, current.category(), uuid);
			} else {
				merged.add(current);
				current = next;
			}
		}
		merged.add(current);
		return List.copyOf(merged);
	}

	private static Segment normalizeSegment(Object raw, Set<Category> requested) {
		if (!(raw instanceof Map<?, ?> item)) return null;
		Category category = Category.fromApiName(string(item.get("category")));
		if ((category == null) || !requested.contains(category)) return null;
		String action = string(item.get("actionType"));
		if (!action.isEmpty() && !"skip".equals(action)) return null;
		Object rawRange = item.get("segment");
		if (!(rawRange instanceof List<?> range) || (range.size() < 2)) return null;
		double start = number(range.get(0));
		double end = number(range.get(1));
		if (!Double.isFinite(start) || !Double.isFinite(end)) return null;
		start = Math.max(0.0, start);
		if (end <= start) return null;
		return new Segment(start, end, category, string(item.get("UUID")));
	}

	private static boolean isRetryableStatus(int status) {
		return (status == 408) || (status == 425) || (status == 429) || (status >= 500);
	}

	private static boolean isRetryableTransportFailure(Throwable error) {
		return (error instanceof IOException) && !(error instanceof HttpException) &&
				!(error instanceof ProtocolException);
	}

	static boolean isRetryableFailure(Throwable error) {
		if (error instanceof HttpException http) return isRetryableStatus(http.status());
		return isRetryableTransportFailure(error);
	}

	private static String categoryJson(Set<Category> categories) {
		StringBuilder value = new StringBuilder("[");
		for (Category category : Category.values()) {
			if (!categories.contains(category)) continue;
			if (value.length() > 1) value.append(',');
			value.append('\"').append(category.apiName).append('\"');
		}
		return value.append(']').toString();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, UTF_8);
	}

	private static String string(Object value) {
		return (value instanceof String text) ? text : "";
	}

	private static double number(Object value) {
		return (value instanceof Number number) ? number.doubleValue() : Double.NaN;
	}

	private static ProtocolException protocolError(String message) {
		return new ProtocolException(message);
	}

	public enum Category {
		SPONSOR("sponsor"),
		SELF_PROMOTION("selfpromo"),
		INTERACTION("interaction"),
		INTRO("intro"),
		OUTRO("outro"),
		PREVIEW("preview"),
		HOOK("hook"),
		MUSIC_OFF_TOPIC("music_offtopic"),
		FILLER("filler");

		private final String apiName;

		Category(String apiName) {
			this.apiName = apiName;
		}

		public String apiName() {
			return apiName;
		}

		static Category fromApiName(String value) {
			for (Category category : values()) {
				if (category.apiName.equals(value)) return category;
			}
			return null;
		}
	}

	public record Request(String videoId, Set<Category> categories) {
		public Request {
			videoId = Objects.requireNonNull(videoId).trim();
			if (videoId.isEmpty()) throw new IllegalArgumentException("Video ID is empty");
			EnumSet<Category> copy = EnumSet.noneOf(Category.class);
			if (categories != null) copy.addAll(categories);
			categories = Collections.unmodifiableSet(copy);
		}

		public String cacheKey() {
			return videoId + '|' + categoryJson(categories);
		}
	}

	public record Segment(double startSeconds, double endSeconds, Category category, String uuid) {
		public Segment {
			Objects.requireNonNull(category);
			uuid = (uuid == null) ? "" : uuid;
		}
	}

	static final class HttpException extends IOException {
		private final int status;

		HttpException(int status) {
			super("SponsorBlock request failed with HTTP " + status);
			this.status = status;
		}

		int status() {
			return status;
		}
	}

	static final class ProtocolException extends IOException {
		ProtocolException(String message) {
			super("Invalid SponsorBlock response: " + message);
		}
	}

	interface Transport {
		FutureSupplier<Response> get(String url, int timeoutMs);
	}

	record Response(int status, String body) {
		Response {
			body = (body == null) ? "" : body;
		}
	}

	static class UrlConnectionTransport implements Transport {
		private final Executor executor;

		UrlConnectionTransport(Executor executor) {
			this.executor = executor;
		}

		@Override
		public FutureSupplier<Response> get(String url, int timeoutMs) {
			ConnectionTask task = new ConnectionTask(url, timeoutMs);
			executor.execute(task);
			return task;
		}

		HttpURLConnection open(String url) throws IOException {
			return (HttpURLConnection) new URL(url).openConnection();
		}

		private final class ConnectionTask extends Promise<Response> implements Runnable {
			private final String url;
			private final int timeoutMs;
			private volatile HttpURLConnection connection;
			private volatile Thread thread;

			ConnectionTask(String url, int timeoutMs) {
				this.url = url;
				this.timeoutMs = timeoutMs;
			}

			@Override
			public void run() {
				thread = Thread.currentThread();
				try {
					if (isCancelled()) return;
					HttpURLConnection current = connection = open(url);
					current.setRequestMethod("GET");
					current.setConnectTimeout(timeoutMs);
					current.setReadTimeout(timeoutMs);
					current.setInstanceFollowRedirects(true);
					current.setRequestProperty("Accept", "application/json");
					current.setRequestProperty("Accept-Encoding", "gzip, deflate");
					current.setRequestProperty("User-Agent", "FermataX SponsorBlock client");
					if (isCancelled()) return;

					int status = current.getResponseCode();
					if ((status < 200) || (status >= 300)) {
						SponsorBlockClient.close(current.getErrorStream());
						complete(new Response(status, ""));
						return;
					}
					long length = current.getContentLengthLong();
					if (length > MAX_RESPONSE_BYTES) throw protocolError("Response is too large");
					try (InputStream input = decode(current.getInputStream(),
							current.getContentEncoding())) {
						complete(new Response(status, readBody(input)));
					}
				} catch (Throwable error) {
					if (!isCancelled()) completeExceptionally(error);
				} finally {
					HttpURLConnection current = connection;
					connection = null;
					if (current != null) current.disconnect();
					thread = null;
				}
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				boolean cancelled = super.cancel(mayInterruptIfRunning);
				if (!cancelled) return false;
				HttpURLConnection current = connection;
				if (current != null) current.disconnect();
				if (mayInterruptIfRunning) {
					Thread currentThread = thread;
					if (currentThread != null) currentThread.interrupt();
				}
				return true;
			}
		}
	}

	private static String readBody(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8 * 1024];
		for (int read; (read = input.read(buffer)) != -1; ) {
			if (Thread.currentThread().isInterrupted()) throw new CancellationException();
			if ((output.size() + read) > MAX_RESPONSE_BYTES) {
				throw protocolError("Response is too large");
			}
			output.write(buffer, 0, read);
		}
		return output.toString(UTF_8);
	}

	private static void close(InputStream input) throws IOException {
		if (input != null) input.close();
	}

	private static final class JsonParser {
		private static final int MAX_DEPTH = 32;
		private final String source;
		private int offset;

		JsonParser(String source) {
			this.source = Objects.requireNonNull(source);
		}

		Object parse() throws IOException {
			Object value = value(0);
			whitespace();
			if (offset != source.length()) throw error("Trailing data");
			return value;
		}

		private Object value(int depth) throws IOException {
			if (depth > MAX_DEPTH) throw error("Nesting is too deep");
			whitespace();
			if (offset >= source.length()) throw error("Unexpected end");
			return switch (source.charAt(offset)) {
				case '{' -> object(depth + 1);
				case '[' -> array(depth + 1);
				case '\"' -> string();
				case 't' -> literal("true", Boolean.TRUE);
				case 'f' -> literal("false", Boolean.FALSE);
				case 'n' -> literal("null", null);
				default -> number();
			};
		}

		private Map<String, Object> object(int depth) throws IOException {
			Map<String, Object> values = new LinkedHashMap<>();
			offset++;
			whitespace();
			if (take('}')) return values;
			while (true) {
				whitespace();
				if ((offset >= source.length()) || (source.charAt(offset) != '\"')) {
					throw error("Object key must be a string");
				}
				String key = string();
				whitespace();
				if (!take(':')) throw error("Missing ':'");
				values.put(key, value(depth));
				whitespace();
				if (take('}')) return values;
				if (!take(',')) throw error("Missing ','");
			}
		}

		private List<Object> array(int depth) throws IOException {
			List<Object> values = new ArrayList<>();
			offset++;
			whitespace();
			if (take(']')) return values;
			while (true) {
				values.add(value(depth));
				whitespace();
				if (take(']')) return values;
				if (!take(',')) throw error("Missing ','");
			}
		}

		private String string() throws IOException {
			StringBuilder value = new StringBuilder();
			offset++;
			while (offset < source.length()) {
				char current = source.charAt(offset++);
				if (current == '\"') return value.toString();
				if (current < 0x20) throw error("Control character in string");
				if (current != '\\') {
					value.append(current);
					continue;
				}
				if (offset >= source.length()) throw error("Unterminated escape");
				char escaped = source.charAt(offset++);
				switch (escaped) {
					case '\"', '\\', '/' -> value.append(escaped);
					case 'b' -> value.append('\b');
					case 'f' -> value.append('\f');
					case 'n' -> value.append('\n');
					case 'r' -> value.append('\r');
					case 't' -> value.append('\t');
					case 'u' -> value.append(unicode());
					default -> throw error("Invalid escape");
				}
			}
			throw error("Unterminated string");
		}

		private char unicode() throws IOException {
			if ((offset + 4) > source.length()) throw error("Invalid Unicode escape");
			int value = 0;
			for (int i = 0; i < 4; i++) {
				int digit = Character.digit(source.charAt(offset++), 16);
				if (digit < 0) throw error("Invalid Unicode escape");
				value = (value << 4) | digit;
			}
			return (char) value;
		}

		private Double number() throws IOException {
			int start = offset;
			if (take('-') && (offset >= source.length())) throw error("Invalid number");
			if (take('0')) {
				if ((offset < source.length()) && Character.isDigit(source.charAt(offset))) {
					throw error("Invalid number");
				}
			} else {
				digits();
			}
			if (take('.')) digits();
			if ((offset < source.length()) &&
					((source.charAt(offset) == 'e') || (source.charAt(offset) == 'E'))) {
				offset++;
				if ((offset < source.length()) &&
						((source.charAt(offset) == '+') || (source.charAt(offset) == '-'))) offset++;
				digits();
			}
			try {
				return Double.valueOf(source.substring(start, offset));
			} catch (NumberFormatException ex) {
				throw error("Invalid number");
			}
		}

		private void digits() throws IOException {
			int start = offset;
			while ((offset < source.length()) && Character.isDigit(source.charAt(offset))) offset++;
			if (start == offset) throw error("Invalid number");
		}

		private Object literal(String text, Object value) throws IOException {
			if (!source.regionMatches(offset, text, 0, text.length())) throw error("Invalid value");
			offset += text.length();
			return value;
		}

		private boolean take(char expected) {
			if ((offset >= source.length()) || (source.charAt(offset) != expected)) return false;
			offset++;
			return true;
		}

		private void whitespace() {
			while ((offset < source.length()) && Character.isWhitespace(source.charAt(offset))) offset++;
		}

		private IOException error(String message) {
			return protocolError(message + " at offset " + offset);
		}
	}
}
