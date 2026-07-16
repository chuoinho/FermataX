package me.aap.fermata.addon.podcast.provider;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import me.aap.fermata.addon.podcast.model.PodcastSearchRequest;
import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public class PodcastSearchCoordinatorTest {
	private final PodcastSearchRequest request =
			new PodcastSearchRequest("road", Locale.US, 25);

	@Test
	public void primaryResultsDoNotInvokeFallbackAndAreCached() throws Exception {
		FakeProvider primary = new FakeProvider("apple", completed(List.of(result("apple", "one"))));
		FakeProvider secondary = new FakeProvider("fyyd", completed(List.of(result("fyyd", "two"))));
		PodcastSearchCoordinator coordinator = new PodcastSearchCoordinator(primary, secondary);

		assertEquals(1, coordinator.search(request).get().size());
		assertEquals(1, coordinator.search(request).get().size());
		assertEquals(1, primary.calls);
		assertEquals(0, secondary.calls);
	}

	@Test
	public void emptyOrFailedPrimaryFallsBackWithoutMergingProviderModels() throws Exception {
		FakeProvider secondary = new FakeProvider("fyyd", completed(List.of(result("fyyd", "two"))));
		PodcastSearchCoordinator empty = new PodcastSearchCoordinator(
				new FakeProvider("apple", completed(List.of())), secondary);
		assertEquals("fyyd", empty.search(request).get().get(0).getProvider());

		PodcastSearchCoordinator failed = new PodcastSearchCoordinator(
				new FakeProvider("apple", failed(new IOException("apple down"))),
				new FakeProvider("fyyd", completed(List.of(result("fyyd", "three")))));
		assertEquals("fyyd", failed.search(request).get().get(0).getProvider());
	}

	@Test
	public void secondaryFailureDoesNotTurnAnEmptyPrimaryIntoGlobalFailure() throws Exception {
		PodcastSearchCoordinator coordinator = new PodcastSearchCoordinator(
				new FakeProvider("apple", completed(List.of())),
				new FakeProvider("fyyd", failed(new IOException("fyyd down"))));

		assertTrue(coordinator.search(request).get().isEmpty());
	}

	@Test
	public void normalizedFeedIdentityDeduplicatesResults() throws Exception {
		PodcastSearchResult first = result("apple", "same");
		PodcastSearchResult duplicate = PodcastSearchResult.create("apple", "duplicate", "Other",
				"", "", "HTTPS://FEEDS.TEST:443/same", "", "", "", 0, false);
		PodcastSearchCoordinator coordinator = new PodcastSearchCoordinator(
				new FakeProvider("apple", completed(List.of(first, duplicate))),
				new FakeProvider("fyyd", completed(List.of())));

		assertEquals(1, coordinator.search(request).get().size());
	}

	@Test
	public void concurrentIdenticalSearchesShareOneProviderRequest() throws Exception {
		Promise<List<PodcastSearchResult>> pending = new Promise<>();
		FakeProvider primary = new FakeProvider("apple", pending);
		PodcastSearchCoordinator coordinator = new PodcastSearchCoordinator(primary,
				new FakeProvider("fyyd", completed(List.of())));

		FutureSupplier<List<PodcastSearchResult>> first = coordinator.search(request);
		FutureSupplier<List<PodcastSearchResult>> second = coordinator.search(request);
		assertEquals(1, primary.calls);
		first.cancel();
		assertTrue(!pending.isCancelled());

		pending.complete(List.of(result("apple", "shared")));
		assertEquals(1, second.get().size());
	}

	@Test
	public void repeatedSecondaryFailuresOpenOnlySecondaryCircuit() throws Exception {
		FakeProvider secondary = new FakeProvider("fyyd", failed(new IOException("down")));
		PodcastSearchCoordinator coordinator = new PodcastSearchCoordinator(
				new FakeProvider("apple", completed(List.of())), secondary);

		for (int i = 0; i < 4; i++) {
			PodcastSearchRequest different =
					new PodcastSearchRequest("road " + i, Locale.US, 25);
			assertTrue(coordinator.search(different).get().isEmpty());
		}
		assertEquals(3, secondary.calls);
	}

	private static PodcastSearchResult result(String provider, String id) {
		return PodcastSearchResult.create(provider, id, "Show", "Host", "",
				"https://feeds.test/" + id, "", "", "", 0, false);
	}

	private static final class FakeProvider implements PodcastSearchProvider {
		private final String id;
		private final FutureSupplier<List<PodcastSearchResult>> response;
		int calls;

		FakeProvider(String id, FutureSupplier<List<PodcastSearchResult>> response) {
			this.id = id;
			this.response = response;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public FutureSupplier<List<PodcastSearchResult>> search(PodcastSearchRequest request) {
			calls++;
			return response;
		}
	}
}
