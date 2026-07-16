package me.aap.fermata.addon.podcast.net;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLException;

import me.aap.fermata.BuildConfig;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.fermata.addon.podcast.util.PodcastUrls;

public class PodcastHttpClient {
	public static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
	public static final int MAX_DOCUMENT_COMPRESSED_BYTES = 8 * 1024 * 1024;
	public static final int MAX_DOCUMENT_BYTES = 16 * 1024 * 1024;
	private static final int MAX_REDIRECTS = 10;
	private static final int CONNECT_TIMEOUT = 10_000;
	private static final int READ_TIMEOUT = 20_000;

	public <T> FutureSupplier<T> getJson(String url, BodyParser<T> parser) {
		return App.get().execute(() -> requestJson(url, parser));
	}

	public <T> FutureSupplier<DocumentResponse<T>> getDocument(DocumentRequest request,
			DocumentParser<T> parser) {
		return App.get().execute(() -> requestDocument(request, parser));
	}

	<T> T requestJson(String url, BodyParser<T> parser) throws IOException {
		HttpURLConnection connection;
		try {
			connection = open(url);
		} catch (IOException ex) {
			throw map(ex);
		}

		try {
			return readJson(connection, parser);
		} catch (IOException ex) {
			throw map(ex);
		} finally {
			connection.disconnect();
		}
	}

	HttpURLConnection open(String url) throws IOException {
		HttpURLConnection connection = openConnection(url);
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
		connection.setRequestProperty("User-Agent", "FermataX/" + BuildConfig.VERSION_NAME);
		return connection;
	}

