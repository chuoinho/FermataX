package me.aap.fermata.addon.stremio.net.http;

import static me.aap.fermata.addon.stremio.net.http.HttpFailure.Code.BODY_TIMEOUT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.addon.stremio.net.AddressResolver;
import me.aap.fermata.addon.stremio.net.NetworkLimits;
import me.aap.fermata.addon.stremio.net.NetworkPolicy;
import me.aap.fermata.addon.stremio.net.RedirectPolicy;
import me.aap.fermata.addon.stremio.net.ValidatedEndpoint;

/** Executes bounded GET requests while keeping network policy outside the transport. */
public final class StremioHttpClient {
	private final AddressResolver resolver;
	private final HttpTransport transport;
	private final ScheduledExecutorService scheduler;
	private final Executor bodyExecutor;
	private final HttpConcurrencyGate concurrency;

	public StremioHttpClient(AddressResolver resolver, HttpTransport transport,
			ScheduledExecutorService scheduler, Executor bodyExecutor) {
		this(resolver, transport, scheduler, bodyExecutor, new HttpConcurrencyGate(
				NetworkLimits.MAX_GLOBAL_JSON_CONCURRENCY,
				NetworkLimits.MAX_PER_HOST_JSON_CONCURRENCY));
	}

	StremioHttpClient(AddressResolver resolver, HttpTransport transport,
			ScheduledExecutorService scheduler, Executor bodyExecutor,
			HttpConcurrencyGate concurrency) {
		this.resolver = Objects.requireNonNull(resolver, "resolver");
		this.transport = Objects.requireNonNull(transport, "transport");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		this.bodyExecutor = Objects.requireNonNull(bodyExecutor, "bodyExecutor");
		this.concurrency = Objects.requireNonNull(concurrency, "concurrency");
	}

	public HttpCall execute(HttpRequestSpec request) {
		Objects.requireNonNull(request, "request");
		var call = new ClientCall(request);
		call.start();
		return call;
	}

	private final class ClientCall implements HttpCall {
		private final HttpRequestSpec request;
		private final CompletableFuture<HttpResponseData> result = new CompletableFuture<>();
		private final AtomicBoolean terminal = new AtomicBoolean();
		private final AtomicReference<TransportCall> activeCall = new AtomicReference<>();
		private final AtomicReference<TransportResponse> activeResponse = new AtomicReference<>();
		private final AtomicReference<HttpConcurrencyGate.Permit> permit = new AtomicReference<>();
		private final AtomicReference<CompletableFuture<?>> pendingAdmission = new AtomicReference<>();
		private volatile ScheduledFuture<?> callTimer;

		private ClientCall(HttpRequestSpec request) {
			this.request = request;
		}

		private void start() {
			callTimer = schedule(request.deadlines().call(), HttpFailure.Code.CALL_TIMEOUT,
					"HTTP call deadline exceeded");
			try {
				bodyExecutor.execute(this::startOnIoExecutor);
			} catch (Throwable ex) {
				fail(asFailure(ex));
			}
		}

		private void startOnIoExecutor() {
			if (terminal.get()) return;
			try {
				checkGeneration();
				ValidatedEndpoint endpoint = NetworkPolicy.validate(
						request.uri(), request.consent(), resolver);
				acquire(endpoint, request.headers());
			} catch (Throwable ex) {
				fail(asFailure(ex));
			}
		}

		private void acquire(ValidatedEndpoint endpoint, Map<String, String> headers) {
			CompletableFuture<HttpConcurrencyGate.Permit> admission =
					concurrency.acquire(endpoint.endpoint().host());
			pendingAdmission.set(admission);
			admission.whenComplete((acquired, error) -> {
				pendingAdmission.compareAndSet(admission, null);
				if (error != null) {
					if (!terminal.get()) fail(asFailure(unwrap(error)));
					return;
				}
				if (!permit.compareAndSet(null, acquired)) {
					acquired.close();
					return;
				}
				if (terminal.get()) {
					releasePermit();
					return;
				}
				execute(endpoint, headers, 0);
			});
		}

		private void execute(ValidatedEndpoint endpoint, Map<String, String> headers, int redirects) {
			if (terminal.get()) return;
			try {
				checkGeneration();
				TransportCall transportCall = transport.execute(new TransportRequest(
						endpoint, headers, request.deadlines(), request.maxBodyBytes()));
				activeCall.set(transportCall);
				ScheduledFuture<?> headerTimer = schedule(request.deadlines().headers(),
						HttpFailure.Code.HEADER_TIMEOUT, "HTTP response header deadline exceeded");
				transportCall.response().whenComplete((response, error) -> {
					headerTimer.cancel(false);
					if (terminal.get()) {
						close(response);
						return;
					}
					if (error != null) {
						fail(asFailure(unwrap(error)));
						return;
					}
					activeResponse.set(response);
					handleResponse(endpoint, headers, redirects, response);
				});
			} catch (Throwable ex) {
				fail(asFailure(ex));
			}
		}

		private void handleResponse(ValidatedEndpoint endpoint, Map<String, String> headers,
				int redirects, TransportResponse response) {
			try {
				checkGeneration();
				Map<String, String> responseHeaders = normalizeHeaders(response.headers());
				if (isRedirect(response.status())) {
					String location = responseHeaders.get("location");
					if ((location == null) || location.isBlank()) {
						throw new HttpFailure(HttpFailure.Code.INVALID_REDIRECT,
								"Redirect response is missing Location");
					}
					var decision = RedirectPolicy.follow(endpoint, URI.create(location), redirects,
							request.consent(), resolver, headers);
					close(response);
					activeResponse.compareAndSet(response, null);
					moveToRedirect(decision.target(), decision.requestHeaders(), redirects + 1);
					return;
				}
				if ((response.status() != 304) && ((response.status() < 200) || (response.status() >= 300))) {
					throw new HttpFailure(HttpFailure.Code.HTTP_STATUS,
							"Unexpected HTTP status: " + response.status());
				}
				validateContentLength(responseHeaders.get("content-length"));
				readBody(endpoint.endpoint().uri(), response, responseHeaders);
			} catch (Throwable ex) {
				close(response);
				activeResponse.compareAndSet(response, null);
				fail(asFailure(ex));
			}
		}

