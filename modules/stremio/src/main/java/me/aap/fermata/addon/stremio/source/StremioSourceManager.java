package me.aap.fermata.addon.stremio.source;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.model.source.TransportFingerprint;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.protocol.ManifestValidationException;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;
import me.aap.fermata.addon.stremio.security.SecretTaintDetector;
import me.aap.fermata.addon.stremio.security.HttpValidatorPolicy;
import me.aap.fermata.addon.stremio.security.SecureStorageUnavailableException;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.addon.stremio.security.StremioUrlRedactor;
import me.aap.fermata.addon.stremio.source.StremioManifestClient.Request;
import me.aap.fermata.addon.stremio.source.StremioManifestClient.Response;
import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Action;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Status;

/** Async, UI-independent owner of all Stremio source mutations. */
public final class StremioSourceManager implements AutoCloseable {
	private static final String ADD_GENERATION = "@add";
	private static final String DEFAULT_GENERATION = "@cinemeta";
	private static final String ORDER_GENERATION = "@order";

	private final StremioSourceStore store;
	private final StremioSourceSecretVault secretVault;
	private final StremioManifestClient manifestClient;
	private final StremioManifestParser manifestParser;
	private final LongSupplier clock;
	private final Supplier<String> sourceUuidFactory;
	private final Supplier<String> secretUuidFactory;
	private final Map<String, RequestGeneration> generations = new HashMap<>();
	private final CopyOnWriteArrayList<Consumer<StremioSourceSnapshot>> observers =
			new CopyOnWriteArrayList<>();
	private final Object mutationLock = new Object();
	private final AtomicBoolean closed = new AtomicBoolean();
	private CompletableFuture<Void> mutationTail = CompletableFuture.completedFuture(null);

	public StremioSourceManager(StremioSourceStore store,
			StremioSourceSecretVault secretVault,
			StremioManifestClient manifestClient,
			StremioManifestParser manifestParser) {
		this(store, secretVault, manifestClient, manifestParser,
				System::currentTimeMillis, () -> UUID.randomUUID().toString(),
				() -> UUID.randomUUID().toString());
	}

	StremioSourceManager(StremioSourceStore store,
			StremioSourceSecretVault secretVault,
			StremioManifestClient manifestClient,
			StremioManifestParser manifestParser,
			LongSupplier clock,
			Supplier<String> sourceUuidFactory) {
		this(store, secretVault, manifestClient, manifestParser, clock, sourceUuidFactory,
				() -> UUID.randomUUID().toString());
	}

	StremioSourceManager(StremioSourceStore store,
			StremioSourceSecretVault secretVault,
			StremioManifestClient manifestClient,
			StremioManifestParser manifestParser,
			LongSupplier clock,
			Supplier<String> sourceUuidFactory,
			Supplier<String> secretUuidFactory) {
		this.store = Objects.requireNonNull(store, "store");
		this.secretVault = Objects.requireNonNull(secretVault, "secretVault");
		this.manifestClient = Objects.requireNonNull(manifestClient, "manifestClient");
		this.manifestParser = Objects.requireNonNull(manifestParser, "manifestParser");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.sourceUuidFactory = Objects.requireNonNull(sourceUuidFactory, "sourceUuidFactory");
		this.secretUuidFactory = Objects.requireNonNull(secretUuidFactory, "secretUuidFactory");
	}

	public CompletableFuture<StremioSourceSnapshot> sources() {
		if (closed.get()) return StremioFutures.failedFuture(
				new StremioSourceException(Code.CLOSED));
		return loadState();
	}

	/** Observers receive only fully committed snapshots, never intermediate transaction state. */
	public AutoCloseable observe(Consumer<StremioSourceSnapshot> observer) {
		Objects.requireNonNull(observer, "observer");
		if (closed.get()) throw new StremioSourceException(Code.CLOSED);
		observers.add(observer);
		return () -> observers.remove(observer);
	}

