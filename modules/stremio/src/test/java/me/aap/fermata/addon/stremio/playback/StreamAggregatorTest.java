package me.aap.fermata.addon.stremio.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ExternalStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ProxyHeaders;
import me.aap.fermata.addon.stremio.protocol.response.StreamBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.fermata.addon.stremio.protocol.response.YoutubeStreamTarget;

public class StreamAggregatorTest {
	private ExecutorService executor;
	private ScheduledExecutorService scheduler;
	private FakeClient client;

	@Before
	public void setUp() {
		executor = Executors.newFixedThreadPool(4);
		scheduler = Executors.newScheduledThreadPool(2);
		client = new FakeClient();
	}

	@After
	public void tearDown() throws Exception {
		executor.shutdownNow();
		scheduler.shutdownNow();
		executor.awaitTermination(2, TimeUnit.SECONDS);
		scheduler.awaitTermination(2, TimeUnit.SECONDS);
	}

	@Test
	public void observerReceivesFiniteIncrementalProviderStatesAndFinalSnapshot()
			throws Exception {
		FakeCall first = client.enqueue("p1");
		FakeCall second = client.enqueue("p2");
		StreamAggregator aggregator = aggregator(2, 2_000L);
		StreamAggregationCall call = aggregator.aggregate(request(), List.of(
				provider("p1", 0, true), provider("p2", 1, true)));
		var snapshots = new LinkedBlockingQueue<StreamAggregationResult>();
		AutoCloseable observation = call.observe(snapshots::add);

		StreamAggregationResult loading = snapshots.poll(2, TimeUnit.SECONDS);
		assertTrue(loading.providerGroups().stream().allMatch(group ->
				group.loadState() instanceof me.aap.fermata.addon.stremio.lifecycle.ProviderLoadState.Loading));
		long operationId = loading.providerGroups().get(0).operationId();
		assertTrue(operationId > 0L);
		assertEquals(operationId, loading.providerGroups().get(1).operationId());

		client.awaitStarted("p1");
		client.awaitStarted("p2");
		second.response.complete(List.of(stream("second",
				new DirectStreamTarget("https://cdn.invalid/second.mp4"))));
		StreamAggregationResult partial = snapshots.poll(2, TimeUnit.SECONDS);
		assertEquals(StreamAggregationResult.ProviderStatus.PENDING,
				partial.providerGroups().get(0).status());
		assertEquals(StreamAggregationResult.ProviderStatus.SUCCESS,
				partial.providerGroups().get(1).status());

		first.response.complete(List.of(stream("first",
				new DirectStreamTarget("https://cdn.invalid/first.mp4"))));
		StreamAggregationResult completed = call.completion().get(2, TimeUnit.SECONDS);
		StreamAggregationResult published = snapshots.poll(2, TimeUnit.SECONDS);
		assertEquals(completed, published);
		assertTrue(completed.providerGroups().stream().allMatch(group ->
				group.loadState() instanceof me.aap.fermata.addon.stremio.lifecycle.ProviderLoadState.Ready));

		observation.close();
		aggregator.close();
	}

