package me.aap.fermata.addon.stremio.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.addon.stremio.source.StremioManifestClient.Request;
import me.aap.fermata.addon.stremio.source.StremioManifestClient.Response;
import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Status;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class StremioSourceManagerTest {
	private static final String UUID_A = "123e4567-e89b-12d3-a456-426614174000";
	private static final String UUID_B = "123e4567-e89b-12d3-a456-426614174001";
	private static final String UUID_C = "123e4567-e89b-12d3-a456-426614174002";
	private static final String MANIFEST = "{\"id\":\"org.test.provider\","
			+ "\"name\":\"Test Provider\",\"description\":\"Fixture\",\"version\":\"1.0.0\","
			+ "\"types\":[\"movie\"],\"resources\":[\"catalog\"],\"catalogs\":[{"
			+ "\"type\":\"movie\",\"id\":\"fixture\",\"name\":\"Fixture\"}]}";
	private static final String MANIFEST_V2 = MANIFEST.replace("1.0.0", "2.0.0")
			.replace("Test Provider", "Updated Provider");
	private static final String STREAMING_CATALOGS_MANIFEST = """
			{"id":"pw.ers.netflix-catalog","logo":"https://play-lh.googleusercontent.com/
			TBRwjS_qfJCSj1m7zZB93FnpJM5fSpMA_wUlFDLxWAb45T9RmwBvQd5cWR5viJJOhkI",
			"version":"1.1.1","name":"Streaming Catalogs",
			"description":"Trending movies and series on Netflix, HBO Max, Disney+, Apple TV+ and more.",
			"catalogs":[{"id":"nfx","type":"movie","name":"Netflix"},
			{"id":"nfx","type":"series","name":"Netflix"}],"resources":["catalog"],
			"types":["movie","series"],"idPrefixes":["tt"],
			"behaviorHints":{"configurable":true}}
			""".replace("\n", "");

	private FakeStore store;
	private FakeVault vault;
	private FakeClient client;
	private AtomicLong clock;
	private Queue<String> uuids;
	private StremioSourceManager manager;

	@Before
	public void setUp() {
		store = new FakeStore();
		vault = new FakeVault();
		client = new FakeClient();
		clock = new AtomicLong(1_000);
		uuids = new ArrayDeque<>(List.of(UUID_A, UUID_B, UUID_C));
		manager = new StremioSourceManager(store, vault, client,
				StremioManifestParser.strict(), clock::incrementAndGet, uuids::remove);
	}

	@After
	public void tearDown() {
		manager.close();
	}

	@Test
	public void addPersistsOnlyRedactedTransportAndPublishesCommittedSnapshot() throws Exception {
		List<StremioSourceSnapshot> observed = new ArrayList<>();
		manager.observe(observed::add);
		StremioSourceInput input = new StremioSourceInput(
				"https://provider.invalid/one/manifest.json?token=one", "private-token",
				new NetworkConsent(true, true));

		StremioSourceOutcome outcome = await(manager.add(input));

		assertEquals(outcome.toString(), 1, client.requests.size());
		assertEquals(outcome.toString(), 1, vault.saveCalls);
		assertEquals(outcome.toString(), 1, store.commits);
		assertEquals(outcome.toString(), Status.CHANGED, outcome.status());
		assertEquals(UUID_A, outcome.sourceUuid());
		StremioSourceRecord source = outcome.source();
		assertEquals(0, source.position());
		assertTrue(source.enabled());
		assertEquals(new NetworkConsent(true, true), source.networkConsent());
		assertEquals(source.networkConsent(), client.requests.get(0).consent());
		assertEquals("org.test.provider", source.addonId());
		assertEquals("secure:stremio-source:" + UUID_A, source.secretRef());
		assertFalse(source.redactedTransportUrl().contains("?token"));
		assertFalse(source.redactedTransportUrl().contains("private-token"));
		assertFalse(outcome.toString().contains("private-token"));
		assertEquals(input.transportUrl(), vault.values.get(UUID_A).transportUrl());
		assertEquals(List.of(outcome.snapshot()), observed);
	}

	@Test
	public void duplicateTransportIsRejectedWithoutSecretOrObservableMutation() throws Exception {
		await(manager.add(input("same", "token")));
		int commits = store.commits;
		AtomicInteger observed = new AtomicInteger();
		manager.observe(ignored -> observed.incrementAndGet());

		StremioSourceOutcome duplicate = await(manager.add(input("same", "token")));

		assertEquals(Status.FAILED, duplicate.status());
		assertEquals(Code.DUPLICATE_TRANSPORT, duplicate.errorCode());
		assertEquals(commits, store.commits);
		assertEquals(0, observed.get());
		assertEquals(1, vault.values.size());
	}

	@Test
	public void tokenParticipatesInDuplicateFingerprintButNeverDurableIdentity() throws Exception {
		StremioSourceOutcome first = await(manager.add(input("same-url", "token-a")));
		StremioSourceOutcome second = await(manager.add(input("same-url", "token-b")));

		assertEquals(Status.CHANGED, second.status());
		assertNotEquals(first.source().transportFingerprint(), second.source().transportFingerprint());
		assertFalse(second.snapshot().toString().contains("token-b"));
	}

	@Test
	public void shortConfigurationTokenDoesNotTaintUnrelatedManifestText() throws Exception {
		StremioSourceOutcome outcome = await(manager.add(input("short-token", "a")));

		assertEquals(Status.CHANGED, outcome.status());
		assertEquals("a", vault.values.get(UUID_A).configurationToken());
		assertFalse(outcome.snapshot().toString().contains("configurationToken"));
	}

	@Test
	public void editPreservesUuidOrderEnabledStateAndInstallTime() throws Exception {
		StremioSourceOutcome added = await(manager.add(input("old", "old-token")));
		await(manager.disable(UUID_A));
		StremioSourceRecord before = store.state.source(UUID_A);
		client.responses.add(CompletableFuture.completedFuture(
				Response.modified(MANIFEST_V2, "etag-v2", "later")));

		StremioSourceOutcome edited = await(manager.edit(UUID_A, input("new", "new-token")));

		assertEquals(Status.CHANGED, edited.status());
		StremioSourceRecord after = edited.source();
		assertEquals(added.sourceUuid(), after.sourceUuid());
		assertEquals(before.position(), after.position());
		assertEquals(before.installedMs(), after.installedMs());
		assertFalse(after.enabled());
		assertEquals("Updated Provider", after.name());
		assertEquals("2.0.0", after.version());
		assertEquals("new-token", secret(after).configurationToken());
		assertNull(vault.values.get(UUID_A));
	}

	@Test
	public void editCannotTakeAnotherSourcesTransport() throws Exception {
		await(manager.add(input("one", "alpha-secret")));
		await(manager.add(input("two", "beta-secret")));
		assertEquals(List.of(UUID_A, UUID_B), ids(store.state));
		StremioSourceSecret original = secret(store.state.source(UUID_A));

		StremioSourceOutcome outcome = await(manager.edit(UUID_A,
				input("two", "beta-secret")));

		assertEquals(Code.DUPLICATE_TRANSPORT, outcome.errorCode());
		assertSame(original, secret(store.state.source(UUID_A)));
		assertEquals(2, store.state.sources().size());
	}

	@Test
	public void enableDisableAreIdempotentAndDoNotReorderProviders() throws Exception {
		await(manager.add(input("one", null)));
		await(manager.add(input("two", null)));

		StremioSourceOutcome disabled = await(manager.disable(UUID_A));
		int commits = store.commits;
		StremioSourceOutcome unchanged = await(manager.disable(UUID_A));
		StremioSourceOutcome enabled = await(manager.enable(UUID_A));

		assertFalse(disabled.source().enabled());
		assertEquals(Status.UNCHANGED, unchanged.status());
		assertEquals(commits + 1, store.commits);
		assertTrue(enabled.source().enabled());
		assertEquals(List.of(UUID_A, UUID_B), ids(store.state));
	}

	@Test
	public void reorderPersistsExactProviderOrderAndRejectsInvalidSets() throws Exception {
		await(manager.add(input("one", null)));
		await(manager.add(input("two", null)));
		await(manager.add(input("three", null)));

		StremioSourceOutcome reordered = await(manager.reorder(List.of(UUID_C, UUID_A, UUID_B)));
		StremioSourceOutcome invalid = await(manager.reorder(List.of(UUID_A, UUID_A, UUID_B)));

		assertEquals(List.of(UUID_C, UUID_A, UUID_B), ids(reordered.snapshot()));
		assertEquals(List.of(0, 1, 2), positions(reordered.snapshot()));
		assertEquals(Code.INVALID_ORDER, invalid.errorCode());
		assertEquals(List.of(UUID_C, UUID_A, UUID_B), ids(store.state));
	}

	@Test
	public void removeDeletesSecretAndNormalizesRemainingPositions() throws Exception {
		await(manager.add(input("one", null)));
		await(manager.add(input("two", null)));
		await(manager.add(input("three", null)));

		StremioSourceOutcome removed = await(manager.remove(UUID_B));

		assertEquals(Status.CHANGED, removed.status());
		assertEquals(List.of(UUID_A, UUID_C), ids(removed.snapshot()));
		assertEquals(List.of(0, 1), positions(removed.snapshot()));
		assertNull(vault.values.get(UUID_B));
	}

	@Test
	public void failedAddCommitRemovesNewSecretAndDoesNotPublish() throws Exception {
		store.failNextCommit = true;
		AtomicInteger observed = new AtomicInteger();
		manager.observe(ignored -> observed.incrementAndGet());

		StremioSourceOutcome outcome = await(manager.add(input("one", "secret")));

		assertEquals(Code.PERSISTENCE, outcome.errorCode());
		assertTrue(store.state.sources().isEmpty());
		assertTrue(vault.values.isEmpty());
		assertEquals(0, observed.get());
	}

	@Test
	public void failedEditCommitRestoresPreviousSecretAndRecord() throws Exception {
		await(manager.add(input("old", "old-secret")));
		StremioSourceRecord original = store.state.source(UUID_A);
		store.failNextCommit = true;

		StremioSourceOutcome outcome = await(manager.edit(UUID_A, input("new", "new-secret")));

		assertEquals(Code.PERSISTENCE, outcome.errorCode());
		assertEquals(original, store.state.source(UUID_A));
		assertEquals("old-secret", secret(original).configurationToken());
		assertEquals(1, vault.values.size());
	}

	@Test
	public void obsoleteSecretCleanupFailureCannotInvalidateCommittedEdit() throws Exception {
		await(manager.add(input("old", "old-secret")));
		vault.failNextRemove = true;

		StremioSourceOutcome outcome = await(manager.edit(UUID_A,
				input("new", "new-secret")));

		assertEquals(Status.CHANGED, outcome.status());
		assertEquals("new-secret", secret(outcome.source()).configurationToken());
		assertEquals(2, vault.values.size());
	}

	@Test
	public void failedRemoveCommitRestoresSecretAndSource() throws Exception {
		await(manager.add(input("one", "keep")));
		store.failNextCommit = true;

		StremioSourceOutcome outcome = await(manager.remove(UUID_A));

		assertEquals(Code.PERSISTENCE, outcome.errorCode());
		assertNotNull(store.state.source(UUID_A));
		assertEquals("keep", secret(store.state.source(UUID_A)).configurationToken());
	}

	@Test
	public void secretWriteFailureCannotCreateOrMutateSource() throws Exception {
		vault.failNextSave = true;
		StremioSourceOutcome add = await(manager.add(input("one", null)));
		assertEquals(Code.SECURE_STORAGE, add.errorCode());
		assertTrue(store.state.sources().isEmpty());

		await(manager.add(input("two", null)));
		StremioSourceRecord before = store.state.source(UUID_B);
		vault.failNextSave = true;
		StremioSourceOutcome edit = await(manager.edit(UUID_B, input("edited", null)));
		assertEquals(Code.SECURE_STORAGE, edit.errorCode());
		assertEquals(before, store.state.source(UUID_B));
	}

	@Test
	public void rollbackFailureIsReportedExplicitly() throws Exception {
		await(manager.add(input("old", null)));
		store.failNextCommit = true;
		vault.failNextRemove = true;

		StremioSourceOutcome outcome = await(manager.edit(UUID_A, input("new", null)));

		assertEquals(Code.ROLLBACK, outcome.errorCode());
	}

	@Test
	public void lateAddGenerationCannotCommitAfterNewerAdd() throws Exception {
		CompletableFuture<Response> oldResponse = new CompletableFuture<>();
		CompletableFuture<Response> newResponse = new CompletableFuture<>();
		client.responses.add(oldResponse);
		client.responses.add(newResponse);
		CompletableFuture<StremioSourceOutcome> old = manager.add(input("old", null));
		CompletableFuture<StremioSourceOutcome> newer = manager.add(input("new", null));

		newResponse.complete(Response.modified(MANIFEST, null, null));
		assertEquals(Status.CHANGED, await(newer).status());
		oldResponse.complete(Response.modified(MANIFEST, null, null));

		assertEquals(Status.CANCELLED, await(old).status());
		assertEquals(1, store.state.sources().size());
		assertEquals(UUID_B, store.state.sources().get(0).sourceUuid());
		assertFalse(client.requests.get(0).isActive());
	}

	@Test
	public void newerSourceOperationCancelsLateRefresh() throws Exception {
		await(manager.add(input("one", null)));
		CompletableFuture<Response> refreshResponse = new CompletableFuture<>();
		client.responses.add(refreshResponse);
		CompletableFuture<StremioSourceOutcome> refresh = manager.refresh(UUID_A);
		StremioSourceOutcome disabled = await(manager.disable(UUID_A));
		refreshResponse.complete(Response.modified(MANIFEST_V2, null, null));

		assertFalse(disabled.source().enabled());
		assertEquals(Status.CANCELLED, await(refresh).status());
		assertEquals("1.0.0", store.state.source(UUID_A).version());
	}

	@Test
	public void refreshUsesConditionalHeadersAndHandlesNotModified() throws Exception {
		client.responses.add(CompletableFuture.completedFuture(
				Response.modified(MANIFEST, "etag-one", "yesterday")));
		await(manager.add(input("one", null)));
		long previousSuccess = store.state.source(UUID_A).lastSuccessMs();
		client.responses.add(CompletableFuture.completedFuture(
				Response.notModified("etag-one", "today")));

		StremioSourceOutcome outcome = await(manager.refresh(UUID_A));

		Request request = client.requests.get(client.requests.size() - 1);
		assertEquals("etag-one", request.etag());
		assertEquals("yesterday", request.lastModified());
		assertEquals(MANIFEST, outcome.source().manifestJson());
		assertEquals("today", outcome.source().manifestLastModified());
		assertTrue(outcome.source().lastSuccessMs() > previousSuccess);
	}

	@Test
	public void responseValidatorsCannotPersistShortProviderSecrets() throws Exception {
		client.responses.add(CompletableFuture.completedFuture(
				Response.modified(MANIFEST, "etag-aB12cd", "modified-aB12cd")));

		StremioSourceOutcome outcome = await(manager.add(input("one", "aB12cd")));

		assertEquals(Status.CHANGED, outcome.status());
		assertNull(outcome.source().manifestEtag());
		assertNull(outcome.source().manifestLastModified());
	}

	@Test
	public void genericQueryKeyCredentialReflectedWithoutFullTransportIsRejected() throws Exception {
		String tainted = MANIFEST.replace("Fixture", "Reflected aB12cd");
		client.responses.add(CompletableFuture.completedFuture(
				Response.modified(tainted, "etag-aB12cd", null)));
		StremioSourceInput input = new StremioSourceInput(
				"https://provider.invalid/catalog/manifest.json?key=aB12cd&type=movie", null);

		StremioSourceOutcome outcome = await(manager.add(input));

		assertEquals(Code.SECRET_TAINT, outcome.errorCode());
		assertTrue(store.state.sources().isEmpty());
		assertTrue(vault.values.isEmpty());
	}

	@Test
	public void refreshFailurePersistsOnlySafeErrorCode() throws Exception {
		await(manager.add(input("one", "do-not-leak")));
		client.responses.add(CompletableFuture.failedFuture(
				new IllegalStateException("https://provider.invalid/?token=do-not-leak")));

		StremioSourceOutcome outcome = await(manager.refresh(UUID_A));

		assertEquals(Status.FAILED, outcome.status());
		assertEquals(Code.TRANSPORT, outcome.errorCode());
		assertEquals("TRANSPORT", outcome.source().lastErrorCode());
		assertFalse(outcome.snapshot().toString().contains("do-not-leak"));
	}

	@Test
	public void secretTaintedAndMalformedManifestsNeverReachPersistence() throws Exception {
		String tainted = MANIFEST.substring(0, MANIFEST.length() - 1)
				+ ",\"token\":\"provider-secret\"}";
		client.responses.add(CompletableFuture.completedFuture(
				Response.modified(tainted, null, null)));
		StremioSourceOutcome secret = await(manager.add(input("one", null)));
		assertEquals(Code.SECRET_TAINT, secret.errorCode());

		client.responses.add(CompletableFuture.completedFuture(
				Response.modified("<html>login</html>", null, null)));
		StremioSourceOutcome malformed = await(manager.add(input("two", null)));
		assertEquals(Code.INVALID_MANIFEST, malformed.errorCode());
		assertTrue(store.state.sources().isEmpty());
		assertTrue(vault.values.isEmpty());
	}

	@Test
	public void acceptsStreamingCatalogsManifestWithPublicOpaqueLogoId() throws Exception {
		client.responses.add(CompletableFuture.completedFuture(
				Response.modified(STREAMING_CATALOGS_MANIFEST, null, null)));

		StremioSourceOutcome outcome = await(manager.add(new StremioSourceInput(
				"https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/" +
						"manifest.json", null)));

		assertEquals(Status.CHANGED, outcome.status());
		assertEquals("pw.ers.netflix-catalog", outcome.source().addonId());
		assertEquals(2, StremioManifestParser.strict()
				.parse(outcome.source().manifestJson()).catalogs().size());
	}

	@Test
	public void freshInstallAddsCinemetaOnceAndRemovalNeverReinstallsIt() throws Exception {
		StremioSourceInput cinemeta = input("cinemeta", null);
		StremioSourceOutcome installed = await(manager.initializeCinemeta(true, cinemeta));
		assertTrue(installed.snapshot().cinemetaInstallHandled());
		assertEquals(1, installed.snapshot().sources().size());

		await(manager.remove(UUID_A));
		int requests = client.requests.size();
		StremioSourceOutcome second = await(manager.initializeCinemeta(true, cinemeta));

		assertEquals(Status.UNCHANGED, second.status());
		assertTrue(second.snapshot().sources().isEmpty());
		assertEquals(requests, client.requests.size());
	}

	@Test
	public void upgradedInstallMarksDefaultHandledWithoutNetworkOrInstall() throws Exception {
		StremioSourceOutcome outcome = await(manager.initializeCinemeta(false,
				input("cinemeta", null)));

		assertEquals(Status.CHANGED, outcome.status());
		assertTrue(outcome.snapshot().cinemetaInstallHandled());
		assertTrue(outcome.snapshot().sources().isEmpty());
		assertTrue(client.requests.isEmpty());
	}

	@Test
	public void transientCinemetaFailureDoesNotBurnOneTimeInstall() throws Exception {
		client.responses.add(CompletableFuture.failedFuture(new IllegalStateException("offline")));
		StremioSourceOutcome failed = await(manager.initializeCinemeta(true,
				input("cinemeta", null)));
		assertEquals(Code.TRANSPORT, failed.errorCode());
		assertFalse(store.state.cinemetaInstallHandled());

		StremioSourceOutcome retried = await(manager.initializeCinemeta(true,
				input("cinemeta", null)));
		assertEquals(Status.CHANGED, retried.status());
		assertTrue(retried.snapshot().cinemetaInstallHandled());
	}

	@Test
	public void duplicateExistingCinemetaTransportOnlySetsMarker() throws Exception {
		StremioSourceInput cinemeta = input("cinemeta", null);
		await(manager.add(cinemeta));
		int sourceCount = store.state.sources().size();

		StremioSourceOutcome initialized = await(manager.initializeCinemeta(true, cinemeta));

		assertEquals(Status.CHANGED, initialized.status());
		assertTrue(initialized.snapshot().cinemetaInstallHandled());
		assertEquals(sourceCount, initialized.snapshot().sources().size());
	}

	@Test
	public void closeDuringPendingCinemetaCannotCommitSourceOrMarker() throws Exception {
		CompletableFuture<Response> pending = new CompletableFuture<>();
		client.responses.add(pending);
		CompletableFuture<StremioSourceOutcome> initialization = manager.initializeCinemeta(
				true, input("cinemeta-pending", null));
		assertEquals(1, client.requests.size());

		manager.close();
		pending.complete(Response.modified(MANIFEST, null, null));
		StremioSourceOutcome outcome = await(initialization);

		assertEquals(Status.CANCELLED, outcome.status());
		assertTrue(store.state.sources().isEmpty());
		assertFalse(store.state.cinemetaInstallHandled());
		assertTrue(vault.values.isEmpty());
	}

	@Test
	public void failingObserverCannotInvalidateCommittedTransaction() throws Exception {
		manager.observe(ignored -> {
			throw new IllegalStateException("UI observer failure");
		});

		StremioSourceOutcome outcome = await(manager.add(input("one", null)));

		assertEquals(Status.CHANGED, outcome.status());
		assertNotNull(store.state.source(UUID_A));
	}

	private static StremioSourceInput input(String identity, String token) {
		return new StremioSourceInput(
				"https://provider.invalid/" + identity + "/manifest.json?token=" + identity, token);
	}

	private static List<String> ids(StremioSourceSnapshot snapshot) {
		return snapshot.sources().stream().map(StremioSourceRecord::sourceUuid).toList();
	}

	private static List<Integer> positions(StremioSourceSnapshot snapshot) {
		return snapshot.sources().stream().map(StremioSourceRecord::position).toList();
	}

	private StremioSourceSecret secret(StremioSourceRecord source) {
		String prefix = "secure:stremio-source:";
		String reference = source.secretRef();
		String id = ((reference != null) && reference.startsWith(prefix)) ?
				reference.substring(prefix.length()) : source.sourceUuid();
		return vault.values.get(id);
	}

	private static <T> T await(CompletableFuture<T> future) throws Exception {
		return future.get(5, TimeUnit.SECONDS);
	}

	private static final class FakeStore implements StremioSourceStore {
		private StremioSourceSnapshot state = StremioSourceSnapshot.empty();
		private boolean failNextCommit;
		private int commits;

		@Override
		public CompletableFuture<StremioSourceSnapshot> load() {
			return CompletableFuture.completedFuture(state);
		}

		@Override
		public CompletableFuture<Void> commit(
				StremioSourceSnapshot expected, StremioSourceSnapshot replacement) {
			if (failNextCommit) {
				failNextCommit = false;
				return CompletableFuture.failedFuture(new IllegalStateException("commit failed"));
			}
			if (!state.equals(expected)) return CompletableFuture.failedFuture(
					new StremioSourceException(Code.CONCURRENT_MODIFICATION));
			state = replacement;
			commits++;
			return CompletableFuture.completedFuture(null);
		}
	}

	private static final class FakeVault implements StremioSourceSecretVault {
		private final Map<String, StremioSourceSecret> values = new HashMap<>();
		private boolean failNextSave;
		private boolean failNextRemove;
		private int saveCalls;

		@Override
		public CompletableFuture<StremioSourceSecret> load(String sourceUuid) {
			return CompletableFuture.completedFuture(values.get(sourceUuid));
		}

		@Override
		public CompletableFuture<Void> save(String sourceUuid, StremioSourceSecret secret) {
			saveCalls++;
			if (failNextSave) {
				failNextSave = false;
				return CompletableFuture.failedFuture(new IllegalStateException("secret save failed"));
			}
			values.put(sourceUuid, secret);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> remove(String sourceUuid) {
			if (failNextRemove) {
				failNextRemove = false;
				return CompletableFuture.failedFuture(
						new IllegalStateException("secret remove failed"));
			}
			values.remove(sourceUuid);
			return CompletableFuture.completedFuture(null);
		}
	}

	private static final class FakeClient implements StremioManifestClient {
		private final Queue<CompletableFuture<Response>> responses = new ArrayDeque<>();
		private final List<Request> requests = new ArrayList<>();

		@Override
		public CompletableFuture<Response> fetch(Request request) {
			requests.add(request);
			CompletableFuture<Response> response = responses.poll();
			return (response == null) ? CompletableFuture.completedFuture(
					Response.modified(MANIFEST, null, null)) : response;
		}
	}
}
