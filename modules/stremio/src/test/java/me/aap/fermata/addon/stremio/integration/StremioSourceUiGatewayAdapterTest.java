package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.lang.reflect.Constructor;

import org.junit.Test;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.addon.stremio.source.StremioSourceInput;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;
import me.aap.fermata.addon.stremio.ui.source.SourceUiConsent;
import me.aap.fermata.addon.stremio.ui.source.SourceUiDraft;
import me.aap.fermata.addon.stremio.ui.source.SourceUiResult;
import me.aap.fermata.addon.stremio.ui.config.StremioConfigResult;

public class StremioSourceUiGatewayAdapterTest {
	@Test
	public void mapsSecretFreeRowsAndLoadsDraftOnlyThroughSecretBridge() throws Exception {
		FakeSources sources = new FakeSources(snapshot(record("source-a", 0, true)));
		StremioSourceUiGatewayAdapter gateway = new StremioSourceUiGatewayAdapter(sources,
				source -> CompletableFuture.completedFuture(new StremioSourceSecret(
						"https://provider.invalid/private/manifest.json", "private-token")));

		var row = gateway.load().get().sources().get(0);
		var draft = gateway.loadDraft("source-a").get();

		assertEquals("Fixture", row.name());
		assertTrue(row.configurable());
		assertFalse(row.toString().contains("provider.invalid"));
		assertEquals("https://provider.invalid/private/manifest.json", draft.transportUrl());
		assertEquals("private-token", draft.configurationToken());
		assertFalse(draft.toString().contains("private-token"));
		assertFalse(gateway.loadConfiguration("source-a").get().toString()
				.contains("private-token"));
	}

	@Test
	public void configuredManifestIsAppliedThroughEncryptedSourceEditBoundary() throws Exception {
		FakeSources sources = new FakeSources(snapshot(record("source-a", 0, true)));
		StremioSourceUiGatewayAdapter gateway = new StremioSourceUiGatewayAdapter(sources,
				source -> CompletableFuture.completedFuture(new StremioSourceSecret(
						"https://provider.invalid/manifest.json", "old-token")));
		String configured = "https://provider.invalid/new-private-token/manifest.json";
		Constructor<StremioConfigResult> constructor = StremioConfigResult.class
				.getDeclaredConstructor(String.class);
		constructor.setAccessible(true);

		SourceUiResult result = gateway.configure("source-a", constructor.newInstance(configured))
				.completion().get();

		assertEquals(SourceUiResult.Status.CHANGED, result.status());
		assertEquals(configured, sources.lastEditInput.transportUrl());
		assertEquals(null, sources.lastEditInput.configurationToken());
	}

	@Test
	public void addRetainsConsentAndRemoveDropsSourceFromSnapshot() throws Exception {
		FakeSources sources = new FakeSources(StremioSourceSnapshot.empty());
		StremioSourceUiGatewayAdapter gateway = new StremioSourceUiGatewayAdapter(sources,
				source -> CompletableFuture.completedFuture(null));
		SourceUiConsent consent = new SourceUiConsent(true, true);

		SourceUiResult added = gateway.add(new SourceUiDraft(
				"http://10.0.0.2/manifest.json", "", consent)).completion().get();
		assertEquals(SourceUiResult.Status.CHANGED, added.status());
		assertEquals(consent, added.snapshot().sources().get(0).consent());
		assertEquals(consent, new StremioSourceUiGatewayAdapter(sources,
				source -> CompletableFuture.completedFuture(null)).load().get()
				.sources().get(0).consent());

		SourceUiResult removed = gateway.remove("source-added").completion().get();
		assertEquals(SourceUiResult.Status.CHANGED, removed.status());
		assertTrue(removed.snapshot().sources().isEmpty());
	}