	@Test
	public void aggregatesFourProvidersWithBoundedConcurrencyFailureIsolationAndStableRanking()
			throws Exception {
		FakeCall first = client.enqueue("p1");
		FakeCall failed = client.enqueue("p2");
		FakeCall duplicate = client.enqueue("p3");
		FakeCall fourth = client.enqueue("p4");
		StreamAggregator aggregator = aggregator(2, 2_000L);
		List<StreamProvider> providers = List.of(
				provider("p3", 2, true), provider("disabled", 0, false),
				provider("p1", 0, true), provider("p4", 3, true), provider("p2", 1, true));

		StreamAggregationCall call = aggregator.aggregate(request(), providers);
		client.awaitStarted("p1");
		client.awaitStarted("p2");
		assertEquals(2, client.maxInFlight());
		failed.response.completeExceptionally(new IllegalStateException("provider failure URL secret"));
		client.awaitStarted("p3");
		duplicate.response.complete(List.of(
				stream("duplicate", new DirectStreamTarget("https://cdn.invalid/a.m3u8"))));
		client.awaitStarted("p4");
		fourth.response.complete(List.of(
				stream("provider-four", new DirectStreamTarget("https://cdn.invalid/four.mp4"))));
		first.response.complete(List.of(
				stream("External", new ExternalStreamTarget("https://external.invalid/watch")),
				stream("Torrent", new InfoHashStreamTarget("abcdef", 0, List.of())),
				stream("HTTP", new DirectStreamTarget("https://cdn.invalid/a.mp4")),
				stream("YouTube", new YoutubeStreamTarget("video-id")),
				stream("DASH", new DirectStreamTarget("https://cdn.invalid/a.mpd")),
				stream("Z duplicate", new DirectStreamTarget("https://cdn.invalid/a.m3u8")),
				stream("A HLS", new DirectStreamTarget("https://cdn.invalid/a.m3u8"))));

		StreamAggregationResult result = call.response().get(2, TimeUnit.SECONDS);
		assertEquals(List.of("p1", "p2", "p3", "p4"), result.providerGroups().stream()
				.map(group -> group.provider().sourceUuid()).toList());
		assertEquals(List.of(
				StreamAggregationResult.ProviderStatus.SUCCESS,
				StreamAggregationResult.ProviderStatus.FAILED,
				StreamAggregationResult.ProviderStatus.SUCCESS,
				StreamAggregationResult.ProviderStatus.SUCCESS),
				result.providerGroups().stream().map(StreamAggregationResult.ProviderGroup::status).toList());
		assertEquals(List.of(
				PlaybackDescriptor.TargetKind.HLS,
				PlaybackDescriptor.TargetKind.DASH,
				PlaybackDescriptor.TargetKind.DIRECT_HTTP,
				PlaybackDescriptor.TargetKind.TORRENT,
				PlaybackDescriptor.TargetKind.UNSUPPORTED),
				result.providerGroups().get(0).descriptors().stream()
						.map(PlaybackDescriptor::targetKind).toList());
		assertEquals("A HLS", result.providerGroups().get(0).descriptors().get(0).streamName());
		assertFalse(result.descriptors().stream().anyMatch(descriptor ->
				"YouTube".equals(descriptor.streamName())));
		assertEquals(1, result.providerGroups().get(2).descriptors().size());
		assertEquals(6, result.descriptors().size());
		assertTrue(result.hasPartialFailures());
		assertTrue(client.maxInFlight() <= 2);
		assertFalse(client.started().contains("disabled"));
		assertThrows(UnsupportedOperationException.class,
				() -> result.providerGroups().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> result.providerGroups().get(0).descriptors().clear());
		aggregator.close();
	}

	@Test
	public void duplicateTorrentMergesTrackersAndRetainsContributingProviders()
			throws Exception {
		FakeCall first = client.enqueue("p1");
		FakeCall second = client.enqueue("p2");
		StreamAggregator aggregator = aggregator(2, 2_000L);
		StreamAggregationCall call = aggregator.aggregate(request(), List.of(
				provider("p1", 0, true), provider("p2", 1, true)));
		client.awaitStarted("p1");
		client.awaitStarted("p2");
		first.response.complete(List.of(stream("first", new InfoHashStreamTarget(
				"abcdef", 4, List.of("tracker:https://one.invalid/announce")))));
		second.response.complete(List.of(stream("second", new InfoHashStreamTarget(
				"ABCDEF", 4, List.of("tracker:https://two.invalid/announce",
						"tracker:https://one.invalid/announce")))));

		StreamAggregationResult result = call.completion().get(2, TimeUnit.SECONDS);

		assertEquals(1, result.descriptors().size());
		assertEquals(1, result.providerGroups().get(0).descriptors().size());
		assertEquals(1, result.providerGroups().get(1).descriptors().size());
		PlaybackDescriptor descriptor = result.descriptors().get(0);
		assertEquals(List.of("p1", "p2"), descriptor.contributingProviderSourceUuids());
		InfoHashStreamTarget merged = (InfoHashStreamTarget) descriptor.sourceTarget();
		assertEquals(List.of("tracker:https://one.invalid/announce",
				"tracker:https://two.invalid/announce"), merged.sources());
		aggregator.close();
	}