	public CompletableFuture<StremioSourceOutcome> add(StremioSourceInput input) {
		Objects.requireNonNull(input, "input");
		Action action = Action.ADD;
		RequestGeneration.Token token = begin(ADD_GENERATION);
		String sourceUuid;
		try {
			sourceUuid = canonicalUuid(sourceUuidFactory.get());
		} catch (RuntimeException failure) {
			return completedFailure(action, null, failure);
		}
		StremioSourceSecret secret = input.secret();
		return prepare(secret, input.networkConsent(), null, null, null, token)
				.thenCompose(prepared -> mutate(() -> addPrepared(action, sourceUuid,
						secret, prepared, token, false)))
				.handle((outcome, failure) -> finish(action, sourceUuid, outcome, failure));
	}

	public CompletableFuture<StremioSourceOutcome> edit(
			String sourceUuid, StremioSourceInput input) {
		Objects.requireNonNull(input, "input");
		sourceUuid = canonicalUuid(sourceUuid);
		Action action = Action.EDIT;
		RequestGeneration.Token token = begin(sourceUuid);
		StremioSourceSecret replacementSecret = input.secret();
		String id = sourceUuid;
		return prepare(replacementSecret, input.networkConsent(), null, null, null, token)
				.thenCompose(prepared -> mutate(() -> editPrepared(id, replacementSecret,
						prepared, token)))
				.handle((outcome, failure) -> finish(action, id, outcome, failure));
	}

	public CompletableFuture<StremioSourceOutcome> enable(String sourceUuid) {
		return setEnabled(sourceUuid, true, Action.ENABLE);
	}

	public CompletableFuture<StremioSourceOutcome> disable(String sourceUuid) {
		return setEnabled(sourceUuid, false, Action.DISABLE);
	}

	public CompletableFuture<StremioSourceOutcome> refresh(String sourceUuid) {
		sourceUuid = canonicalUuid(sourceUuid);
		Action action = Action.REFRESH;
		RequestGeneration.Token token = begin(sourceUuid);
		String id = sourceUuid;
		CompletableFuture<PreparedManifest> preparation = loadState().thenCompose(snapshot -> {
			StremioSourceRecord current = requireSource(snapshot, id);
			return loadSecret(current).thenCompose(secret -> {
				if (secret == null) return StremioFutures.failedFuture(
						new StremioSourceException(Code.SECURE_STORAGE));
				return prepare(secret, current.networkConsent(), current.manifestJson(), current.manifestEtag(),
						current.manifestLastModified(), token);
			});
		});

		return preparation.handle(FetchAttempt::new).thenCompose(attempt -> {
			if (attempt.failure == null) {
				return mutate(() -> refreshPrepared(id, attempt.prepared, token));
			}
			Throwable failure = unwrap(attempt.failure);
			if (isCancelled(failure) || !token.isCurrent()) {
				return CompletableFuture.completedFuture(cancelled(action, id));
			}
			Code code = codeOf(failure);
			if ((code != Code.TRANSPORT) && (code != Code.INVALID_MANIFEST) &&
					(code != Code.SECRET_TAINT)) {
				return CompletableFuture.completedFuture(failed(action, id, null, code));
			}
			return mutate(() -> recordRefreshFailure(id, token, code));
		}).handle((outcome, failure) -> finish(action, id, outcome, failure));
	}

	public CompletableFuture<StremioSourceOutcome> remove(String sourceUuid) {
		sourceUuid = canonicalUuid(sourceUuid);
		Action action = Action.REMOVE;
		RequestGeneration.Token token = begin(sourceUuid);
		String id = sourceUuid;
		return mutate(() -> loadState().thenCompose(before -> {
			token.throwIfStale();
			StremioSourceRecord source = requireSource(before, id);
			return loadSecret(source).thenCompose(oldSecret -> {
				if (oldSecret == null) return StremioFutures.failedFuture(
						new StremioSourceException(Code.SECURE_STORAGE));
				List<StremioSourceRecord> sources = new ArrayList<>(before.sources());
				sources.remove(source.position());
				sources = normalizePositions(sources, clock.getAsLong());
				StremioSourceSnapshot after = before.next(sources,
						before.cinemetaInstallHandled());
				return commit(before, after).thenCompose(ignored ->
						removeObsoleteSecret(secretId(source)))
						.thenApply(ignored -> changed(action, id, after));
			});
		})).handle((outcome, failure) -> finish(action, id, outcome, failure));
	}