	@Test
	public void observationCanBeClosedWithoutLateUiCallbacks() throws Exception {
		FakeSources sources = new FakeSources(StremioSourceSnapshot.empty());
		StremioSourceUiGatewayAdapter gateway = new StremioSourceUiGatewayAdapter(sources,
				source -> CompletableFuture.completedFuture(null));
		List<Long> revisions = new ArrayList<>();
		AutoCloseable observation = gateway.observe(snapshot -> revisions.add(snapshot.revision()));

		sources.emit(snapshot(record("source-a", 0, true)));
		observation.close();
		sources.emit(snapshot(record("source-a", 0, false)));

		assertEquals(List.of(1L), revisions);
	}

	private static StremioSourceSnapshot snapshot(StremioSourceRecord... sources) {
		return new StremioSourceSnapshot(1, List.of(sources), false);
	}

		private static StremioSourceRecord record(String id, int position, boolean enabled) {
		return record(id, position, enabled, false, false);
	}

	private static StremioSourceRecord record(String id, int position, boolean enabled,
			boolean allowCleartext, boolean allowLan) {
		return new StremioSourceRecord(id, "fingerprint-" + id, "org.fixture", "Fixture",
				"1.0.0", "https://provider.invalid/manifest.json", "secure:" + id,
				enabled, position,
				"{\"id\":\"org.fixture\",\"name\":\"Fixture\",\"description\":\"F\"," +
						"\"version\":\"1.0.0\",\"types\":[\"movie\"]," +
						"\"resources\":[\"catalog\"],\"catalogs\":[]," +
						"\"behaviorHints\":{\"configurable\":true}}",
				null, null, 0, 0, null, 0, 0, allowCleartext, allowLan);
	}

	private static final class FakeSources
			implements StremioSourceUiGatewayAdapter.SourceAccess {
		private StremioSourceSnapshot state;
		private Consumer<StremioSourceSnapshot> observer;
		private StremioSourceInput lastEditInput;

		FakeSources(StremioSourceSnapshot state) {
			this.state = state;
		}

		@Override
		public CompletableFuture<StremioSourceSnapshot> sources() {
			return CompletableFuture.completedFuture(state);
		}

		@Override
		public AutoCloseable observe(Consumer<StremioSourceSnapshot> observer) {
			this.observer = observer;
			return () -> {
				if (this.observer == observer) this.observer = null;
			};
		}

		void emit(StremioSourceSnapshot next) {
			state = next;
			if (observer != null) observer.accept(next);
		}

		@Override
		public CompletableFuture<StremioSourceOutcome> add(StremioSourceInput input) {
			state = snapshot(record("source-added", 0, true,
					input.networkConsent().allowCleartext(), input.networkConsent().allowLan()));
			return changed(StremioSourceOutcome.Action.ADD, "source-added");
		}

		@Override
		public CompletableFuture<StremioSourceOutcome> edit(
				String sourceUuid, StremioSourceInput input) {
			lastEditInput = input;
			return changed(StremioSourceOutcome.Action.EDIT, sourceUuid);
		}

		@Override
		public CompletableFuture<StremioSourceOutcome> enable(String sourceUuid) {
			return changed(StremioSourceOutcome.Action.ENABLE, sourceUuid);
		}

		@Override
		public CompletableFuture<StremioSourceOutcome> disable(String sourceUuid) {
			return changed(StremioSourceOutcome.Action.DISABLE, sourceUuid);
		}

		@Override
		public CompletableFuture<StremioSourceOutcome> refresh(String sourceUuid) {
			return changed(StremioSourceOutcome.Action.REFRESH, sourceUuid);
		}

		@Override
		public CompletableFuture<StremioSourceOutcome> remove(String sourceUuid) {
			state = new StremioSourceSnapshot(state.revision() + 1, List.of(), false);
			return changed(StremioSourceOutcome.Action.REMOVE, sourceUuid);
		}

		@Override
		public CompletableFuture<StremioSourceOutcome> reorder(List<String> ids) {
			return changed(StremioSourceOutcome.Action.REORDER, null);
		}

		private CompletableFuture<StremioSourceOutcome> changed(
				StremioSourceOutcome.Action action, String sourceUuid) {
			return CompletableFuture.completedFuture(new StremioSourceOutcome(action,
					StremioSourceOutcome.Status.CHANGED, sourceUuid, state, null));
		}
	}
}