	@Test
	public void malformedChoiceDoesNotHideHealthyChoiceFromSameProvider() throws Exception {
		FakeCall providerCall = client.enqueue("provider");
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(
				(a, b, c, d) -> { throw new IllegalArgumentException("bad header registry"); });
		StreamAggregator aggregator = new StreamAggregator(client, factory, executor, scheduler,
				() -> 1_000_000L, 1, 2_000L);
		StreamAggregationCall call = aggregator.aggregate(request(),
				List.of(provider("provider", 0, true)));
		client.awaitStarted("provider");
		providerCall.response.complete(List.of(
				new StremioStream("bad", "bad", null,
						new DirectStreamTarget("https://cdn.invalid/bad.m3u8"),
						new StreamBehaviorHints(false, null, null, null,
								new ProxyHeaders(Map.of("Authorization", "secret"), Map.of()))),
				stream("healthy", new DirectStreamTarget("https://cdn.invalid/healthy.mp4"))));

		StreamAggregationResult result = call.response().get(2, TimeUnit.SECONDS);
		assertEquals(StreamAggregationResult.ProviderStatus.SUCCESS,
				result.providerGroups().get(0).status());
		assertEquals(1, result.descriptors().size());
		assertEquals("healthy", result.descriptors().get(0).streamName());
		aggregator.close();
	}

	@Test
	public void timesOutOneProviderAndIgnoresItsLateResponse() throws Exception {
		FakeCall slow = client.enqueue("slow");
		FakeCall healthy = client.enqueue("healthy");
		StreamAggregator aggregator = aggregator(2, 80L);
		StreamAggregationCall call = aggregator.aggregate(request(),
				List.of(provider("slow", 0, true), provider("healthy", 1, true)));
		client.awaitStarted("slow");
		client.awaitStarted("healthy");
		healthy.response.complete(List.of(
				stream("healthy", new DirectStreamTarget("https://cdn.invalid/healthy.mp4"))));

		StreamAggregationResult result = call.response().get(2, TimeUnit.SECONDS);
		assertEquals(StreamAggregationResult.ProviderStatus.TIMED_OUT,
				result.providerGroups().get(0).status());
		assertEquals(StreamAggregationResult.ProviderStatus.SUCCESS,
				result.providerGroups().get(1).status());
		assertTrue(slow.cancelled);
		assertEquals(1, result.descriptors().size());

		slow.response.complete(List.of(
				stream("late", new DirectStreamTarget("https://cdn.invalid/late.mp4"))));
		Thread.sleep(30L);
		assertEquals(1, result.descriptors().size());
		aggregator.close();
	}