	public CompletableFuture<StremioSourceOutcome> reorder(List<String> orderedSourceUuids) {
		List<String> requested = List.copyOf(Objects.requireNonNull(
				orderedSourceUuids, "orderedSourceUuids"));
		Action action = Action.REORDER;
		RequestGeneration.Token token = begin(ORDER_GENERATION);
		return mutate(() -> loadState().thenCompose(before -> {
			token.throwIfStale();
			if (!sameIds(before.sources(), requested)) {
				return StremioFutures.failedFuture(
						new StremioSourceException(Code.INVALID_ORDER));
			}
			boolean same = true;
			Map<String, StremioSourceRecord> byId = new HashMap<>();
			for (StremioSourceRecord source : before.sources()) byId.put(source.sourceUuid(), source);
			List<StremioSourceRecord> reordered = new ArrayList<>(requested.size());
			long now = clock.getAsLong();
			for (int i = 0; i < requested.size(); i++) {
				StremioSourceRecord source = byId.get(requested.get(i));
				if (source.position() != i) same = false;
				reordered.add(withPosition(source, i, now));
			}
			if (same) return CompletableFuture.completedFuture(
					unchanged(action, null, before));
			StremioSourceSnapshot after = before.next(reordered,
					before.cinemetaInstallHandled());
			return commit(before, after).thenApply(ignored -> changed(action, null, after));
		})).handle((outcome, failure) -> finish(action, null, outcome, failure));
	}

	/**
	 * Marks upgraded installs as handled without installing a default. A true fresh install retries
	 * transient fetch failures, but once Cinemeta was installed then removed it is never restored.
	 */
	public CompletableFuture<StremioSourceOutcome> initializeCinemeta(
			boolean freshInstall, StremioSourceInput cinemeta) {
		Objects.requireNonNull(cinemeta, "cinemeta");
		Action action = Action.INITIALIZE_DEFAULT;
		RequestGeneration.Token token = begin(DEFAULT_GENERATION);
		return loadState().thenCompose(initial -> {
			if (initial.cinemetaInstallHandled()) {
				return CompletableFuture.completedFuture(unchanged(action, null, initial));
			}
			if (!freshInstall) return mutate(() -> markCinemetaHandled(token));
			StremioSourceSecret secret = cinemeta.secret();
			return prepare(secret, cinemeta.networkConsent(), null, null, null, token)
					.thenCompose(prepared -> mutate(() -> addPrepared(action,
							canonicalUuid(sourceUuidFactory.get()), secret, prepared, token, true)));
		}).handle((outcome, failure) -> finish(action, null, outcome, failure));
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		synchronized (generations) {
			for (RequestGeneration generation : generations.values()) generation.close();
			generations.clear();
		}
		observers.clear();
	}

	private CompletableFuture<StremioSourceOutcome> addPrepared(Action action, String sourceUuid,
			StremioSourceSecret secret, PreparedManifest prepared,
			RequestGeneration.Token token, boolean markCinemetaHandled) {
		return loadState().thenCompose(before -> {
			token.throwIfStale();
			if (markCinemetaHandled && before.cinemetaInstallHandled()) {
				return CompletableFuture.completedFuture(unchanged(action, null, before));
			}
			final String fingerprint;
			try {
				fingerprint = fingerprint(secret);
			} catch (RuntimeException failure) {
				return StremioFutures.failedFuture(
						new StremioSourceException(Code.INVALID_TRANSPORT, failure));
			}
			StremioSourceRecord duplicate = sourceWithFingerprint(before, fingerprint, null);
			if (duplicate != null) {
				if (!markCinemetaHandled) return StremioFutures.failedFuture(
						new StremioSourceException(Code.DUPLICATE_TRANSPORT));
				StremioSourceSnapshot after = before.next(before.sources(), true);
				return commit(before, after).thenApply(ignored -> changed(action,
						duplicate.sourceUuid(), after));
			}
			long now = clock.getAsLong();
			final StremioSourceRecord source;
			try {
				source = createSource(sourceUuid, fingerprint,
						StremioSecretReference.create(sourceUuid),
						prepared, before.sources().size(), now);
			} catch (RuntimeException failure) {
				return StremioFutures.failedFuture(
						new StremioSourceException(Code.PERSISTENCE, failure));
			}
			List<StremioSourceRecord> sources = new ArrayList<>(before.sources());
			sources.add(source);
			final StremioSourceSnapshot after;
			try {
				after = before.next(sources,
						markCinemetaHandled || before.cinemetaInstallHandled());
			} catch (RuntimeException failure) {
				return StremioFutures.failedFuture(
						new StremioSourceException(Code.PERSISTENCE, failure));
			}
			return stageThenCommit(token, sourceUuid, secret, null, before, after)
					.thenApply(ignored -> changed(action, sourceUuid, after));
		});
	}

