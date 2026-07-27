package me.aap.fermata.engine.exoplayer;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.media.engine.PlaybackFailureException;
import me.aap.fermata.media.engine.PlaybackFailureException.Reason;
import me.aap.fermata.media.net.PlaybackRequestValidationException;
import me.aap.fermata.media.net.ResolvedRemotePlaybackRequest;
import me.aap.fermata.media.net.ValidatedPlaybackEndpoint;
import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;

/** HTTP data source that validates every redirect hop before forwarding request headers. */
@UnstableApi
final class ProfileHttpDataSource extends BaseDataSource {
	private static final int MAX_REDIRECTS = 10;
	private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
	private static final int READ_TIMEOUT_MILLIS = 20_000;
	private static final int P2P_READ_TIMEOUT_MILLIS = 130_000;

	private final ResolvedRemotePlaybackRequest request;
	private HttpURLConnection connection;
	private PinnedHttpConnection pinnedConnection;
	private InputStream input;
	private Uri currentUri;
	private Map<String, List<String>> responseHeaders = Collections.emptyMap();
	private long bytesRemaining;
	private boolean opened;

	ProfileHttpDataSource(ResolvedRemotePlaybackRequest request) {
		super(true);
		this.request = request;
	}

	@Override
	public long open(DataSpec dataSpec) throws IOException {
		transferInitializing(dataSpec);
		URI current = URI.create(dataSpec.uri.toString());
		try {
			for (int redirects = 0; ; redirects++) {
				ValidatedPlaybackEndpoint endpoint = request.validateEndpoint(current);
				if (endpoint == null) connection = openConnection(current, dataSpec);
				else pinnedConnection = openPinnedConnection(endpoint, dataSpec);
				int status = responseCode();
				if (!isRedirect(status)) break;
				if (redirects >= MAX_REDIRECTS) throw failure("Too many playback redirects", null);
				String location = responseHeader("Location");
				if ((location == null) || location.isBlank()) {
					throw failure("Playback redirect has no destination", null);
				}
				URI destination = current.resolve(location);
				if (!request.getProfile().isRedirectAllowed(current, destination)) {
					throw failure("Playback redirect origin is not allowed", null);
				}
				closeConnection();
				current = destination;
			}

			int status = responseCode();
			if ((status < 200) || (status > 299)) {
				if (isP2p()) {
					Reason reason = p2pFailureReason(status);
					if (reason != null) throw new PlaybackFailureException(reason);
				}
				throw failure("Playback server returned HTTP " + status, null);
			}
			currentUri = Uri.parse(current.toString());
			responseHeaders = responseHeaders();
			input = responseBody();
			if ((dataSpec.position > 0) && (status == HttpURLConnection.HTTP_OK)) {
				skipFully(input, dataSpec.position);
			}
			long contentLength = contentLength();
			if (dataSpec.length != C.LENGTH_UNSET) bytesRemaining = dataSpec.length;
			else if (contentLength >= 0) {
				bytesRemaining = Math.max(0, contentLength -
						((status == HttpURLConnection.HTTP_OK) ? dataSpec.position : 0));
			} else bytesRemaining = C.LENGTH_UNSET;
			opened = true;
			transferStarted(dataSpec);
			return bytesRemaining;
		} catch (PlaybackRequestValidationException | IllegalArgumentException ex) {
			closeConnection();
			throw failure("Playback request validation failed", ex);
		} catch (IOException ex) {
			closeConnection();
			if (isP2p() && (PlaybackFailureException.find(ex) == null)) {
				throw new PlaybackFailureException(Reason.P2P_ENGINE_UNAVAILABLE, ex);
			}
			throw ex;
		}
	}

	private PinnedHttpConnection openPinnedConnection(ValidatedPlaybackEndpoint endpoint,
			DataSpec dataSpec) throws IOException, PlaybackRequestValidationException {
		if ((dataSpec.httpBody != null) && (dataSpec.httpBody.length != 0)) {
			throw failure("Pinned playback requests do not support a request body", null);
		}
		Map<String, String> headers = requestHeaders(endpoint.uri(), dataSpec);
		return PinnedHttpConnection.open(endpoint, dataSpec.getHttpMethodString(), headers,
				CONNECT_TIMEOUT_MILLIS, readTimeoutMillis());
	}

	private HttpURLConnection openConnection(URI uri, DataSpec dataSpec)
			throws IOException, PlaybackRequestValidationException {
		HttpURLConnection next = (HttpURLConnection) new URL(uri.toString()).openConnection();
		next.setInstanceFollowRedirects(false);
		next.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
		next.setReadTimeout(readTimeoutMillis());
		next.setRequestMethod(dataSpec.getHttpMethodString());
		for (Map.Entry<String, String> header : requestHeaders(uri, dataSpec).entrySet()) {
			next.setRequestProperty(header.getKey(), header.getValue());
		}
		return next;
	}