	@Test
	public void settlesInteractiveResultThenAppendsLateProviderWithoutReordering()
			throws Exception {
		FakeCall preferredLate = client.enqueue("preferred-late");
		FakeCall visibleFirst = client.enqueue("visible-first");
		StreamAggregator aggregator = aggregator(2, 2_000L, 80L);
		StreamAggregationCall call = aggregator.aggregate(request(), List.of(
				provider("preferred-late", 0, true),
				provider("visible-first", 1, true)));
		client.awaitStarted("preferred-late");
		client.awaitStarted("visible-first");
		visibleFirst.response.complete(List.of(stream("visible",
				new DirectStreamTarget("https://cdn.invalid/visible.mp4"))));

		StreamAggregationResult initial = call.response().get(1, TimeUnit.SECONDS);
		assertEquals(List.of("visible"), initial.descriptors().stream()
				.map(PlaybackDescriptor::streamName).toList());
		assertEquals(StreamAggregationResult.ProviderStatus.PENDING,
				initial.providerGroups().get(0).status());
		assertFalse(preferredLate.cancelled);
		assertFalse(call.completion().isDone());

		preferredLate.response.complete(List.of(stream("late",
				new DirectStreamTarget("https://cdn.invalid/late.mp4"))));
		StreamAggregationResult completed = call.completion().get(1, TimeUnit.SECONDS);
		assertEquals(List.of("visible", "late"), completed.descriptors().stream()
				.map(PlaybackDescriptor::streamName).toList());
		assertEquals(List.of("preferred-late", "visible-first"),
				completed.providerGroups().stream()
						.map(group -> group.provider().sourceUuid()).toList());
		aggregator.close();
	}

	@Test
	public void publishesAllLateSuccessesInOneFinalBatch() throws Exception {
		FakeCall firstLate = client.enqueue("first-late");
		FakeCall visible = client.enqueue("visible");
		FakeCall secondLate = client.enqueue("second-late");
		StreamAggregator aggregator = aggregator(3, 2_000L, 60L);
		StreamAggregationCall call = aggregator.aggregate(request(), List.of(
				provider("first-late", 0, true), provider("visible", 1, true),
				provider("second-late", 2, true)));
		client.awaitStarted("first-late");
		client.awaitStarted("visible");
		client.awaitStarted("second-late");
		visible.response.complete(List.of(stream("visible",
				new DirectStreamTarget("https://cdn.invalid/visible.mp4"))));
		call.response().get(1, TimeUnit.SECONDS);
		AtomicInteger finalPublications = new AtomicInteger();
		call.completion().thenAccept(ignored -> finalPublications.incrementAndGet());

		secondLate.response.complete(List.of(stream("second",
				new DirectStreamTarget("https://cdn.invalid/second.mp4"))));
		Thread.sleep(30L);
		assertFalse(call.completion().isDone());
		firstLate.response.complete(List.of(stream("first",
				new DirectStreamTarget("https://cdn.invalid/first.mp4"))));

		StreamAggregationResult completed = call.completion().get(1, TimeUnit.SECONDS);
		awaitCondition(() -> finalPublications.get() == 1);
		assertEquals(1, finalPublications.get());
		assertEquals(List.of("visible", "first", "second"), completed.descriptors().stream()
				.map(PlaybackDescriptor::streamName).toList());
		aggregator.close();
	}

	@Test
	public void concurrentAggregationDoesNotCancelSettledLateBatch() throws Exception {
		FakeCall oldPending = client.enqueue("old-pending");
		FakeCall oldVisible = client.enqueue("old-visible");
		FakeCall current = client.enqueue("current");
		StreamAggregator aggregator = aggregator(2, 2_000L, 60L);
		StreamAggregationCall old = aggregator.aggregate(request(), List.of(
				provider("old-pending", 0, true), provider("old-visible", 1, true)));
		client.awaitStarted("old-pending");
		client.awaitStarted("old-visible");
		oldVisible.response.complete(List.of(stream("old-visible",
				new DirectStreamTarget("https://cdn.invalid/old-visible.mp4"))));
		assertEquals(1, old.response().get(1, TimeUnit.SECONDS).descriptors().size());

		StreamAggregationCall replacement = aggregator.aggregate(request(),
				List.of(provider("current", 0, true)));
		client.awaitStarted("current");
		oldPending.response.complete(List.of(stream("stale-late",
				new DirectStreamTarget("https://cdn.invalid/stale-late.mp4"))));
		current.response.complete(List.of(stream("current",
				new DirectStreamTarget("https://cdn.invalid/current.mp4"))));

		assertFalse(oldPending.cancelled);
		assertEquals(List.of("old-visible", "stale-late"),
				old.completion().get(1, TimeUnit.SECONDS).descriptors().stream()
						.map(PlaybackDescriptor::streamName).toList());
		assertEquals("current", replacement.completion().get(1, TimeUnit.SECONDS)
				.descriptors().get(0).streamName());
		aggregator.close();
	}