	private CompletableFuture<StremioSourceOutcome> editPrepared(String sourceUuid,
			StremioSourceSecret replacementSecret, PreparedManifest prepared,
			RequestGeneration.Token token) {
		return loadState().thenCompose(before -> {
			token.throwIfStale();
			StremioSourceRecord current = requireSource(before, sourceUuid);
			String fingerprint = fingerprint(replacementSecret);
			if (sourceWithFingerprint(before, fingerprint, sourceUuid) != null) {
				return StremioFutures.failedFuture(
						new StremioSourceException(Code.DUPLICATE_TRANSPORT));
			}
			return loadSecret(current).thenCompose(oldSecret -> {
				if (oldSecret == null) return StremioFutures.failedFuture(
						new StremioSourceException(Code.SECURE_STORAGE));
				String replacementSecretId = canonicalUuid(secretUuidFactory.get());
				long now = clock.getAsLong();
			StremioSourceRecord edited = refreshed(current, fingerprint, replacementSecret,
						StremioSecretReference.create(replacementSecretId), prepared, now);
				List<StremioSourceRecord> sources = replace(before.sources(), edited);
				StremioSourceSnapshot after = before.next(sources,
						before.cinemetaInstallHandled());
				return stageThenCommit(token, replacementSecretId, replacementSecret,
						secretId(current), before, after)
						.thenApply(ignored -> changed(Action.EDIT, sourceUuid, after));
			});
		});
	}

	private CompletableFuture<StremioSourceOutcome> refreshPrepared(String sourceUuid,
			PreparedManifest prepared, RequestGeneration.Token token) {
		return loadState().thenCompose(before -> {
			token.throwIfStale();
			StremioSourceRecord current = requireSource(before, sourceUuid);
			long now = clock.getAsLong();
			StremioSourceRecord refreshed = refreshed(current,
					current.transportFingerprint(), null, current.secretRef(), prepared, now);
			StremioSourceSnapshot after = before.next(replace(before.sources(), refreshed),
					before.cinemetaInstallHandled());
			return commit(before, after).thenApply(ignored ->
					changed(Action.REFRESH, sourceUuid, after));
		});
	}

	private CompletableFuture<StremioSourceOutcome> recordRefreshFailure(String sourceUuid,
			RequestGeneration.Token token, Code code) {
		return loadState().thenCompose(before -> {
			token.throwIfStale();
			StremioSourceRecord current = requireSource(before, sourceUuid);
			long now = clock.getAsLong();
			StremioSourceRecord failed = new StremioSourceRecord(current.sourceUuid(),
					current.transportFingerprint(), current.addonId(), current.name(), current.version(),
					current.redactedTransportUrl(), current.secretRef(), current.enabled(),
					current.position(), current.manifestJson(), current.manifestEtag(),
					current.manifestLastModified(), now, current.lastSuccessMs(), code.name(),
					current.installedMs(), now, current.allowCleartext(), current.allowLan());
			StremioSourceSnapshot after = before.next(replace(before.sources(), failed),
					before.cinemetaInstallHandled());
			return commit(before, after).thenApply(ignored -> failed(
					Action.REFRESH, sourceUuid, after, code));
		});
	}

