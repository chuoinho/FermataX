package me.aap.fermata.addon.stremio.integration;

import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.CANCELLED;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.INVALID_SOURCE;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.SECRET_UNAVAILABLE;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.SOURCE_CHANGED;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.SOURCE_DISABLED;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.SOURCE_NOT_FOUND;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.UNSUPPORTED_CAPABILITY;
import static me.aap.fermata.addon.stremio.integration.StremioProtocolFailureMapper.failure;
import static me.aap.fermata.addon.stremio.integration.StremioProtocolFailureMapper.map;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.lifecycle.StremioCall;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.net.cache.CacheKey;
import me.aap.fermata.addon.stremio.net.cache.CachedCall;
import me.aap.fermata.addon.stremio.net.cache.CachedResponse;
import me.aap.fermata.addon.stremio.protocol.CapabilityMatcher;
import me.aap.fermata.addon.stremio.protocol.ManifestValidator;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;
import me.aap.fermata.addon.stremio.runtime.StremioRuntime;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.diagnostics.DiagnosticOperation;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.utils.log.Log;

/** Secret-owning bridge from enabled source records to bounded cached protocol requests. */
public final class StremioProtocolClient implements AutoCloseable {
	private static final Map<String, String> JSON_HEADERS = Map.of("accept", "application/json");

	private final StremioRuntimeAccess runtime;
	private final StremioProviderSecretAccess secrets;
	private final Executor executor;
	private final ConcurrentHashMap<ProtocolCallImpl, Boolean> active = new ConcurrentHashMap<>();
	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicReference<me.aap.fermata.addon.stremio.source.StremioSourceSnapshot>
			currentSources = new AtomicReference<>();
	private final AutoCloseable sourceObserver;

	public StremioProtocolClient(StremioRuntime runtime,
			StremioProviderSecretAccess secrets, Executor executor,
			ScheduledExecutorService scheduler) {
		this(StremioRuntimeAccess.from(runtime), secrets, executor, scheduler);
	}