	@Test
	public void allProvidersBeforeSettleShareSingleCompleteResult() throws Exception {
		FakeCall provider = client.enqueue("provider");
		StreamAggregator aggregator = aggregator(1, 2_000L, 500L);
		StreamAggregationCall call = aggregator.aggregate(request(),
				List.of(provider("provider", 0, true)));
		client.awaitStarted("provider");
		provider.response.complete(List.of(stream("ready",
				new DirectStreamTarget("https://cdn.invalid/ready.mp4"))));

		StreamAggregationResult initial = call.response().get(1, TimeUnit.SECONDS);
		StreamAggregationResult completed = call.completion().get(1, TimeUnit.SECONDS);
		assertEquals(initial, completed);
		assertEquals(StreamAggregationResult.ProviderStatus.SUCCESS,
				initial.providerGroups().get(0).status());
		aggregator.close();
	}

	@Test
	public void cancellationAfterSettleStopsUnfinishedProvidersAndFinalPublication()
			throws Exception {
		FakeCall pending = client.enqueue("pending");
		FakeCall visible = client.enqueue("visible");
		StreamAggregator aggregator = aggregator(2, 2_000L, 60L);
		StreamAggregationCall call = aggregator.aggregate(request(), List.of(
				provider("pending", 0, true), provider("visible", 1, true)));
		client.awaitStarted("pending");
		client.awaitStarted("visible");
		visible.response.complete(List.of(stream("visible",
				new DirectStreamTarget("https://cdn.invalid/visible.mp4"))));
		assertEquals(1, call.response().get(1, TimeUnit.SECONDS).descriptors().size());

		call.cancel();
		awaitCondition(() -> pending.cancelled);
		assertThrows(CancellationException.class,
				() -> call.completion().get(1, TimeUnit.SECONDS));
		pending.response.complete(List.of(stream("must-not-publish",
				new DirectStreamTarget("https://cdn.invalid/stale.mp4"))));
		Thread.sleep(30L);
		assertTrue(call.completion().isCompletedExceptionally());
		aggregator.close();
	}

	@Test
	public void concurrentAggregationsCompleteIndependently() throws Exception {
		FakeCall oldCall = client.enqueue("provider");
		FakeCall newCall = client.enqueue("provider");
		StreamAggregator aggregator = aggregator(1, 2_000L);
		List<StreamProvider> providers = List.of(provider("provider", 0, true));
		StreamAggregationCall oldAggregation = aggregator.aggregate(request(), providers);
		client.awaitStartCount("provider", 1);
		StreamAggregationCall newAggregation = aggregator.aggregate(request(), providers);
		client.awaitStartCount("provider", 2);
		oldCall.response.complete(List.of(
				stream("old", new DirectStreamTarget("https://cdn.invalid/old.mp4"))));
		newCall.response.complete(List.of(
				stream("new", new DirectStreamTarget("https://cdn.invalid/new.mp4"))));
		assertFalse(oldCall.cancelled);
		assertEquals("old", oldAggregation.response().get(2, TimeUnit.SECONDS)
				.descriptors().get(0).streamName());
		StreamAggregationResult current = newAggregation.response().get(2, TimeUnit.SECONDS);
		assertEquals("new", current.descriptors().get(0).streamName());
		assertFalse(current.descriptors().get(0).targetValue().contains("old.mp4"));
		aggregator.close();
	}