	private CompletableFuture<StremioSourceOutcome> setEnabled(
			String sourceUuid, boolean enabled, Action action) {
		sourceUuid = canonicalUuid(sourceUuid);
		RequestGeneration.Token token = begin(sourceUuid);
		String id = sourceUuid;
		return mutate(() -> loadState().thenCompose(before -> {
			token.throwIfStale();
			StremioSourceRecord current = requireSource(before, id);
			if (current.enabled() == enabled) {
				return CompletableFuture.completedFuture(unchanged(action, id, before));
			}
			long now = clock.getAsLong();
			StremioSourceRecord updated = new StremioSourceRecord(current.sourceUuid(),
					current.transportFingerprint(), current.addonId(), current.name(), current.version(),
					current.redactedTransportUrl(), current.secretRef(), enabled, current.position(),
					current.manifestJson(), current.manifestEtag(), current.manifestLastModified(),
					current.lastCheckedMs(), current.lastSuccessMs(), current.lastErrorCode(),
					current.installedMs(), now, current.allowCleartext(), current.allowLan());
			StremioSourceSnapshot after = before.next(replace(before.sources(), updated),
					before.cinemetaInstallHandled());
			return commit(before, after).thenApply(ignored -> changed(action, id, after));
		})).handle((outcome, failure) -> finish(action, id, outcome, failure));
	}

	private CompletableFuture<StremioSourceOutcome> markCinemetaHandled(
			RequestGeneration.Token token) {
		return loadState().thenCompose(before -> {
			token.throwIfStale();
			if (before.cinemetaInstallHandled()) return CompletableFuture.completedFuture(
					unchanged(Action.INITIALIZE_DEFAULT, null, before));
			StremioSourceSnapshot after = before.next(before.sources(), true);
			return commit(before, after).thenApply(ignored ->
					changed(Action.INITIALIZE_DEFAULT, null, after));
		});
	}

	private CompletableFuture<PreparedManifest> prepare(StremioSourceSecret secret,
			NetworkConsent consent,
			String existingJson, String etag, String lastModified,
			RequestGeneration.Token token) {
		final String redactedUrl;
		try {
			validateTransport(secret.transportUrl());
			redactedUrl = StremioUrlRedactor.forStorage(secret.transportUrl());
			if (redactedUrl == null) throw new StremioSourceException(Code.INVALID_TRANSPORT);
		} catch (StremioSourceException failure) {
			return StremioFutures.failedFuture(failure);
		}
		Request request = new Request(secret, etag, lastModified, token, consent);
		CompletableFuture<Response> fetched;
		try {
			fetched = manifestClient.fetch(request);
		} catch (RuntimeException failure) {
			return StremioFutures.failedFuture(
					new StremioSourceException(Code.TRANSPORT, failure));
		}
		return fetched.handle((response, failure) -> {
			if (failure != null) throw new StremioSourceException(Code.TRANSPORT, unwrap(failure));
			token.throwIfStale();
			if (response == null) throw new StremioSourceException(Code.TRANSPORT);
			String json = response.notModified() ? existingJson : response.manifestJson();
			if (json == null) throw new StremioSourceException(Code.TRANSPORT);
			if (!response.notModified()) assertUntainted(json, secret);
			final StremioManifest manifest;
			try {
				manifest = manifestParser.parse(json);
			} catch (IllegalArgumentException failureValue) {
				throw new StremioSourceException(Code.INVALID_MANIFEST, failureValue);
			}
			Collection<String> knownSecrets = knownSecrets(secret);
			String responseEtag = HttpValidatorPolicy.sanitize(
					(response.etag() == null) ? etag : response.etag(), knownSecrets);
			String responseLastModified = HttpValidatorPolicy.sanitize(
					(response.lastModified() == null) ? lastModified : response.lastModified(),
					knownSecrets);
			return new PreparedManifest(manifest, json, responseEtag,
					responseLastModified, redactedUrl, consent);
		});
	}

