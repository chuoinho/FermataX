package me.aap.fermata.addon.stremio.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;
import me.aap.fermata.media.net.PlaybackRequestValidationException;

public class PlaybackHeaderRegistryTest {
	@Test
	public void evictsOldestEntriesAndRejectsOversizedHeaders() throws Exception {
		AtomicLong clock = new AtomicLong(1_000L);
		PlaybackHeaderRegistry.HeaderStore store =
				new PlaybackHeaderRegistry.HeaderStore(clock::get);
		var first = store.register("source", "first", Map.of("x-test", "one"), 10_000L);
		for (int i = 1; i <= PlaybackHeaderRegistry.HeaderStore.MAX_ENTRIES; i++) {
			store.register("source", "descriptor-" + i, Map.of("x-test", "value"), 10_000L);
		}

		assertEquals(PlaybackHeaderRegistry.HeaderStore.MAX_ENTRIES, store.size());
		assertThrows(PlaybackRequestValidationException.class, () -> store.resolve(first));
		assertThrows(IllegalArgumentException.class, () -> store.register("source", "large",
				Map.of("x-test", "x".repeat(16 * 1024)), 10_000L));
	}

	@Test
	public void revokesOnlyHeadersOwnedByDisabledOrRemovedSource() throws Exception {
		PlaybackHeaderRegistry.HeaderStore store = new PlaybackHeaderRegistry.HeaderStore();
		StremioSourceRecord first = source("first", "fingerprint-first", "secret-first", true, 0);
		StremioSourceRecord second = source("second", "fingerprint-second", "secret-second", true, 1);
		store.reconcileSources(snapshot(1, first, second));
		var firstHeaders = store.register("first", "descriptor-first",
				Map.of("Authorization", "first-value"), Long.MAX_VALUE);
		var secondHeaders = store.register("second", "descriptor-second",
				Map.of("Authorization", "second-value"), Long.MAX_VALUE);

		store.reconcileSources(snapshot(2,
				source("first", "fingerprint-first", "secret-first", false, 0), second));

		assertThrows(PlaybackRequestValidationException.class,
				() -> store.resolve(firstHeaders));
		assertEquals(Map.of("Authorization", "second-value"), store.resolve(secondHeaders));
		assertThrows(IllegalStateException.class, () -> store.register("first", "stale",
				Map.of("Authorization", "stale"), Long.MAX_VALUE));

		store.reconcileSources(snapshot(3,
				source("first", "fingerprint-first", "secret-first", false, 0)));
		assertThrows(PlaybackRequestValidationException.class,
				() -> store.resolve(secondHeaders));
	}

	@Test
	public void materialEditRevokesOwnerButRefreshAndReorderKeepHealthyHeaders() throws Exception {
		PlaybackHeaderRegistry.HeaderStore store = new PlaybackHeaderRegistry.HeaderStore();
		StremioSourceRecord first = source("first", "fingerprint-first", "secret-first", true, 0);
		StremioSourceRecord second = source("second", "fingerprint-second", "secret-second", true, 1);
		store.reconcileSources(snapshot(1, first, second));
		var firstHeaders = store.register("first", "descriptor-first",
				Map.of("x-source", "first"), Long.MAX_VALUE);
		var secondHeaders = store.register("second", "descriptor-second",
				Map.of("x-source", "second"), Long.MAX_VALUE);

		StremioSourceRecord refreshedSecond = new StremioSourceRecord(second.sourceUuid(),
				second.transportFingerprint(), second.addonId(), "Refreshed name", "2.0",
				second.redactedTransportUrl(), second.secretRef(), true, 0, "{\"refreshed\":true}",
				"etag-2", null, 20, 20, null, 1, 20, false, false);
		StremioSourceRecord reorderedFirst = source(
				"first", "fingerprint-first", "secret-first", true, 1);
		store.reconcileSources(snapshot(2, refreshedSecond, reorderedFirst));

		assertEquals(Map.of("x-source", "first"), store.resolve(firstHeaders));
		assertEquals(Map.of("x-source", "second"), store.resolve(secondHeaders));

		StremioSourceRecord editedFirst = source(
				"first", "fingerprint-edited", "secret-edited", true, 1);
		store.reconcileSources(snapshot(3, refreshedSecond, editedFirst));

		assertThrows(PlaybackRequestValidationException.class,
				() -> store.resolve(firstHeaders));
		assertEquals(Map.of("x-source", "second"), store.resolve(secondHeaders));
	}

	private static StremioSourceSnapshot snapshot(long revision,
			StremioSourceRecord... sources) {
		return new StremioSourceSnapshot(revision, List.of(sources), true);
	}

	private static StremioSourceRecord source(String uuid, String fingerprint,
			String secretRef, boolean enabled, int position) {
		return new StremioSourceRecord(uuid, fingerprint, "addon-" + uuid, "Source " + uuid,
				"1.0", "https://example.com/manifest.json", secretRef, enabled, position, "{}",
				null, null, 1, 1, null, 1, 1, false, false);
	}
}
