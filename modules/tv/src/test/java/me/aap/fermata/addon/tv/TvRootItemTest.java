package me.aap.fermata.addon.tv;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import me.aap.utils.pref.BasicPreferenceStore;

public class TvRootItemTest extends Assert {

	@Test
	public void missingSourceTypeDefaultsToM3uForLegacySources() {
		BasicPreferenceStore ps = new BasicPreferenceStore();

		assertEquals(TvSourceItem.TYPE_M3U, TvRootItem.getSourceType(ps, 3));
	}

	@Test
	public void xtreamSourceTypeIsPreserved() {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		ps.applyStringPref(TvRootItem.sourceTypePref(4), TvSourceItem.TYPE_XTREAM);

		assertEquals(TvSourceItem.TYPE_XTREAM, TvRootItem.getSourceType(ps, 4));
	}

	@Test
	public void unknownSourceTypeFallsBackToM3u() {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		ps.applyStringPref(TvRootItem.sourceTypePref(5), "unknown");

		assertEquals(TvSourceItem.TYPE_M3U, TvRootItem.getSourceType(ps, 5));
	}

	@Test
	public void failedSourceDoesNotBlockAvailableSources() throws Exception {
		IOException expected = new IOException("unavailable");
		List<Integer> failedIds = new ArrayList<>();
		List<Throwable> failures = new ArrayList<>();

		List<String> available = TvRootItem.loadAvailableSources(List.of(1, 2, 3), id -> switch (id) {
			case 1 -> completed("one");
			case 2 -> failed(expected);
			case 3 -> completed("three");
			default -> throw new AssertionError(id);
		}, (id, failure) -> {
			failedIds.add(id);
			failures.add(failure);
		}).get();

		assertEquals(List.of("one", "three"), available);
		assertEquals(List.of(2), failedIds);
		assertEquals(List.of(expected), failures);
	}

	@Test
	public void unavailableSourcesAreReportedAndSkipped() throws Exception {
		List<Integer> failedIds = new ArrayList<>();

		List<String> available = TvRootItem.<String>loadAvailableSources(List.of(1, 2, 3), id -> switch (id) {
			case 1 -> completed("one");
			case 2 -> null;
			case 3 -> completedNull();
			default -> throw new AssertionError(id);
		}, (id, failure) -> failedIds.add(id)).get();

		assertEquals(List.of("one"), available);
		assertEquals(List.of(2, 3), failedIds);
	}

	@Test
	public void createUnavailableSourceReturnsConfiguredItem() throws Exception {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		ps.applyStringPref(TvRootItem.sourceTypePref(42), TvSourceItem.TYPE_XTREAM);
		IOException failure = new IOException("Connection refused");

		UnavailableTvSourceItem item = TvRootItem.createUnavailableSource(ps, 42, "My Xtream", failure);

		assertEquals(42, item.getSourceId());
		assertEquals(TvSourceItem.TYPE_XTREAM, item.getSourceType());
		assertEquals("My Xtream", item.getName());
		assertEquals(failure, item.getFailure());
		assertTrue(item.listChildren().get().isEmpty());
	}

	@Test
	public void createUnavailableSourceDefaultsToM3uWhenTypeMissing() throws Exception {
		UnavailableTvSourceItem item = TvRootItem.createUnavailableSource(10, "Dead Stream", null);

		assertEquals(10, item.getSourceId());
		assertEquals(TvSourceItem.TYPE_M3U, item.getSourceType());
		assertEquals("Dead Stream", item.getName());
		assertTrue(item.listChildren().get().isEmpty());
	}

	@Test
	public void unavailableSourcesMappedAndPreservedInOrder() throws Exception {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		ps.applyStringPref(TvRootItem.sourceTypePref(2), TvSourceItem.TYPE_XTREAM);
		List<Integer> ids = List.of(1, 2, 3);
		IOException failure = new IOException("Server error");
		java.util.Map<Integer, UnavailableTvSourceItem> unavailable = new java.util.HashMap<>();

		List<String> available = TvRootItem.<String>loadAvailableSources(ids, id -> switch (id) {
			case 1 -> completed("source-1");
			case 2 -> failed(failure);
			case 3 -> completed("source-3");
			default -> throw new AssertionError(id);
		}, (id, err) -> unavailable.put(id, TvRootItem.createUnavailableSource(ps, id, "Source-" + id, err))).get();

		assertEquals(List.of("source-1", "source-3"), available);
		assertEquals(1, unavailable.size());
		UnavailableTvSourceItem item2 = unavailable.get(2);
		assertNotNull(item2);
		assertEquals(2, item2.getSourceId());
		assertEquals(TvSourceItem.TYPE_XTREAM, item2.getSourceType());
		assertEquals("Source-2", item2.getName());
		assertEquals(failure, item2.getFailure());
	}
}
