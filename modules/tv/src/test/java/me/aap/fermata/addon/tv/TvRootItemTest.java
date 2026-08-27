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
}
