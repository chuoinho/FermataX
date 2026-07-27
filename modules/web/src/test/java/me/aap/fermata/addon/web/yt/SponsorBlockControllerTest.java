package me.aap.fermata.addon.web.yt;

import static me.aap.utils.async.Completed.completed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.Test;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public class SponsorBlockControllerTest {
	private static final SponsorBlockClient.Segment SEGMENT = new SponsorBlockClient.Segment(
			1.0, 2.0, SponsorBlockClient.Category.SPONSOR, "segment-1");

	@Test
	public void cacheUsesVideoAndNormalizedCategoriesUntilTtlExpires() throws Exception {
		MutableClock clock = new MutableClock(1_000L);
		FakeSource source = new FakeSource();
		source.enqueue(completed(List.of(SEGMENT)));
		source.enqueue(completed(List.of()));
		SponsorBlockController controller = new SponsorBlockController(source, clock);
		SponsorBlockClient.Request first = request("video-1", SponsorBlockClient.Category.INTRO,
				SponsorBlockClient.Category.SPONSOR);
		SponsorBlockClient.Request sameKey = request("video-1", SponsorBlockClient.Category.SPONSOR,
				SponsorBlockClient.Category.INTRO);

		List<SponsorBlockClient.Segment> loaded = controller.getSegments(first).get();
		assertEquals(loaded, controller.getSegments(sameKey).get());
		assertEquals(1, source.calls);

		clock.set(1_000L + SponsorBlockController.CACHE_TTL_MS - 1L);
		assertEquals(loaded, controller.getSegments(first).get());
		assertEquals(1, source.calls);

		clock.set(1_000L + SponsorBlockController.CACHE_TTL_MS);
		assertTrue(controller.getSegments(first).get().isEmpty());
		assertEquals(2, source.calls);
	}

	@Test
	public void emptyResultsUseShorterTtl() throws Exception {
		MutableClock clock = new MutableClock(1_000L);
		FakeSource source = new FakeSource();
		source.enqueue(completed(List.of()));
		source.enqueue(completed(List.of(SEGMENT)));
		SponsorBlockController controller = new SponsorBlockController(source, clock);
		SponsorBlockClient.Request request = request("video-1",
				SponsorBlockClient.Category.SPONSOR);

		assertTrue(controller.getSegments(request).get().isEmpty());
		clock.set(1_000L + SponsorBlockController.EMPTY_CACHE_TTL_MS - 1L);
		assertTrue(controller.getSegments(request).get().isEmpty());
		assertEquals(1, source.calls);

		clock.set(1_000L + SponsorBlockController.EMPTY_CACHE_TTL_MS);
		assertEquals(List.of(SEGMENT), controller.getSegments(request).get());
		assertEquals(2, source.calls);
	}

	@Test
	public void cacheKeySeparatesVideoAndCategorySelection() throws Exception {
		FakeSource source = new FakeSource();
		source.enqueue(completed(List.of(SEGMENT)));
		source.enqueue(completed(List.of()));
		source.enqueue(completed(List.of()));
		SponsorBlockController controller = new SponsorBlockController(source, () -> 0L);

		controller.getSegments(request("video-1", SponsorBlockClient.Category.SPONSOR)).get();
		controller.getSegments(request("video-1", SponsorBlockClient.Category.INTRO)).get();
		controller.getSegments(request("video-2", SponsorBlockClient.Category.SPONSOR)).get();

		assertEquals(3, source.calls);
	}

	@Test
	public void singleFlightCancelsUpstreamOnlyAfterLastSubscriber() throws Exception {
		FakeSource source = new FakeSource();
		Promise<List<SponsorBlockClient.Segment>> upstream = new Promise<>();
		source.enqueue(upstream);
		SponsorBlockController controller = new SponsorBlockController(source, () -> 0L);
		SponsorBlockClient.Request request = request("video-1",
				SponsorBlockClient.Category.SPONSOR);

		FutureSupplier<List<SponsorBlockClient.Segment>> first = controller.getSegments(request);
		FutureSupplier<List<SponsorBlockClient.Segment>> second = controller.getSegments(request);
		assertEquals(1, source.calls);

		assertTrue(first.cancel(true));
		assertFalse(upstream.isCancelled());
		upstream.complete(List.of(SEGMENT));
		assertEquals(List.of(SEGMENT), second.get());
		assertTrue(first.isCancelled());
	}

	@Test
	public void concurrentCallersReserveOneFlightBeforeSourceReturns() throws Exception {
		Promise<List<SponsorBlockClient.Segment>> upstream = new Promise<>();
		BlockingSource source = new BlockingSource(upstream);
		SponsorBlockController controller = new SponsorBlockController(source, () -> 0L);
		SponsorBlockClient.Request request = request("video-1",
				SponsorBlockClient.Category.SPONSOR);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<FutureSupplier<List<SponsorBlockClient.Segment>>> firstCall =
					executor.submit(() -> controller.getSegments(request));
			assertTrue(source.entered.await(2, TimeUnit.SECONDS));

			FutureSupplier<List<SponsorBlockClient.Segment>> second = controller.getSegments(request);
			assertEquals(1, source.calls.get());

			source.release.countDown();
			FutureSupplier<List<SponsorBlockClient.Segment>> first = firstCall.get(2, TimeUnit.SECONDS);
			upstream.complete(List.of(SEGMENT));

			assertEquals(List.of(SEGMENT), first.get());
			assertEquals(List.of(SEGMENT), second.get());
			assertEquals(1, source.calls.get());
		} finally {
			source.release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	public void finalSubscriberCancellationPropagatesAndDoesNotPopulateCache() {
		FakeSource source = new FakeSource();
		Promise<List<SponsorBlockClient.Segment>> upstream = new Promise<>();
		source.enqueue(upstream);
		SponsorBlockController controller = new SponsorBlockController(source, () -> 0L);
		SponsorBlockClient.Request request = request("video-1",
				SponsorBlockClient.Category.SPONSOR);

		FutureSupplier<List<SponsorBlockClient.Segment>> first = controller.getSegments(request);
		FutureSupplier<List<SponsorBlockClient.Segment>> second = controller.getSegments(request);
		first.cancel(true);
		second.cancel(true);

		assertTrue(upstream.isCancelled());
		assertEquals(1, source.calls);
	}

	@Test
	public void canceledFlightIsReplacedByFreshRequest() throws Exception {
		FakeSource source = new FakeSource();
		Promise<List<SponsorBlockClient.Segment>> canceled = new Promise<>();
		Promise<List<SponsorBlockClient.Segment>> replacement = new Promise<>();
		source.enqueue(canceled);
		source.enqueue(replacement);
		SponsorBlockController controller = new SponsorBlockController(source, () -> 0L);
		SponsorBlockClient.Request request = request("video-1",
				SponsorBlockClient.Category.SPONSOR);

		FutureSupplier<List<SponsorBlockClient.Segment>> first = controller.getSegments(request);
		assertTrue(first.cancel(true));
		assertTrue(canceled.isCancelled());

		FutureSupplier<List<SponsorBlockClient.Segment>> second = controller.getSegments(request);
		assertEquals(2, source.calls);
		replacement.complete(List.of(SEGMENT));
		assertEquals(List.of(SEGMENT), second.get());
	}

	@Test
	public void closeCancelsFlightsAndRejectsNewLoads() {
		FakeSource source = new FakeSource();
		Promise<List<SponsorBlockClient.Segment>> upstream = new Promise<>();
		source.enqueue(upstream);
		SponsorBlockController controller = new SponsorBlockController(source, () -> 0L);
		SponsorBlockClient.Request request = request("video-1",
				SponsorBlockClient.Category.SPONSOR);
		controller.getSegments(request);

		controller.close();

		assertTrue(upstream.isCancelled());
		assertTrue(controller.getSegments(request).isFailed());
		assertEquals(1, source.calls);
	}

	private static SponsorBlockClient.Request request(String videoId,
			SponsorBlockClient.Category... categories) {
		return new SponsorBlockClient.Request(videoId, EnumSet.copyOf(List.of(categories)));
	}

	private static final class MutableClock implements LongSupplier {
		private final AtomicLong value;

		MutableClock(long value) {
			this.value = new AtomicLong(value);
		}

		void set(long value) {
			this.value.set(value);
		}

		@Override
		public long getAsLong() {
			return value.get();
		}
	}

	private static final class FakeSource implements SponsorBlockController.Source {
		private final Queue<FutureSupplier<List<SponsorBlockClient.Segment>>> responses =
				new ArrayDeque<>();
		private int calls;

		void enqueue(FutureSupplier<List<SponsorBlockClient.Segment>> response) {
			responses.add(response);
		}

		@Override
		public FutureSupplier<List<SponsorBlockClient.Segment>> load(
				SponsorBlockClient.Request request) {
			calls++;
			FutureSupplier<List<SponsorBlockClient.Segment>> response = responses.poll();
			if (response == null) throw new AssertionError("Unexpected request: " + request);
			return response;
		}
	}

	private static final class BlockingSource implements SponsorBlockController.Source {
		private final FutureSupplier<List<SponsorBlockClient.Segment>> response;
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicInteger calls = new AtomicInteger();

		BlockingSource(FutureSupplier<List<SponsorBlockClient.Segment>> response) {
			this.response = response;
		}

		@Override
		public FutureSupplier<List<SponsorBlockClient.Segment>> load(
				SponsorBlockClient.Request request) {
			calls.incrementAndGet();
			entered.countDown();
			try {
				if (!release.await(2, TimeUnit.SECONDS))
					throw new AssertionError("Timed out waiting to release source");
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new AssertionError(error);
			}
			return response;
		}
	}
}
