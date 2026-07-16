package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.failed;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

final class XtreamHttpClient {
	private final XtreamAccount account;
	private final XtreamErrorMapper errors;

	XtreamHttpClient(XtreamAccount account, XtreamErrorMapper errors) {
		this.account = account;
		this.errors = errors;
	}

	<T> FutureSupplier<T> get(@Nullable String action,
													 @Nullable Map<String, String> extraParams,
													 ResponseParser<T> parser) {
		String url = account.buildPlayerApiUrl(action, extraParams);
		URL requestUrl;

		try {
			requestUrl = new URL(url);
		} catch (IOException ex) {
			return failed(errors.map(ex));
		}

		return App.get().getExecutor().submitTask(() -> {
			HttpURLConnection connection = null;

			try {
				connection = openConnection(requestUrl, "gzip, deflate");
				int status = connection.getResponseCode();
				if (status != HttpURLConnection.HTTP_OK) {
					close(connection.getErrorStream());
					throw errors.httpStatus(status, connection.getResponseMessage());
				}

				InputStream payload = connection.getInputStream();
				if (payload == null) throw new IOException("Xtream response is empty");
				try (InputStream in = decode(payload, connection.getContentEncoding())) {
					return parser.parse(in);
				}
			} catch (Throwable ex) {
				throw errors.map(ex);
			} finally {
				if (connection != null) connection.disconnect();
			}
		});
	}

	<T> FutureSupplier<T> probe(String url, ProbeHandler<T> handler) {
		URL requestUrl;

		try {
			requestUrl = new URL(url);
		} catch (IOException ex) {
			return failed(errors.map(ex));
		}

		return App.get().getExecutor().submitTask(() -> {
			HttpURLConnection connection = null;

			try {
				connection = openConnection(requestUrl, "identity");
				connection.setRequestProperty("Range", "bytes=0-0");
				int status = connection.getResponseCode();
				close((status >= HttpURLConnection.HTTP_BAD_REQUEST) ?
						connection.getErrorStream() : connection.getInputStream());
				return handler.handle(status, connection.getResponseMessage());
			} catch (Throwable ex) {
				throw errors.map(ex);
			} finally {
				if (connection != null) connection.disconnect();
			}
		});
	}

	private HttpURLConnection openConnection(URL requestUrl, String acceptEncoding)
			throws IOException {
		HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
		connection.setRequestMethod("GET");
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("Accept-Encoding", acceptEncoding);
		String userAgent = account.getUserAgent();
		if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent);

		int timeout = account.getResponseTimeout();
		if (timeout > 0) {
			int millis = (int) Math.min(Integer.MAX_VALUE, timeout * 1000L);
			connection.setConnectTimeout(millis);
			connection.setReadTimeout(millis);
		}

		return connection;
	}

	private InputStream decode(InputStream in, CharSequence encoding) throws IOException {
		if (encoding == null) return in;
		String value = encoding.toString().toLowerCase(Locale.ROOT);
		if (value.contains("gzip")) return new GZIPInputStream(in);
		if (value.contains("deflate")) return new InflaterInputStream(in);
		throw new IOException("Unsupported Xtream content encoding: " + encoding);
	}

	private void close(@Nullable InputStream in) throws IOException {
		if (in != null) in.close();
	}

	interface ResponseParser<T> {
		T parse(InputStream in) throws IOException;
	}

	interface ProbeHandler<T> {
		T handle(int status, String reason) throws IOException;
	}
}
