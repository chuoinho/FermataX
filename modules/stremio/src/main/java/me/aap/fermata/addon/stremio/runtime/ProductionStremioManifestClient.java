package me.aap.fermata.addon.stremio.runtime;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.stremio.net.EndpointNormalizer;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.NetworkLimits;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.net.http.HttpCall;
import me.aap.fermata.addon.stremio.net.http.HttpFailure;
import me.aap.fermata.addon.stremio.net.http.HttpRequestSpec;
import me.aap.fermata.addon.stremio.net.http.StremioHttpClient;
import me.aap.fermata.addon.stremio.source.StremioManifestClient;
import me.aap.fermata.addon.stremio.source.StremioSourceException;
import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;
import me.aap.fermata.addon.stremio.security.HttpValidatorPolicy;
import me.aap.utils.log.Log;

/** Production manifest adapter with bounded requests and lifecycle cancellation. */
public final class ProductionStremioManifestClient implements StremioManifestClient, AutoCloseable {
	private static final long ACTIVE_POLL_MS = 50;
	private final StremioHttpClient http;
	private final NetworkConsent consent;
	private final ScheduledExecutorService scheduler;
	private final RequestGeneration.Token lifecycle;
	private final Set<PendingCall> activeCalls = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean closed = new AtomicBoolean();
	private AutoCloseable lifecycleObservation = () -> {};

	public ProductionStremioManifestClient(StremioHttpClient http, NetworkConsent consent,
			ScheduledExecutorService scheduler, RequestGeneration.Token lifecycle) {
		this.http = Objects.requireNonNull(http, "http");
		this.consent = Objects.requireNonNull(consent, "consent");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
		AutoCloseable observation = lifecycle.onInvalidated(this::close);
		lifecycleObservation = observation;
		if (closed.get()) closeObservation(observation);
	}

	@Override
	public CompletableFuture<Response> fetch(Request request) {
		Objects.requireNonNull(request, "request");
		if (closed.get() || !lifecycle.isCurrent() || !isActive(request)) {
			return failed(Code.CANCELLED);
		}

		final URI manifestUri;
		final Map<String, String> headers;
		try {
			manifestUri = normalizeManifestUri(request.secret().transportUrl());
			headers = requestHeaders(request);
		} catch (StremioSourceException ex) {
			return CompletableFuture.failedFuture(ex);
		}

		HttpRequestSpec spec = new HttpRequestSpec(manifestUri, headers,
				NetworkLimits.MAX_MANIFEST_BODY_BYTES, request.consent(),
				me.aap.fermata.addon.stremio.net.http.HttpDeadlines.DEFAULT, lifecycle);
		HttpCall call = http.execute(spec);
		PendingCall pending = new PendingCall(call, request);
		activeCalls.add(pending);
		pending.start();
		return pending.result;
	}

	static URI normalizeManifestUri(String value) {
		if ((value == null) || value.isBlank()) throw failure(Code.INVALID_TRANSPORT);
		final URI input;
		try {
			input = URI.create(value.trim());
		} catch (IllegalArgumentException ex) {
			throw failure(Code.INVALID_TRANSPORT, ex);
		}

		String scheme = input.getScheme();
		if (scheme == null) throw failure(Code.INVALID_TRANSPORT);
		URI normalized;
		if (scheme.equalsIgnoreCase("stremio")) {
			try {
				String raw = input.toASCIIString();
				normalized = URI.create("https" + raw.substring(raw.indexOf(':')));
			} catch (RuntimeException ex) {
				throw failure(Code.INVALID_TRANSPORT, ex);
			}
		} else if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
			normalized = input;
		} else {
			throw failure(Code.INVALID_TRANSPORT);
		}