	private CompletableFuture<Void> stageThenCommit(RequestGeneration.Token token,
			String stagedSecretId, StremioSourceSecret stagedSecret, String obsoleteSecretId,
			StremioSourceSnapshot before, StremioSourceSnapshot after) {
		CompletableFuture<Void> staged;
		try {
			staged = secretVault.save(stagedSecretId, stagedSecret);
		} catch (RuntimeException failure) {
			return StremioFutures.failedFuture(secretFailure(failure));
		}
		return staged.handle((ignored, failure) -> {
			if (failure != null) throw secretFailure(unwrap(failure));
			return null;
		}).thenCompose(ignored -> {
			CompletableFuture<Void> persisted;
			try {
				token.throwIfStale();
				persisted = commit(before, after);
			} catch (RuntimeException failure) {
				persisted = StremioFutures.failedFuture(failure);
			}
			return persisted.handle((unused, failure) -> failure).thenCompose(failure -> {
				if (failure == null) return CompletableFuture.completedFuture(null);
				Throwable original = unwrap(failure);
				CompletableFuture<Void> cleanup;
				try {
					cleanup = secretVault.remove(stagedSecretId);
				} catch (RuntimeException cleanupFailure) {
					return StremioFutures.failedFuture(rollbackFailure(original, cleanupFailure));
				}
				return cleanup.handle((removed, cleanupFailure) -> {
					if (cleanupFailure != null) {
						throw rollbackFailure(original, unwrap(cleanupFailure));
					}
					throw asRuntime(original);
				});
			}).thenCompose(committed -> (obsoleteSecretId == null) ?
					CompletableFuture.completedFuture(null) : removeObsoleteSecret(obsoleteSecretId));
		});
	}

	private CompletableFuture<Void> removeObsoleteSecret(String secretId) {
		try {
			return secretVault.remove(secretId).handle((ignored, failure) -> null);
		} catch (RuntimeException ignored) {
			return CompletableFuture.completedFuture(null);
		}
	}

	private CompletableFuture<Void> commit(
			StremioSourceSnapshot before, StremioSourceSnapshot after) {
		return store.commit(before, after).handle((ignored, failure) -> {
			if (failure != null) {
				Throwable cause = unwrap(failure);
				if (cause instanceof StremioSourceException sourceFailure) throw sourceFailure;
				throw new StremioSourceException(Code.PERSISTENCE, cause);
			}
			publish(after);
			return null;
		});
	}

	private CompletableFuture<StremioSourceSnapshot> loadState() {
		return store.load().handle((snapshot, failure) -> {
			if (failure != null) {
				Throwable cause = unwrap(failure);
				if (cause instanceof StremioSourceException sourceFailure) throw sourceFailure;
				throw new StremioSourceException(Code.PERSISTENCE, cause);
			}
			return snapshot;
		});
	}

	private CompletableFuture<StremioSourceSecret> loadSecret(StremioSourceRecord source) {
		return secretVault.load(secretId(source)).handle((secret, failure) -> {
			if (failure != null) throw secretFailure(unwrap(failure));
			return secret;
		});
	}

	private <T> CompletableFuture<T> mutate(Supplier<CompletableFuture<T>> operation) {
		synchronized (mutationLock) {
			if (closed.get()) return StremioFutures.failedFuture(
					new StremioSourceException(Code.CLOSED));
			CompletableFuture<T> result = mutationTail.handle((ignored, failure) -> null)
					.thenCompose(ignored -> operation.get());
			mutationTail = result.handle((ignored, failure) -> null);
			return result;
		}
	}

	private RequestGeneration.Token begin(String key) {
		if (closed.get()) throw new StremioSourceException(Code.CLOSED);
		synchronized (generations) {
			return generations.computeIfAbsent(key, ignored -> new RequestGeneration()).begin();
		}
	}

	private void publish(StremioSourceSnapshot snapshot) {
		for (Consumer<StremioSourceSnapshot> observer : observers) {
			try {
				observer.accept(snapshot);
			} catch (RuntimeException ignored) {
				// A UI observer cannot invalidate an already durable domain transaction.
			}
		}
	}

	private static StremioSourceRecord createSource(String sourceUuid, String fingerprint,
			String secretRef, PreparedManifest prepared, int position, long now) {
		StremioManifest manifest = prepared.manifest;
		return new StremioSourceRecord(sourceUuid, fingerprint, manifest.id(), manifest.name(),
				manifest.version(), prepared.redactedUrl, secretRef, true,
				position, prepared.json, prepared.etag, prepared.lastModified, now, now, null,
				now, now, prepared.consent.allowCleartext(), prepared.consent.allowLan());
	}