		private void moveToRedirect(ValidatedEndpoint endpoint, Map<String, String> headers,
				int redirects) {
			HttpConcurrencyGate.Permit acquired = permit.get();
			if (acquired == null) {
				fail(new HttpFailure(HttpFailure.Code.TRANSPORT,
						"HTTP redirect lost concurrency ownership"));
				return;
			}
			CompletableFuture<Void> admission = acquired.moveTo(endpoint.endpoint().host());
			pendingAdmission.set(admission);
			admission.whenComplete((ignored, error) -> {
				pendingAdmission.compareAndSet(admission, null);
				if (terminal.get()) return;
				if (error != null) fail(asFailure(unwrap(error)));
				else execute(endpoint, headers, redirects);
			});
		}

		private void readBody(URI finalUri, TransportResponse response,
				Map<String, String> responseHeaders) {
			ScheduledFuture<?> bodyTimer = schedule(request.deadlines().body(), BODY_TIMEOUT,
					"HTTP response body deadline exceeded");
			bodyExecutor.execute(() -> {
				try (response; InputStream in = response.body()) {
					byte[] body = readBounded(in, request.maxBodyBytes());
					checkGeneration();
					bodyTimer.cancel(false);
					activeResponse.compareAndSet(response, null);
					complete(new HttpResponseData(response.status(), finalUri, responseHeaders, body));
				} catch (Throwable ex) {
					bodyTimer.cancel(false);
					activeResponse.compareAndSet(response, null);
					fail(asFailure(ex));
				}
			});
		}

		private void validateContentLength(String value) throws HttpFailure {
			if (value == null) return;
			try {
				long length = Long.parseLong(value.trim());
				if (length > request.maxBodyBytes()) {
					throw new HttpFailure(HttpFailure.Code.BODY_TOO_LARGE,
							"HTTP body exceeds configured limit");
				}
			} catch (NumberFormatException ex) {
				throw new HttpFailure(HttpFailure.Code.TRANSPORT, "Invalid Content-Length", ex);
			}
		}

		private void checkGeneration() throws HttpFailure {
			if (!request.isCurrent()) {
				throw new HttpFailure(HttpFailure.Code.CANCELLED, "Stale request generation");
			}
		}

		private ScheduledFuture<?> schedule(java.time.Duration duration,
				HttpFailure.Code code, String message) {
			return scheduler.schedule(() -> fail(new HttpFailure(code, message)),
					duration.toMillis(), TimeUnit.MILLISECONDS);
		}

		private void complete(HttpResponseData response) {
			if (!terminal.compareAndSet(false, true)) return;
			cancelTimer();
			activeCall.set(null);
			activeResponse.set(null);
			releasePermit();
			result.complete(response);
		}

		private void fail(HttpFailure failure) {
			if (!terminal.compareAndSet(false, true)) return;
			cancelTimer();
			CompletableFuture<?> admission = pendingAdmission.getAndSet(null);
			if (admission != null) admission.cancel(false);
			TransportCall call = activeCall.getAndSet(null);
			if (call != null) call.cancel();
			close(activeResponse.getAndSet(null));
			releasePermit();
			result.completeExceptionally(failure);
		}

		private void releasePermit() {
			close(permit.getAndSet(null));
		}

		private void cancelTimer() {
			ScheduledFuture<?> timer = callTimer;
			if (timer != null) timer.cancel(false);
		}

		@Override
		public CompletableFuture<HttpResponseData> response() {
			return result;
		}

		@Override
		public void cancel() {
			fail(new HttpFailure(HttpFailure.Code.CANCELLED, "HTTP call cancelled"));
		}
	}

	private static byte[] readBounded(InputStream in, long maxBytes) throws IOException {
		var out = new ByteArrayOutputStream((int) Math.min(maxBytes, 16 * 1024));
		byte[] buffer = new byte[8192];
		long count = 0;
		for (int read; (read = in.read(buffer)) != -1; ) {
			count += read;
			if (count > maxBytes) {
				throw new HttpFailure(HttpFailure.Code.BODY_TOO_LARGE,
						"HTTP body exceeds configured limit");
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
		var normalized = new LinkedHashMap<String, String>(headers.size());
		headers.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
		return Map.copyOf(normalized);
	}

	private static boolean isRedirect(int status) {
		return (status == 301) || (status == 302) || (status == 303) ||
				(status == 307) || (status == 308);
	}

	private static HttpFailure asFailure(Throwable error) {
		if (error instanceof HttpFailure failure) return failure;
		if (error instanceof java.util.concurrent.CancellationException) {
			return new HttpFailure(HttpFailure.Code.CANCELLED, "HTTP call cancelled", error);
		}
		return new HttpFailure(HttpFailure.Code.TRANSPORT, "HTTP transport failed", error);
	}

	private static Throwable unwrap(Throwable error) {
		if ((error instanceof java.util.concurrent.CompletionException) && (error.getCause() != null)) {
			return error.getCause();
		}
		return error;
	}

	private static void close(AutoCloseable closeable) {
		if (closeable == null) return;
		try {
			closeable.close();
		} catch (Exception ignored) {
			// The terminal result is determined by the request, not a best-effort close.
		}
	}
}