		try {
			normalized = EndpointNormalizer.normalize(normalized).uri();
		} catch (Exception ex) {
			throw failure(Code.INVALID_TRANSPORT, ex);
		}
		String path = normalized.getRawPath();
		if ((path == null) || !path.endsWith("/manifest.json")) {
			throw failure(Code.INVALID_TRANSPORT);
		}
		return normalized;
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		closeObservation(lifecycleObservation);
		for (PendingCall call : activeCalls) call.cancel();
		activeCalls.clear();
	}

	int activeCallCount() {
		return activeCalls.size();
	}

	private static Map<String, String> requestHeaders(Request request) {
		var headers = new LinkedHashMap<String, String>();
		headers.put("accept", "application/json");
		putConditional(headers, "if-none-match", request.etag());
		putConditional(headers, "if-modified-since", request.lastModified());
		return Map.copyOf(headers);
	}

	private static void putConditional(Map<String, String> headers, String name, String value) {
		if (value == null) return;
		if ((value.length() > HttpValidatorPolicy.MAX_CHARS) ||
				value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
			throw failure(Code.TRANSPORT);
		}
		headers.put(name, value);
	}

	private static String decodeUtf8(byte[] body) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(body)).toString();
		} catch (CharacterCodingException ex) {
			throw failure(Code.INVALID_MANIFEST, ex);
		}
	}

	private static CompletableFuture<Response> failed(Code code) {
		return CompletableFuture.failedFuture(failure(code));
	}

	private static StremioSourceException failure(Code code) {
		return new StremioSourceException(code);
	}

	private static StremioSourceException failure(Code code, Throwable cause) {
		return new StremioSourceException(code, cause);
	}

	private static Throwable unwrap(Throwable error) {
		while ((error instanceof CompletionException) && (error.getCause() != null)) {
			error = error.getCause();
		}
		return error;
	}

	private static StremioSourceException mapFailure(Throwable error) {
		error = unwrap(error);
		if (error instanceof StremioSourceException source) return source;
		if ((error instanceof HttpFailure httpFailure) &&
				(httpFailure.code() == HttpFailure.Code.CANCELLED)) {
			return failure(Code.CANCELLED, error);
		}
		return failure(Code.TRANSPORT, error);
	}

	private static boolean isActive(Request request) {
		try {
			return request.isActive();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private final class PendingCall {
		private final HttpCall call;
		private final Request request;
		private final CompletableFuture<Response> result = new CompletableFuture<>();
		private final AtomicBoolean terminal = new AtomicBoolean();
		private volatile ScheduledFuture<?> monitor;
		private AutoCloseable requestObservation = () -> {};

		private PendingCall(HttpCall call, Request request) {
			this.call = call;
			this.request = request;
		}

		private void start() {
			try {
				AutoCloseable observation = request.onInvalidated(this::cancel);
				requestObservation = observation;
				if (terminal.get()) closeObservation(observation);
				if (!request.hasInvalidationSignal()) {
					monitor = scheduler.scheduleWithFixedDelay(() -> {
						if (closed.get() || !lifecycle.isCurrent() || !isActive(request)) cancel();
					}, ACTIVE_POLL_MS, ACTIVE_POLL_MS, TimeUnit.MILLISECONDS);
				}
			} catch (Throwable error) {
				cancel();
				return;
			}
			result.whenComplete((response, error) -> {
				if (result.isCancelled()) call.cancel();
			});
			call.response().whenComplete((response, error) -> {
				if (error != null) {
					completeExceptionally(mapFailure(error));
					return;
				}
				if (closed.get() || !lifecycle.isCurrent() || !isActive(request)) {
					completeExceptionally(failure(Code.CANCELLED));
					return;
				}
				try {
					Response value = (response.status() == 304) ?
							Response.notModified(response.header("etag"),
									response.header("last-modified")) :
							Response.modified(decodeUtf8(response.body()),
									response.header("etag"), response.header("last-modified"));
					complete(value);
				} catch (Throwable ex) {
					completeExceptionally((ex instanceof StremioSourceException source) ?
							source : failure(Code.INVALID_MANIFEST, ex));
				}
			});
		}

		private void cancel() {
			call.cancel();
			completeExceptionally(failure(Code.CANCELLED));
		}

		private void complete(Response response) {
			if (!finish()) return;
			result.complete(response);
		}

		private void completeExceptionally(Throwable error) {
			if (!finish()) return;
			result.completeExceptionally(error);
		}

		private boolean finish() {
			if (!terminal.compareAndSet(false, true)) return false;
			ScheduledFuture<?> task = monitor;
			if (task != null) task.cancel(false);
			closeObservation(requestObservation);
			activeCalls.remove(this);
			return true;
		}
	}

	private static void closeObservation(AutoCloseable observation) {
		try {
			observation.close();
		} catch (Exception error) {
			Log.e("Stremio manifest observer cleanup failed: ",
					error.getClass().getName());
		}
	}
}