	@Test
	public void explicitCancellationCancelsEveryStartedProvider() throws Exception {
		FakeCall first = client.enqueue("p1");
		FakeCall second = client.enqueue("p2");
		StreamAggregator aggregator = aggregator(2, 2_000L);
		StreamAggregationCall call = aggregator.aggregate(request(),
				List.of(provider("p1", 0, true), provider("p2", 1, true)));
		client.awaitStarted("p1");
		client.awaitStarted("p2");
		call.cancel();
		awaitCondition(() -> first.cancelled && second.cancelled);
		assertThrows(CancellationException.class,
				() -> call.response().get(2, TimeUnit.SECONDS));
		aggregator.close();
	}

	private StreamAggregator aggregator(int concurrency, long timeoutMillis) {
		return new StreamAggregator(client,
				new PlaybackDescriptorFactory((a, b, c, d) -> null),
				executor, scheduler, () -> 1_000_000L, concurrency, timeoutMillis);
	}

	private StreamAggregator aggregator(
			int concurrency, long timeoutMillis, long settleMillis) {
		return new StreamAggregator(client,
				new PlaybackDescriptorFactory((a, b, c, d) -> null),
				executor, scheduler, () -> 1_000_000L, concurrency, timeoutMillis,
				settleMillis);
	}

	private static StreamProvider provider(String id, int position, boolean enabled) {
		return new StreamProvider(id, "addon." + id, "Provider " + id, position, enabled);
	}

	private static StreamAggregationRequest request() {
		return new StreamAggregationRequest(
				StremioPlaybackIdentity.canonical("movie", "tt123", "tt123"),
				"movie", "tt123", "tt123",
				new StremioPlaybackMetadata("Exact Movie", "https://img.invalid/movie.jpg", 90_000L));
	}

	private static StremioStream stream(String name,
			me.aap.fermata.addon.stremio.protocol.response.StreamTarget target) {
		return new StremioStream(name, name + " title", null, target, StreamBehaviorHints.EMPTY);
	}

	private static void awaitCondition(Check condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (!condition.done()) {
			if (System.nanoTime() >= deadline) throw new AssertionError("condition timed out");
			Thread.sleep(5L);
		}
	}

	@FunctionalInterface
	private interface Check {
		boolean done();
	}

	private static final class FakeClient implements StreamProviderClient {
		private final Map<String, Queue<FakeCall>> queued = new HashMap<>();
		private final List<String> started = new ArrayList<>();
		private final List<FakeCall> active = new ArrayList<>();
		private int maxInFlight;

		private synchronized FakeCall enqueue(String provider) {
			FakeCall call = new FakeCall();
			queued.computeIfAbsent(provider, ignored -> new ArrayDeque<>()).add(call);
			return call;
		}

		@Override
		public synchronized ProviderStreamCall fetch(
				StreamProvider provider, StreamAggregationRequest request) {
			Queue<FakeCall> calls = queued.get(provider.sourceUuid());
			if ((calls == null) || calls.isEmpty()) throw new AssertionError("No call for provider");
			FakeCall call = calls.remove();
			started.add(provider.sourceUuid());
			active.removeIf(candidate -> candidate.response.isDone());
			active.add(call);
			maxInFlight = Math.max(maxInFlight, active.size());
			notifyAll();
			return call;
		}

		private synchronized void awaitStarted(String provider) throws Exception {
			awaitStartCount(provider, 1);
		}

		private synchronized void awaitStartCount(String provider, int count) throws Exception {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
			while (started.stream().filter(provider::equals).count() < count) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) throw new AssertionError("provider did not start: " + provider);
				TimeUnit.NANOSECONDS.timedWait(this, remaining);
			}
		}

		private synchronized int maxInFlight() {
			return maxInFlight;
		}

		private synchronized List<String> started() {
			return List.copyOf(started);
		}
	}

	private static final class FakeCall implements ProviderStreamCall {
		private final CompletableFuture<List<StremioStream>> response = new CompletableFuture<>();
		private volatile boolean cancelled;

		@Override
		public CompletableFuture<List<StremioStream>> response() {
			return response;
		}

		@Override
		public void cancel() {
			cancelled = true;
		}
	}
}