	private static StremioSourceRecord refreshed(StremioSourceRecord current,
			String fingerprint, StremioSourceSecret replacementSecret,
			String secretRef, PreparedManifest prepared, long now) {
		StremioManifest manifest = prepared.manifest;
		String redactedUrl = (replacementSecret == null) ? current.redactedTransportUrl() :
				prepared.redactedUrl;
		NetworkConsent consent = (replacementSecret == null) ? current.networkConsent() :
				prepared.consent;
		return new StremioSourceRecord(current.sourceUuid(), fingerprint, manifest.id(),
				manifest.name(), manifest.version(), redactedUrl, secretRef,
				current.enabled(), current.position(), prepared.json, prepared.etag,
				prepared.lastModified, now, now, null, current.installedMs(), now,
				consent.allowCleartext(), consent.allowLan());
	}

	private static StremioSourceRecord withPosition(
			StremioSourceRecord source, int position, long now) {
		if (source.position() == position) return source;
		return new StremioSourceRecord(source.sourceUuid(), source.transportFingerprint(),
				source.addonId(), source.name(), source.version(), source.redactedTransportUrl(),
				source.secretRef(), source.enabled(), position, source.manifestJson(),
				source.manifestEtag(), source.manifestLastModified(), source.lastCheckedMs(),
				source.lastSuccessMs(), source.lastErrorCode(), source.installedMs(), now,
				source.allowCleartext(), source.allowLan());
	}

	private static List<StremioSourceRecord> normalizePositions(
			List<StremioSourceRecord> sources, long now) {
		List<StremioSourceRecord> result = new ArrayList<>(sources.size());
		for (int i = 0; i < sources.size(); i++) result.add(withPosition(sources.get(i), i, now));
		return result;
	}

	private static List<StremioSourceRecord> replace(
			List<StremioSourceRecord> sources, StremioSourceRecord replacement) {
		List<StremioSourceRecord> result = new ArrayList<>(sources);
		result.set(replacement.position(), replacement);
		return result;
	}

	private static StremioSourceRecord requireSource(
			StremioSourceSnapshot snapshot, String sourceUuid) {
		StremioSourceRecord source = snapshot.source(sourceUuid);
		if (source == null) throw new StremioSourceException(Code.NOT_FOUND);
		return source;
	}

	private static StremioSourceRecord sourceWithFingerprint(StremioSourceSnapshot snapshot,
			String fingerprint, String excludedSourceUuid) {
		for (StremioSourceRecord source : snapshot.sources()) {
			if (source.transportFingerprint().equals(fingerprint) &&
					!source.sourceUuid().equals(excludedSourceUuid)) return source;
		}
		return null;
	}

	private static boolean sameIds(
			List<StremioSourceRecord> sources, List<String> requested) {
		if (sources.size() != requested.size()) return false;
		Set<String> expected = new HashSet<>();
		for (StremioSourceRecord source : sources) expected.add(source.sourceUuid());
		return (new HashSet<>(requested).size() == requested.size()) &&
				expected.equals(new HashSet<>(requested));
	}

