package me.aap.fermata.addon.stremio.net.http;

import static me.aap.utils.async.Completed.completedVoid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.utils.function.Cancellable;
import me.aap.utils.net.http.HttpConnection;

/** Adapter over the project's async HTTP stack. Redirects remain owned by StremioHttpClient. */
public final class ProjectHttpTransport implements HttpTransport {
	@Override
	public TransportCall execute(TransportRequest request) {
		var result = new CompletableFuture<TransportResponse>();
		var cancelled = new AtomicBoolean();
		var connection = new AtomicReference<HttpConnection>();
		var delivered = new AtomicReference<ProjectResponse>();
		var options = new HttpConnection.Opts();
		try {
			options.url = request.endpoint().endpoint().uri().toURL();
			options.address = new InetSocketAddress(request.endpoint().pinnedAddress(),
					request.endpoint().endpoint().port());
			options.connectTimeout = secondsCeil(request.deadlines().connect());
			options.responseTimeout = secondsCeil(request.deadlines().headers());
			options.readTimeout = secondsCeil(request.deadlines().body());
			options.keepAlive = false;
			options.maxRedirects = 0;
			options.maxReconnects = 0;
			options.builder = builder -> {
				request.headers().forEach(builder::addHeader);
				return builder.build();
			};
		} catch (Exception ex) {
			result.completeExceptionally(ex);
			return completedCall(result);
		}

		Cancellable cancellable = HttpConnection.connect(options, (response, error) -> {
			if (error != null) {
				result.completeExceptionally(beforeHeadersFailure(error));
				return completedVoid();
			}
			connection.set(response.getConnection());
			if (cancelled.get()) {
				response.getConnection().close();
				return completedVoid();
			}
			int status = response.getStatusCode();
			Map<String, String> headers = parseHeaders(response.getHeaders());
			var projectResponse = new ProjectResponse(status, headers, response.getConnection());
			delivered.set(projectResponse);
			var payloadFuture = response.getPayload((payload, payloadError) -> {
				if (payloadError != null) projectResponse.failBody(bodyFailure(payloadError));
				else if (!cancelled.get()) projectResponse.completeBody(toByteArray(payload));
				return completedVoid();
			}, true, Math.toIntExact(request.maxBodyBytes()));
			result.complete(projectResponse);
			return payloadFuture;
		});

		return new TransportCall() {
			@Override
			public CompletableFuture<TransportResponse> response() {
				return result;
			}

			@Override
			public void cancel() {
				if (!cancelled.compareAndSet(false, true)) return;
				cancellable.cancel();
				ProjectResponse response = delivered.getAndSet(null);
				if (response != null) response.close();
				HttpConnection active = connection.getAndSet(null);
				if (active != null) active.close();
			}
		};
	}

	private static TransportCall completedCall(CompletableFuture<TransportResponse> response) {
		return new TransportCall() {
			@Override
			public CompletableFuture<TransportResponse> response() {
				return response;
			}

			@Override
			public void cancel() {
			}
		};
	}

	private static int secondsCeil(java.time.Duration duration) {
		return Math.max(1, Math.toIntExact((duration.toMillis() + 999L) / 1000L));
	}

	private static byte[] toByteArray(ByteBuffer payload) {
		ByteBuffer copy = payload.duplicate();
		byte[] bytes = new byte[copy.remaining()];
		copy.get(bytes);
		return bytes;
	}

	private static Throwable beforeHeadersFailure(Throwable error) {
		if (error instanceof TimeoutException timeout) {
			HttpFailure.Code code = (timeout.getMessage() != null) &&
					timeout.getMessage().startsWith("Connect") ? HttpFailure.Code.CONNECT_TIMEOUT :
					HttpFailure.Code.HEADER_TIMEOUT;
			return new HttpFailure(code, timeout.getMessage(), timeout);
		}
		return error;
	}

	private static Throwable bodyFailure(Throwable error) {
		if (error instanceof TimeoutException timeout) {
			return new HttpFailure(HttpFailure.Code.BODY_TIMEOUT, timeout.getMessage(), timeout);
		}
		return error;
	}

	private static Map<String, String> parseHeaders(CharSequence rawHeaders) {
		var headers = new LinkedHashMap<String, String>();
		String raw = rawHeaders.toString();
		for (String line : raw.split("\\r?\\n")) {
			int colon = line.indexOf(':');
			if (colon <= 0) continue;
			String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
			String value = line.substring(colon + 1).trim();
			if (!name.isEmpty()) headers.merge(name, value, (first, second) -> first + ", " + second);
		}
		return Map.copyOf(headers);
	}

	private static final class ProjectResponse implements TransportResponse {
		private final int status;
		private final Map<String, String> headers;
		private final HttpConnection connection;
		private final CompletableFuture<byte[]> body = new CompletableFuture<>();
		private final AtomicBoolean closed = new AtomicBoolean();

		private ProjectResponse(int status, Map<String, String> headers, HttpConnection connection) {
			this.status = status;
			this.headers = Map.copyOf(headers);
			this.connection = connection;
		}

		@Override
		public int status() {
			return status;
		}

		@Override
		public Map<String, String> headers() {
			return headers;
		}

		@Override
		public InputStream body() throws IOException {
			try {
				return new ByteArrayInputStream(body.get());
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while reading HTTP body", ex);
			} catch (ExecutionException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof IOException io) throw io;
				throw new IOException("Failed to read HTTP body", cause);
			}
		}

		@Override
		public void close() {
			if (!closed.compareAndSet(false, true)) return;
			body.completeExceptionally(new IOException("HTTP response closed"));
			connection.close();
		}

		private void completeBody(byte[] bytes) {
			if (!closed.get()) body.complete(bytes.clone());
		}

		private void failBody(Throwable error) {
			body.completeExceptionally(error);
		}
	}
}
