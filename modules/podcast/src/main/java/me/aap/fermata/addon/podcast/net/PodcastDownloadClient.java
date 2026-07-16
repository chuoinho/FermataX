package me.aap.fermata.addon.podcast.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

import androidx.annotation.Nullable;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.podcast.download.PodcastDownloadResult;
import me.aap.fermata.addon.podcast.util.PodcastUrls;

public class PodcastDownloadClient {
	private static final int MAX_REDIRECTS = 10;
	private static final int CONNECT_TIMEOUT = 10_000;
	private static final int READ_TIMEOUT = 60_000;

	public PodcastDownloadResult download(String sourceUrl, Map<String, String> requestHeaders,
			File partial, File complete, ProgressListener listener) throws IOException {
		return download(sourceUrl, requestHeaders, partial, complete, null, null, listener);
	}

	public PodcastDownloadResult download(String sourceUrl, Map<String, String> requestHeaders,
			File partial, File complete, @Nullable String etag, @Nullable String lastModified,
			ProgressListener listener) throws IOException {
		String url = PodcastUrls.normalizeHttpUrl(sourceUrl);
		if (url == null) throw invalid("Podcast media URL is invalid");
		File directory = complete.getParentFile();
		if ((directory != null) && !directory.isDirectory() && !directory.mkdirs()) {
			throw new IOException("Cannot create the Podcast download directory");
		}
		return download(url, new HashMap<>(requestHeaders), partial, complete, etag, lastModified,
				listener, true);
	}