	private static void validateTransport(String value) {
		final URI uri;
		try {
			uri = new URI(value.trim());
		} catch (URISyntaxException failure) {
			throw new StremioSourceException(Code.INVALID_TRANSPORT, failure);
		}
		String scheme = uri.getScheme();
		if (!uri.isAbsolute() || uri.isOpaque() || (uri.getHost() == null) ||
				(!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme) &&
						!"stremio".equalsIgnoreCase(scheme))) {
			throw new StremioSourceException(Code.INVALID_TRANSPORT);
		}
	}

	private static void assertUntainted(String manifestJson, StremioSourceSecret secret) {
		Collection<String> knownSecrets = knownSecrets(secret);
		if (SecretTaintDetector.isManifestTainted(manifestJson, knownSecrets)) {
			throw new StremioSourceException(Code.SECRET_TAINT);
		}
	}

	private static Collection<String> knownSecrets(StremioSourceSecret secret) {
		Collection<String> knownSecrets = new ArrayList<>();
		knownSecrets.add(secret.transportUrl());
		knownSecrets.addAll(SecretTaintDetector.extractTransportSecrets(secret.transportUrl()));
		if ((secret.configurationToken() != null) &&
				(secret.configurationToken().length() >= 4)) {
			knownSecrets.add(secret.configurationToken());
		}
		return knownSecrets;
	}

	private static String fingerprint(StremioSourceSecret secret) {
		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		String url = encoder.encodeToString(secret.transportUrl().getBytes(StandardCharsets.UTF_8));
		String token = (secret.configurationToken() == null) ? "" : encoder.encodeToString(
				secret.configurationToken().getBytes(StandardCharsets.UTF_8));
		return TransportFingerprint.create("stremio-source:" + url + '.' + token);
	}

	private static String canonicalUuid(String value) {
		try {
			String canonical = UUID.fromString(value).toString();
			if (!canonical.equals(value)) throw new IllegalArgumentException();
			return canonical;
		} catch (NullPointerException | IllegalArgumentException failure) {
			throw new IllegalArgumentException("sourceUuid must be a canonical UUID", failure);
		}
	}

	private static String secretId(StremioSourceRecord source) {
		return StremioSecretReference.resolve(source);
	}

	private static StremioSourceOutcome changed(Action action, String sourceUuid,
			StremioSourceSnapshot snapshot) {
		return new StremioSourceOutcome(action, Status.CHANGED, sourceUuid, snapshot, null);
	}

	private static StremioSourceOutcome unchanged(Action action, String sourceUuid,
			StremioSourceSnapshot snapshot) {
		return new StremioSourceOutcome(action, Status.UNCHANGED, sourceUuid, snapshot, null);
	}

	private static StremioSourceOutcome failed(Action action, String sourceUuid,
			StremioSourceSnapshot snapshot, Code code) {
		return new StremioSourceOutcome(action, Status.FAILED, sourceUuid, snapshot, code);
	}

	private static StremioSourceOutcome cancelled(Action action, String sourceUuid) {
		return new StremioSourceOutcome(action, Status.CANCELLED, sourceUuid, null, Code.CANCELLED);
	}

	private static StremioSourceOutcome finish(Action action, String sourceUuid,
			StremioSourceOutcome outcome, Throwable failure) {
		if (failure == null) return outcome;
		Throwable cause = unwrap(failure);
		if (isCancelled(cause)) return cancelled(action, sourceUuid);
		return failed(action, sourceUuid, null, codeOf(cause));
	}

	private static CompletableFuture<StremioSourceOutcome> completedFailure(
			Action action, String sourceUuid, Throwable failure) {
		return CompletableFuture.completedFuture(finish(action, sourceUuid, null, failure));
	}

	private static Code codeOf(Throwable failure) {
		failure = unwrap(failure);
		if (failure instanceof StremioSourceException sourceFailure) return sourceFailure.code();
		if (failure instanceof SecureStorageUnavailableException) return Code.SECURE_STORAGE;
		if (failure instanceof ManifestValidationException) return Code.INVALID_MANIFEST;
		if (failure instanceof CancellationException) return Code.CANCELLED;
		return Code.TRANSPORT;
	}

	private static StremioSourceException secretFailure(Throwable failure) {
		return (failure instanceof StremioSourceException sourceFailure) ? sourceFailure :
				new StremioSourceException(Code.SECURE_STORAGE, failure);
	}

	private static StremioSourceException rollbackFailure(
			Throwable original, Throwable rollbackFailure) {
		StremioSourceException failure = new StremioSourceException(Code.ROLLBACK, rollbackFailure);
		failure.addSuppressed(original);
		return failure;
	}

	private static RuntimeException asRuntime(Throwable failure) {
		return (failure instanceof RuntimeException runtime) ? runtime :
				new CompletionException(failure);
	}

	private static boolean isCancelled(Throwable failure) {
		return (failure instanceof CancellationException) ||
				((failure instanceof StremioSourceException sourceFailure) &&
						(sourceFailure.code() == Code.CANCELLED));
	}

	private static Throwable unwrap(Throwable failure) {
		while ((failure instanceof CompletionException) && (failure.getCause() != null)) {
			failure = failure.getCause();
		}
		return failure;
	}

	private record PreparedManifest(StremioManifest manifest, String json, String etag,
			String lastModified, String redactedUrl, NetworkConsent consent) {
	}

	private static final class FetchAttempt {
		private final PreparedManifest prepared;
		private final Throwable failure;

		private FetchAttempt(PreparedManifest prepared, Throwable failure) {
			this.prepared = prepared;
			this.failure = failure;
		}
	}
}