	StremioProtocolClient(StremioRuntimeAccess runtime,
			StremioProviderSecretAccess secrets, Executor executor,
			ScheduledExecutorService scheduler) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.secrets = Objects.requireNonNull(secrets, "secrets");
		this.executor = Objects.requireNonNull(executor, "executor");
		Objects.requireNonNull(scheduler, "scheduler");
		sourceObserver = runtime.observeSources(this::sourcesChanged);
	}

	public ProtocolCall fetch(String sourceUuid, String expectedAddonId,
			StremioRequest request, RequestGeneration.Token generation) {
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(expectedAddonId, "expectedAddonId");
		Objects.requireNonNull(request, "request");
		ProtocolCallImpl call = new ProtocolCallImpl(generation, request);
		if (closed.get()) {
			call.fail(failure(CANCELLED));
			return call;
		}
		active.put(call, Boolean.TRUE);
		call.result.whenComplete((value, error) -> active.remove(call));
		try {
			executor.execute(() -> begin(call, sourceUuid, expectedAddonId, request));
		} catch (RuntimeException error) {
			call.fail(failure(CANCELLED));
		}
		return call;
	}

	private void begin(ProtocolCallImpl call, String sourceUuid, String expectedAddonId,
			StremioRequest request) {
		if (!call.isActive()) return;
		CompletionStage<me.aap.fermata.addon.stremio.source.StremioSourceSnapshot> stage;
		try {
			stage = runtime.sources();
		} catch (Throwable error) {
			call.fail(map(error));
			return;
		}
		stage.whenCompleteAsync((snapshot, error) -> {
			if (error != null) {
				call.fail(map(error));
				return;
			}
			if (!call.isActive()) return;
			currentSources.set(snapshot);
			StremioSourceRecord source = snapshot.source(sourceUuid);
			if (source == null) {
				call.fail(failure(SOURCE_NOT_FOUND));
				return;
			}
			if (!source.enabled()) {
				call.fail(failure(SOURCE_DISABLED));
				return;
			}
			if (!source.addonId().equals(expectedAddonId)) {
				call.fail(failure(SOURCE_CHANGED));
				return;
			}
			final StremioManifest manifest;
			try {
				manifest = ManifestValidator.parse(source.manifestJson());
				if (!manifest.id().equals(source.addonId())) throw failure(INVALID_SOURCE);
				if (!CapabilityMatcher.supports(manifest, request)) {
					throw failure(UNSUPPORTED_CAPABILITY);
				}
			} catch (Throwable parseError) {
				call.fail((parseError instanceof StremioIntegrationException integration) ?
						integration : failure(INVALID_SOURCE));
				return;
			}
			loadSecret(call, source, snapshot.revision(), request);
		}, executor);
	}

	private void loadSecret(ProtocolCallImpl call, StremioSourceRecord source,
			long sourceRevision, StremioRequest request) {
		CompletionStage<StremioSourceSecret> loaded;
		try {
			loaded = secrets.load(source);
		} catch (Throwable error) {
			call.fail(failure(SECRET_UNAVAILABLE));
			return;
		}
		loaded.whenCompleteAsync((secret, error) -> {
			if (error != null || secret == null) {
				call.fail(failure(SECRET_UNAVAILABLE));
				return;
			}
			if (!call.isActive()) return;
			final StremioProtocolRequestPlanner.RequestPlan plan;
			final CacheKey cacheKey;
			final StremioProtocolRequestPolicy.Policy resourcePolicy;
			try {
				plan = StremioProtocolRequestPlanner.plan(secret.transportUrl(), request,
						source.transportFingerprint(), source.addonId(), source.updatedMs(),
						source.allowCleartext(), source.allowLan());
				resourcePolicy = StremioProtocolRequestPolicy.forResource(request.resource());
				cacheKey = CacheKey.derive(UUID.fromString(source.sourceUuid()),
						request.resource(), plan.cacheIdentity());
			} catch (Throwable invalid) {
				call.fail(failure(INVALID_SOURCE));
				return;
			}
			SourceBinding binding = new SourceBinding(sourceRevision, source);
			call.bind(binding);
			startTransport(call, cacheKey, plan.uri(), resourcePolicy, source.networkConsent(),
					binding);
		}, executor);
	}

	private void startTransport(ProtocolCallImpl call, CacheKey key, java.net.URI uri,
			StremioProtocolRequestPolicy.Policy resourcePolicy,
			me.aap.fermata.addon.stremio.net.NetworkConsent consent,
			SourceBinding binding) {
		CompletionStage<me.aap.fermata.addon.stremio.source.StremioSourceSnapshot> stage;
		try {
			stage = runtime.sources();
		} catch (Throwable error) {
			call.fail(map(error));
			return;
		}
		stage.whenCompleteAsync((snapshot, error) -> {
			if (error != null) call.fail(map(error));
			else currentSources.set(snapshot);
			if (error != null) return;
			if (!call.isActive()) call.cancel();
			else if (!binding.matches(snapshot)) call.fail(failure(SOURCE_CHANGED));
			else startValidatedTransport(call, key, uri, resourcePolicy, consent, binding);
		}, executor);
	}

	private void startValidatedTransport(ProtocolCallImpl call, CacheKey key, java.net.URI uri,
			StremioProtocolRequestPolicy.Policy resourcePolicy,
			me.aap.fermata.addon.stremio.net.NetworkConsent consent,
			SourceBinding binding) {
		if (!call.isActive()) return;
		final CachedCall transport;
		try {
			transport = runtime.fetch(key, uri, JSON_HEADERS,
					resourcePolicy.maxBodyBytes(), resourcePolicy.cachePolicy(), consent,
					resourcePolicy.deadlines(),
					call::isActive);
			call.transport.set(transport);
		} catch (Throwable error) {
			call.fail(map(error));
			return;
		}
		if (!call.isActive()) {
			transport.cancel();
			return;
		}
		transport.response().whenCompleteAsync((response, error) -> {
			if (error != null) call.fail(map(error));
			else if (!call.isActive()) call.cancel();
			else validateSource(call, binding, response);
		}, executor);
	}

	private void validateSource(ProtocolCallImpl call, SourceBinding binding,
			CachedResponse response) {
		CompletionStage<me.aap.fermata.addon.stremio.source.StremioSourceSnapshot> stage;
		try {
			stage = runtime.sources();
		} catch (Throwable error) {
			call.fail(map(error));
			return;
		}
		stage.whenCompleteAsync((snapshot, error) -> {
			if (error != null) call.fail(map(error));
			else currentSources.set(snapshot);
			if (error != null) return;
			if (!call.isActive()) call.cancel();
			else if (!binding.matches(snapshot)) call.fail(failure(SOURCE_CHANGED));
			else call.complete(new ProtocolPayload(response.body(), isStale(response),
					binding.lease(currentSources::get)));
		}, executor);
	}

	private static boolean isStale(CachedResponse response) {
		return response.origin() == CachedResponse.Origin.STALE_CACHE;
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		try {
			sourceObserver.close();
		} catch (Exception ignored) {
		}
		for (ProtocolCallImpl call : active.keySet()) call.cancel();
		active.clear();
	}

	private void sourcesChanged(
			me.aap.fermata.addon.stremio.source.StremioSourceSnapshot snapshot) {
		currentSources.set(snapshot);
		for (ProtocolCallImpl call : active.keySet()) {
			SourceBinding binding = call.binding;
			if ((binding != null) && !binding.matches(snapshot)) {
				call.fail(failure(SOURCE_CHANGED));
			}
		}
	}

	public interface ProtocolCall extends StremioCall<ProtocolPayload> {
		CompletableFuture<ProtocolPayload> response();

		@Override
		default CompletableFuture<ProtocolPayload> completion() {
			return response();
		}

		void cancel();
	}

	public record ProtocolPayload(
			byte[] body, boolean stale, StremioSourceLease sourceLease) {
		public ProtocolPayload {
			body = Objects.requireNonNull(body, "body").clone();
			Objects.requireNonNull(sourceLease, "sourceLease");
		}

		@Override
		public byte[] body() {
			return body.clone();
		}
	}

	private record SourceBinding(long revision, String sourceUuid, String addonId,
			String transportFingerprint, long updatedMs, boolean allowCleartext, boolean allowLan) {
		private SourceBinding(long revision, StremioSourceRecord source) {
			this(revision, source.sourceUuid(), source.addonId(), source.transportFingerprint(),
					source.updatedMs(), source.allowCleartext(), source.allowLan());
		}

		private boolean matches(
				me.aap.fermata.addon.stremio.source.StremioSourceSnapshot snapshot) {
			if (snapshot == null) return false;
			StremioSourceRecord current = snapshot.source(sourceUuid);
			return (current != null) && current.enabled() && addonId.equals(current.addonId()) &&
					transportFingerprint.equals(current.transportFingerprint()) &&
					(updatedMs == current.updatedMs()) &&
					(allowCleartext == current.allowCleartext()) && (allowLan == current.allowLan());
		}

		private StremioSourceLease lease(
				java.util.function.Supplier<
						me.aap.fermata.addon.stremio.source.StremioSourceSnapshot> current) {
			return StremioSourceLease.bound(revision, sourceUuid, addonId,
					transportFingerprint, updatedMs,
					new me.aap.fermata.addon.stremio.net.NetworkConsent(
							allowCleartext, allowLan), current);
		}
	}

	private final class ProtocolCallImpl implements ProtocolCall {
		private final CompletableFuture<ProtocolPayload> result = new CompletableFuture<>();
		private final AtomicReference<CachedCall> transport = new AtomicReference<>();
		private final AtomicBoolean terminal = new AtomicBoolean();
		private final RequestGeneration.Token generation;
		private final DiagnosticOperation diagnostics;
		private volatile SourceBinding binding;
		private AutoCloseable generationObservation = () -> {};

		private ProtocolCallImpl(RequestGeneration.Token generation, StremioRequest request) {
			this.generation = generation;
			Map<String, Object> attributes = new java.util.LinkedHashMap<>();
			attributes.put("generation", (generation == null) ? -1L : generation.value());
			attributes.put("request_type", request.type());
			diagnostics = AndroidDiagnosticsRuntime.get().begin("stremio_protocol",
				"protocol_request", attributes);
			if (generation != null) {
				AutoCloseable observation = generation.onInvalidated(this::cancel);
				generationObservation = observation;
				if (terminal.get()) closeObservation(observation);
			}
		}

		@Override
		public boolean isActive() {
			return !terminal.get() && !closed.get() &&
					(generation == null || generation.isCurrent());
		}

		private void bind(SourceBinding value) {
			binding = value;
		}

		private void complete(ProtocolPayload payload) {
			if (!finish()) return;
			if (diagnostics != null) diagnostics.complete(Map.of(
					"status", "completed",
					"byte_count", payload.body().length,
					"stale", payload.stale()));
			result.complete(payload);
		}

		private void fail(Throwable error) {
			if (!finish()) return;
			CachedCall call = transport.getAndSet(null);
			if (call != null) call.cancel();
			Map<String, Object> attributes = new java.util.LinkedHashMap<>();
			attributes.put("status", isCancelled(error) ? "cancelled" : "failed");
			attributes.put("byte_count", 0);
			attributes.put("failure_code", failureCode(error));
			if (diagnostics != null) {
				if (isCancelled(error)) diagnostics.cancel(attributes);
				else diagnostics.fail(error, attributes);
			}
			result.completeExceptionally(error);
		}

		private boolean finish() {
			if (!terminal.compareAndSet(false, true)) return false;
			closeObservation(generationObservation);
			return true;
		}

		private void closeObservation(AutoCloseable observation) {
			try {
				observation.close();
			} catch (Exception error) {
				Log.e("Stremio protocol observer cleanup failed: ",
						error.getClass().getName());
			}
		}

		@Override
		public CompletableFuture<ProtocolPayload> response() {
			return result;
		}

		@Override
		public void cancel() {
			if (!finish()) return;
			CachedCall call = transport.get();
			if (call != null) call.cancel();
			if (diagnostics != null) diagnostics.cancel(Map.of(
					"status", "cancelled", "byte_count", 0));
			result.completeExceptionally(failure(CANCELLED));
		}
	}

	private static boolean isCancelled(Throwable error) {
		while ((error instanceof java.util.concurrent.CompletionException) &&
				(error.getCause() != null)) error = error.getCause();
		return (error instanceof java.util.concurrent.CancellationException) ||
				((error instanceof StremioIntegrationException integration) &&
						(integration.code() == CANCELLED));
	}

	private static String failureCode(Throwable error) {
		while ((error instanceof java.util.concurrent.CompletionException) &&
				(error.getCause() != null)) error = error.getCause();
		if (error instanceof StremioIntegrationException integration) {
			return integration.code().name();
		}
		if (error instanceof java.util.concurrent.CancellationException) return CANCELLED.name();
		return (error == null) ? "UNKNOWN" : error.getClass().getSimpleName();
	}
}