	private PodcastDownloadResult download(String startUrl, Map<String, String> headers,
			File partial, File complete, @Nullable String etag, @Nullable String lastModified,
			ProgressListener listener, boolean allowResume) throws IOException {
		String current = startUrl;
		long existing = allowResume && partial.isFile() ? partial.length() : 0;
		File directory = complete.getParentFile();

		for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
			checkCancelled(listener);
			HttpURLConnection connection = open(current);
			try {
				for (Map.Entry<String, String> header : headers.entrySet()) {
					connection.setRequestProperty(header.getKey(), header.getValue());
				}
				if (existing > 0) {
					connection.setRequestProperty("Range", "bytes=" + existing + '-');
					String validator = ((etag == null) || etag.isEmpty()) ? lastModified : etag;
					if ((validator != null) && !validator.isEmpty()) {
						connection.setRequestProperty("If-Range", validator);
					}
				}
				int status = connection.getResponseCode();
				if (isRedirect(status)) {
					if (redirects == MAX_REDIRECTS) throw redirect("Too many Podcast media redirects");
					String next = resolve(current, connection.getHeaderField("Location"));
					if (next == null) throw redirect("Podcast media redirect is invalid");
					if (headers.containsKey("Authorization") && isSecure(current) && !isSecure(next)) {
						throw redirect("Podcast authentication cannot use an insecure redirect");
					}
					if (!sameOrigin(current, next)) headers.remove("Authorization");
					current = next;
					continue;
				}
				if ((status == 416) && (existing > 0)) {
					if (!partial.delete()) throw new IOException("Cannot restart Podcast download");
					return download(startUrl, headers, partial, complete, etag, lastModified,
							listener, false);
				}
				if ((status != 200) && (status != 206)) throw status(status);

				boolean append = (status == 206) && (existing > 0);
				ContentRange range = (status == 206) ?
						parseContentRange(connection.getHeaderField("Content-Range")) : null;
				if ((status == 206) && ((range == null) || (range.start != existing))) {
					if (allowResume && (existing > 0)) {
						if (!partial.delete()) throw new IOException("Cannot restart Podcast download");
						return download(startUrl, headers, partial, complete, etag, lastModified,
								listener, false);
					}
					throw new IOException("Podcast server returned an invalid byte range");
				}
				if (!append) existing = 0;
				long bodyLength = connection.getContentLengthLong();
				if ((range != null) && (bodyLength >= 0) &&
						(bodyLength != (range.end - range.start + 1))) {
					throw new IOException("Podcast server returned an invalid byte range length");
				}
				long total = ((range != null) && (range.total >= 0)) ? range.total :
						((bodyLength < 0) ? -1 : existing + bodyLength);
				if ((total > 0) && (directory != null) &&
						(usableSpace(directory) < (total - existing))) {
					throw new PodcastException(PodcastErrorCode.TOO_LARGE,
							"Not enough storage for this podcast episode");
				}

				long downloaded = existing;
				try (InputStream input = new BufferedInputStream(connection.getInputStream());
					 FileOutputStream file = new FileOutputStream(partial, append);
					 BufferedOutputStream output = new BufferedOutputStream(file)) {
					byte[] buffer = new byte[64 * 1024];
					for (int read; (read = input.read(buffer)) != -1; ) {
						checkCancelled(listener);
						output.write(buffer, 0, read);
						downloaded += read;
						if ((directory != null) && (usableSpace(directory) == 0)) {
							throw new PodcastException(PodcastErrorCode.TOO_LARGE,
									"Not enough storage for this podcast episode");
						}
						if (listener != null) listener.onProgress(downloaded, total);
					}
					output.flush();
					file.getFD().sync();
				}
				if ((total >= 0) && (downloaded != total)) {
					throw new IOException("Podcast download ended before the expected length");
				}
				checkCancelled(listener);
				Files.move(partial.toPath(), complete.toPath(), StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
				return new PodcastDownloadResult(complete, downloaded, total,
						connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"));
			} finally {
				connection.disconnect();
			}
		}
		throw redirect("Too many Podcast media redirects");
	}

	protected HttpURLConnection open(String url) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);
		connection.setInstanceFollowRedirects(false);
		connection.setRequestProperty("Accept-Encoding", "identity");
		connection.setRequestProperty("User-Agent", "FermataX/" + BuildConfig.VERSION_NAME);
		return connection;
	}

	protected long usableSpace(File directory) {
		return directory.getUsableSpace();
	}

	private static PodcastException status(int status) {
		PodcastErrorCode code;
		if (status == 401) code = PodcastErrorCode.AUTH_REQUIRED;
		else if (status == 403) code = PodcastErrorCode.AUTH_REJECTED;
		else if (status == 404) code = PodcastErrorCode.NOT_FOUND;
		else if (status == 429) code = PodcastErrorCode.RATE_LIMITED;
		else if (status >= 500) code = PodcastErrorCode.SERVER;
		else code = PodcastErrorCode.HTTP;
		return new PodcastException(code, "Podcast media request failed with HTTP " + status,
				status, 0);
	}

	private static PodcastException invalid(String message) {
		return new PodcastException(PodcastErrorCode.INVALID_CONTENT, message);
	}

	private static PodcastException redirect(String message) {
		return new PodcastException(PodcastErrorCode.REDIRECT_REJECTED, message);
	}

	private static void checkCancelled(@Nullable ProgressListener listener) {
		if (Thread.currentThread().isInterrupted() ||
				((listener != null) && listener.isCancelled())) throw new CancellationException();
	}

	private static boolean isRedirect(int status) {
		return (status == 301) || (status == 302) || (status == 303) ||
				(status == 307) || (status == 308);
	}

	@Nullable
	private static ContentRange parseContentRange(@Nullable String value) {
		if (value == null) return null;
		value = value.trim();
		if (!value.regionMatches(true, 0, "bytes ", 0, 6)) return null;
		int dash = value.indexOf('-', 6);
		int slash = value.indexOf('/', dash + 1);
		if ((dash <= 6) || (slash <= dash + 1)) return null;
		try {
			long start = Long.parseLong(value.substring(6, dash).trim());
			long end = Long.parseLong(value.substring(dash + 1, slash).trim());
			String totalText = value.substring(slash + 1).trim();
			long total = "*".equals(totalText) ? -1 : Long.parseLong(totalText);
			if ((start < 0) || (end < start) || ((total >= 0) && (end >= total))) return null;
			return new ContentRange(start, end, total);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String resolve(String base, String location) {
		if ((location == null) || location.trim().isEmpty()) return null;
		try {
			return PodcastUrls.normalizeHttpUrl(new URI(base).resolve(location.trim()).toString());
		} catch (URISyntaxException | IllegalArgumentException ex) {
			return null;
		}
	}

	private static boolean sameOrigin(String first, String second) {
		try {
			URI a = new URI(first);
			URI b = new URI(second);
			return lower(a.getScheme()).equals(lower(b.getScheme())) &&
					lower(a.getHost()).equals(lower(b.getHost())) && port(a) == port(b);
		} catch (URISyntaxException | NullPointerException ex) {
			return false;
		}
	}

	private static int port(URI uri) {
		if (uri.getPort() != -1) return uri.getPort();
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private static boolean isSecure(String value) {
		return value.regionMatches(true, 0, "https:", 0, 6);
	}

	private static String lower(String value) {
		return (value == null) ? null : value.toLowerCase(Locale.ROOT);
	}

	private record ContentRange(long start, long end, long total) {}

	public interface ProgressListener {
		void onProgress(long downloaded, long total);

		default boolean isCancelled() {
			return false;
		}
	}
}