	private Map<String, String> requestHeaders(URI uri, DataSpec dataSpec)
			throws PlaybackRequestValidationException {
		LinkedHashMap<String, String> headers = new LinkedHashMap<>(dataSpec.httpRequestHeaders);
		headers.putAll(request.headersFor(uri));
		if ((dataSpec.position != 0) || (dataSpec.length != C.LENGTH_UNSET)) {
			long end = (dataSpec.length == C.LENGTH_UNSET) ? -1
					: dataSpec.position + dataSpec.length - 1;
			headers.put("Range", "bytes=" + dataSpec.position + '-'
					+ ((end == -1) ? "" : Long.toString(end)));
		}
		return headers;
	}

	private int responseCode() throws IOException {
		return (pinnedConnection != null) ? pinnedConnection.status() : connection.getResponseCode();
	}

	private String responseHeader(String name) {
		return (pinnedConnection != null) ? pinnedConnection.header(name) :
				connection.getHeaderField(name);
	}

	private Map<String, List<String>> responseHeaders() {
		return (pinnedConnection != null) ? pinnedConnection.headers() : connection.getHeaderFields();
	}

	private InputStream responseBody() throws IOException {
		return (pinnedConnection != null) ? pinnedConnection.body() : connection.getInputStream();
	}

	private long contentLength() {
		if (pinnedConnection == null) return connection.getContentLengthLong();
		String value = pinnedConnection.header("Content-Length");
		if (value == null) return -1;
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		if (length == 0) return 0;
		if (bytesRemaining == 0) return C.RESULT_END_OF_INPUT;
		int requested = (bytesRemaining == C.LENGTH_UNSET) ? length
				: (int) Math.min(length, bytesRemaining);
		int read;
		try {
			read = input.read(buffer, offset, requested);
		} catch (IOException error) {
			if (isP2p()) throw new PlaybackFailureException(Reason.P2P_DATA_TIMEOUT, error);
			throw error;
		}
		if (read == -1) {
			if (bytesRemaining == C.LENGTH_UNSET) return C.RESULT_END_OF_INPUT;
			EOFException error = new EOFException(
					"Playback response ended before the requested range");
			if (isP2p()) throw new PlaybackFailureException(Reason.P2P_DATA_TIMEOUT, error);
			throw error;
		}
		if (bytesRemaining != C.LENGTH_UNSET) bytesRemaining -= read;
		bytesTransferred(read);
		return read;
	}

	@Nullable
	@Override
	public Uri getUri() {
		return currentUri;
	}

	@Override
	public Map<String, List<String>> getResponseHeaders() {
		return responseHeaders;
	}

	@Override
	public void close() {
		closeConnection();
		currentUri = null;
		responseHeaders = Collections.emptyMap();
		if (opened) {
			opened = false;
			transferEnded();
		}
	}

	private void closeConnection() {
		if (input != null) {
			try {
				input.close();
			} catch (IOException ignored) {
			}
			input = null;
		}
		if (connection != null) {
			connection.disconnect();
			connection = null;
		}
		if (pinnedConnection != null) {
			try {
				pinnedConnection.close();
			} catch (IOException ignored) {
			}
			pinnedConnection = null;
		}
	}

	private IOException failure(String message, Throwable cause) {
		String safe = message + " (" + request.getProfile().getDiagnosticIdentity() + ')';
		return (cause == null) ? new IOException(safe) : new IOException(safe, cause);
	}

	private boolean isP2p() {
		return request.getProfile().getRequiredEngineCapabilities().contains(
				EngineCapability.P2P_STREAMING);
	}

	private int readTimeoutMillis() {
		return isP2p() ? P2P_READ_TIMEOUT_MILLIS : READ_TIMEOUT_MILLIS;
	}

	@Nullable
	static Reason p2pFailureReason(int status) {
		return switch (status) {
			case 502 -> Reason.P2P_ENGINE_UNAVAILABLE;
			case 503 -> Reason.P2P_NO_PEERS;
			case 504 -> Reason.P2P_DATA_TIMEOUT;
			default -> null;
		};
	}

	private static boolean isRedirect(int status) {
		return (status == 300) || (status == 301) || (status == 302) || (status == 303)
				|| (status == 307) || (status == 308);
	}

	private static void skipFully(InputStream input, long count) throws IOException {
		long remaining = count;
		while (remaining > 0) {
			long skipped = input.skip(remaining);
			if (skipped > 0) {
				remaining -= skipped;
				continue;
			}
			if (input.read() == -1) throw new EOFException("Playback range starts past response end");
			remaining--;
		}
	}
}