	HttpURLConnection openDocument(String url) throws IOException {
		HttpURLConnection connection = openConnection(url);
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);
		connection.setInstanceFollowRedirects(false);
		connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
		connection.setRequestProperty("User-Agent", "FermataX/" + BuildConfig.VERSION_NAME);
		return connection;
	}

	HttpURLConnection openConnection(String url) throws IOException {
		return (HttpURLConnection) new URL(url).openConnection();
	}

	public <T> DocumentResponse<T> requestDocument(DocumentRequest request,
			DocumentParser<T> parser) throws IOException {
		String current = normalize(request.url);
		if (current == null) {
			throw new PodcastException(PodcastErrorCode.INVALID_CONTENT,
					"Podcast URL is invalid");
		}
		String authorization = request.authorization;

		for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
			HttpURLConnection connection;
			try {
				connection = openDocument(current);
			} catch (IOException ex) {
				throw map(ex);
			}

			try {
				connection.setRequestProperty("Accept", request.accept);
				if (authorization != null) {
					connection.setRequestProperty("Authorization", authorization);
				}
				if (request.etag != null) connection.setRequestProperty("If-None-Match", request.etag);
				if (request.lastModified != null) {
					connection.setRequestProperty("If-Modified-Since", request.lastModified);
				}

				int status = connection.getResponseCode();
				if (isRedirect(status)) {
					if (redirects == MAX_REDIRECTS) {
						throw new PodcastException(PodcastErrorCode.REDIRECT_REJECTED,
								"Podcast request has too many redirects");
					}
					String location = connection.getHeaderField("Location");
					String next = resolve(current, location);
					if (next == null) {
						throw new PodcastException(PodcastErrorCode.REDIRECT_REJECTED,
								"Podcast redirect is invalid");
					}
					if ((authorization != null) && isSecure(current) && !isSecure(next) &&
							!request.allowAuthenticatedDowngrade) {
						throw new PodcastException(PodcastErrorCode.REDIRECT_REJECTED,
								"Podcast authentication cannot be sent over an insecure redirect");
					}
					if (!sameOrigin(current, next)) authorization = null;
					current = next;
					continue;
				}

				String contentType = connection.getContentType();
				String etag = connection.getHeaderField("ETag");
				String lastModified = connection.getHeaderField("Last-Modified");
				if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
					return new DocumentResponse<>(status, current, contentType, etag,
							lastModified, null);
				}
				if ((status < 200) || (status >= 300)) throw statusError(connection, status);

				long length = connection.getContentLengthLong();
				if (length > MAX_DOCUMENT_COMPRESSED_BYTES) {
					throw new PodcastException(PodcastErrorCode.TOO_LARGE,
							"Podcast document is too large");
				}
				try (InputStream raw = new LimitedInputStream(connection.getInputStream(),
						MAX_DOCUMENT_COMPRESSED_BYTES);
					 InputStream decoded = decode(connection, raw);
					 InputStream bounded = new LimitedInputStream(decoded, MAX_DOCUMENT_BYTES)) {
					T body = parser.parse(bounded, contentType, current);
					return new DocumentResponse<>(status, current, contentType, etag,
							lastModified, body);
				}
			} catch (IOException ex) {
				throw map(ex);
			} finally {
				connection.disconnect();
			}
		}

		throw new PodcastException(PodcastErrorCode.REDIRECT_REJECTED,
				"Podcast request has too many redirects");
	}

	static <T> T readJson(HttpURLConnection connection, BodyParser<T> parser) throws IOException {
		int status = connection.getResponseCode();
		if ((status < 200) || (status >= 300)) throw statusError(connection, status);
		long length = connection.getContentLengthLong();
		if (length > MAX_JSON_BYTES) {
			throw new PodcastException(PodcastErrorCode.TOO_LARGE,
					"Podcast directory response is too large");
		}

		try (InputStream raw = new LimitedInputStream(connection.getInputStream(), MAX_JSON_BYTES);
				 InputStream decoded = decode(connection, raw);
				 InputStream bounded = new LimitedInputStream(decoded, MAX_JSON_BYTES);
				 InputStream json = prepareJson(bounded)) {
			return parser.parse(json);
		}
	}

	static InputStream prepareJson(InputStream source) throws IOException {
		PushbackInputStream input = new PushbackInputStream(source, 3);
		byte[] prefix = new byte[3];
		int prefixLength = 0;
		while (prefixLength < prefix.length) {
			int read = input.read(prefix, prefixLength, prefix.length - prefixLength);
			if (read == -1) break;
			prefixLength += read;
		}
		boolean bom = (prefixLength == 3) && ((prefix[0] & 0xFF) == 0xEF) &&
				((prefix[1] & 0xFF) == 0xBB) && ((prefix[2] & 0xFF) == 0xBF);
		if (!bom && (prefixLength > 0)) input.unread(prefix, 0, prefixLength);
		int first;
		do {
			first = input.read();
		} while ((first != -1) && Character.isWhitespace(first));

		if (first == '<') {
			throw new PodcastException(PodcastErrorCode.INVALID_CONTENT,
					"Podcast directory returned HTML instead of JSON");
		}
		if ((first != '{') && (first != '[')) {
			throw new PodcastException(PodcastErrorCode.INVALID_CONTENT,
					"Podcast directory returned invalid JSON");
		}
		input.unread(first);
		return input;
	}

	private static InputStream decode(HttpURLConnection connection, InputStream source)
			throws IOException {
		InputStream buffered = new BufferedInputStream(source);
		String encoding = connection.getContentEncoding();
		if ("gzip".equalsIgnoreCase(encoding)) return new GZIPInputStream(buffered);
		if ("deflate".equalsIgnoreCase(encoding)) return new InflaterInputStream(buffered);
		return buffered;
	}

	private static PodcastException statusError(HttpURLConnection connection, int status) {
		PodcastErrorCode code;
		if (status == 401) code = PodcastErrorCode.AUTH_REQUIRED;
		else if (status == 403) code = PodcastErrorCode.AUTH_REJECTED;
		else if (status == 404) code = PodcastErrorCode.NOT_FOUND;
		else if (status == 429) code = PodcastErrorCode.RATE_LIMITED;
		else if (status >= 500) code = PodcastErrorCode.SERVER;
		else code = PodcastErrorCode.HTTP;

		long retryAfter = parseRetryAfter(connection.getHeaderField("Retry-After"),
				System.currentTimeMillis());
		return new PodcastException(code, "Podcast request failed with HTTP " + status,
				status, retryAfter);
	}

	static long parseRetryAfter(String value, long nowMs) {
		if ((value == null) || (value = value.trim()).isEmpty()) return 0;
		try {
			return Math.max(Math.multiplyExact(Long.parseLong(value), 1000L), 0);
		} catch (ArithmeticException | NumberFormatException ignore) {
		}
		try {
			long retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
					.toInstant().toEpochMilli();
			return Math.max(retryAt - nowMs, 0);
		} catch (DateTimeParseException ignore) {
			return 0;
		}
	}

	private static IOException map(IOException error) {
		if (error instanceof PodcastException) return error;
		if (error instanceof UnknownHostException) {
			return new PodcastException(PodcastErrorCode.DNS,
					"Cannot find the podcast directory", error);
		}
		if (error instanceof SocketTimeoutException) {
			return new PodcastException(PodcastErrorCode.TIMEOUT,
					"Podcast directory connection timed out", error);
		}
		if (error instanceof SSLException) {
			return new PodcastException(PodcastErrorCode.TLS,
					"Secure podcast directory connection failed", error);
		}
		if (error instanceof ConnectException) {
			return new PodcastException(PodcastErrorCode.OFFLINE,
					"Cannot connect to the podcast service", error);
		}
		return error;
	}

	private static boolean isRedirect(int status) {
		return (status == HttpURLConnection.HTTP_MOVED_PERM) ||
				(status == HttpURLConnection.HTTP_MOVED_TEMP) ||
				(status == HttpURLConnection.HTTP_SEE_OTHER) ||
				(status == 307) || (status == 308);
	}

	private static String resolve(String base, String location) {
		if ((location == null) || location.trim().isEmpty()) return null;
		try {
			return normalize(new URI(base).resolve(location.trim()).toString());
		} catch (URISyntaxException | IllegalArgumentException ex) {
			return null;
		}
	}

	private static String normalize(String value) {
		return PodcastUrls.normalizeHttpUrl(value);
	}

	private static boolean isSecure(String value) {
		return value.regionMatches(true, 0, "https:", 0, 6);
	}

	private static boolean sameOrigin(String first, String second) {
		try {
			URI a = new URI(first);
			URI b = new URI(second);
			return lower(a.getScheme()).equals(lower(b.getScheme())) &&
					lower(a.getHost()).equals(lower(b.getHost())) &&
					effectivePort(a) == effectivePort(b);
		} catch (URISyntaxException | NullPointerException ex) {
			return false;
		}
	}

	private static int effectivePort(URI uri) {
		if (uri.getPort() != -1) return uri.getPort();
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private static String lower(String value) {
		return (value == null) ? null : value.toLowerCase(Locale.ROOT);
	}

	public interface BodyParser<T> {
		T parse(InputStream input) throws IOException;
	}

	public interface DocumentParser<T> {
		T parse(InputStream input, String contentType, String finalUrl) throws IOException;
	}

	public static final class DocumentRequest {
		private final String url;
		private final String accept;
		private final String authorization;
		private final String etag;
		private final String lastModified;
		private final boolean allowAuthenticatedDowngrade;

		public DocumentRequest(String url, String accept, String authorization, String etag,
				String lastModified, boolean allowAuthenticatedDowngrade) {
			this.url = url;
			this.accept = ((accept == null) || accept.isEmpty()) ? "*/*" : accept;
			this.authorization = emptyToNull(authorization);
			this.etag = emptyToNull(etag);
			this.lastModified = emptyToNull(lastModified);
			this.allowAuthenticatedDowngrade = allowAuthenticatedDowngrade;
		}

		public static DocumentRequest feed(String url) {
			return new DocumentRequest(url,
					"application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.2",
					null, null, null, false);
		}

		public String getUrl() { return url; }
		public String getAccept() { return accept; }
		public String getAuthorization() { return authorization; }
		public String getEtag() { return etag; }
		public String getLastModified() { return lastModified; }
		public boolean isAuthenticatedDowngradeAllowed() {
			return allowAuthenticatedDowngrade;
		}

		private static String emptyToNull(String value) {
			return ((value == null) || value.isEmpty()) ? null : value;
		}
	}

	public static final class DocumentResponse<T> {
		private final int status;
		private final String finalUrl;
		private final String contentType;
		private final String etag;
		private final String lastModified;
		private final T body;

		public DocumentResponse(int status, String finalUrl, String contentType, String etag,
				String lastModified, T body) {
			this.status = status;
			this.finalUrl = finalUrl;
			this.contentType = contentType;
			this.etag = etag;
			this.lastModified = lastModified;
			this.body = body;
		}

		public int getStatus() { return status; }
		public String getFinalUrl() { return finalUrl; }
		public String getContentType() { return contentType; }
		public String getEtag() { return etag; }
		public String getLastModified() { return lastModified; }
		public T getBody() { return body; }
		public boolean isNotModified() { return status == HttpURLConnection.HTTP_NOT_MODIFIED; }
	}

	private static final class LimitedInputStream extends FilterInputStream {
		private long remaining;

		LimitedInputStream(InputStream input, long limit) {
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
			throw new PodcastException(PodcastErrorCode.TOO_LARGE,
					"Podcast directory response is too large");
		}

		private void checkCancelled() {
			if (Thread.currentThread().isInterrupted()) throw new CancellationException();
		}
	}
}
