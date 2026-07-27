package me.aap.fermata.addon.stremio.playback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.utils.log.Log;

/** Concurrent, bounded and independently cancellable aggregation over enabled providers. */
public final class StreamAggregator implements AutoCloseable {
	public static final int MAX_PROVIDERS = 32;
	public static final int MAX_STREAMS_PER_PROVIDER = 200;
	public static final long DEFAULT_INITIAL_SETTLE_MILLIS = 2_500L;
	public static final long INCREMENTAL_THROTTLE_MILLIS = 150L;

	private final StreamProviderClient client;
	private final PlaybackDescriptorFactory descriptorFactory;
	private final Executor executor;
	private final ScheduledExecutorService scheduler;
	private final LongSupplier clock;
	private final int maxConcurrent;
	private final long providerTimeoutMillis;
	private final long initialSettleMillis;
	private final Set<ActiveAggregation> active = new HashSet<>();
	private final AtomicLong operationIds = new AtomicLong();
	private boolean closed;

	public StreamAggregator(StreamProviderClient client,
			PlaybackDescriptorFactory descriptorFactory, Executor executor,
			ScheduledExecutorService scheduler, LongSupplier clock,
			int maxConcurrent, long providerTimeoutMillis) {
		this(client, descriptorFactory, executor, scheduler, clock, maxConcurrent,
				providerTimeoutMillis, DEFAULT_INITIAL_SETTLE_MILLIS);
	}

	public StreamAggregator(StreamProviderClient client,
			PlaybackDescriptorFactory descriptorFactory, Executor executor,
			ScheduledExecutorService scheduler, LongSupplier clock,
			int maxConcurrent, long providerTimeoutMillis, long initialSettleMillis) {
		this.client = Objects.requireNonNull(client, "client");
		this.descriptorFactory = Objects.requireNonNull(descriptorFactory, "descriptorFactory");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		this.clock = Objects.requireNonNull(clock, "clock");
		if ((maxConcurrent < 1) || (maxConcurrent > 8)) {
			throw new IllegalArgumentException("maxConcurrent must be between 1 and 8");
		}
		if (providerTimeoutMillis <= 0) {
			throw new IllegalArgumentException("providerTimeoutMillis must be positive");
		}
		if (initialSettleMillis <= 0) {
			throw new IllegalArgumentException("initialSettleMillis must be positive");
		}
		this.maxConcurrent = maxConcurrent;
		this.providerTimeoutMillis = providerTimeoutMillis;
		this.initialSettleMillis = initialSettleMillis;
	}

	public synchronized StreamAggregationCall aggregate(
			StreamAggregationRequest request, List<StreamProvider> providers) {
		if (closed) throw new IllegalStateException("StreamAggregator is closed");
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(providers, "providers");
		if (providers.size() > MAX_PROVIDERS) {
			throw new IllegalArgumentException("Too many stream providers");
		}

		List<StreamProvider> enabled = providers.stream()
				.filter(StreamProvider::enabled)
				.sorted(Comparator.comparingInt(StreamProvider::position)
						.thenComparing(StreamProvider::sourceUuid))
				.toList();
		ActiveAggregation next = new ActiveAggregation(request, enabled);
		active.add(next);
		next.start();
		return next;
	}

	@Override
	public void close() {
		List<ActiveAggregation> running;
		synchronized (this) {
			if (closed) return;
			closed = true;
			running = List.copyOf(active);
			active.clear();
		}
		for (ActiveAggregation aggregation : running) aggregation.cancel();
	}

	private synchronized void retire(ActiveAggregation aggregation) {
		active.remove(aggregation);
	}

	private final class ActiveAggregation implements StreamAggregationCall {
		private final StreamAggregationRequest request;
		private final long operationId = operationIds.incrementAndGet();
		private final List<StreamProvider> providers;
		private final CompletableFuture<StreamAggregationResult> response = new CompletableFuture<>();
		private final CompletableFuture<StreamAggregationResult> completion =
				new CompletableFuture<>();
		private final RawOutcome[] outcomes;
		private final ProviderStreamCall[] calls;
		private final ScheduledFuture<?>[] timeouts;
		private ScheduledFuture<?> settleTimeout;
		private ScheduledFuture<?> incrementalTimeout;
		private final Set<Consumer<StreamAggregationResult>> observers = new LinkedHashSet<>();
		private StreamAggregationResult latestSnapshot;
		private long lastSnapshotAt = Long.MIN_VALUE;
		private List<RankedDescriptor> exposed = List.of();
		private int nextIndex;
		private int running;
		private int completed;
		private boolean cancelled;
		private boolean initialBuildInProgress;
		private boolean finishing;

		private ActiveAggregation(StreamAggregationRequest request,
				List<StreamProvider> providers) {
			this.request = request;
			this.providers = providers;
			outcomes = new RawOutcome[providers.size()];
			calls = new ProviderStreamCall[providers.size()];
			timeouts = new ScheduledFuture<?>[providers.size()];
		}

		private synchronized void start() {
			settleTimeout = scheduler.schedule(
					this::settle, initialSettleMillis, TimeUnit.MILLISECONDS);
			pump();
		}

		private synchronized void pump() {
			if (cancelled) return;
			while ((running < maxConcurrent) && (nextIndex < providers.size())) {
				int index = nextIndex++;
				running++;
				timeouts[index] = scheduler.schedule(
						() -> timeout(index), providerTimeoutMillis, TimeUnit.MILLISECONDS);
				executor.execute(() -> start(index));
			}
			maybeFinish();
		}

		private void start(int index) {
			ProviderStreamCall call;
			try {
				call = Objects.requireNonNull(client.fetch(providers.get(index), request),
						"provider call");
				Objects.requireNonNull(call.response(), "provider response")
						.whenComplete((streams, failure) -> complete(index, streams, failure));
			} catch (RuntimeException failure) {
				complete(index, null, failure);
				return;
			}
			synchronized (this) {
				if (cancelled || (outcomes[index] != null)) {
					call.cancel();
				} else {
					calls[index] = call;
				}
			}
		}

		private void complete(int index, List<StremioStream> streams, Throwable failure) {
			RawOutcome outcome = (failure == null) ? success(index, streams) : RawOutcome.failed();
			synchronized (this) {
				if (cancelled || (outcomes[index] != null)) return;
				ScheduledFuture<?> timeout = timeouts[index];
				if (timeout != null) timeout.cancel(false);
				outcomes[index] = outcome;
				running--;
				completed++;
				scheduleIncremental();
				pump();
			}
		}

		private RawOutcome success(int index, List<StremioStream> streams) {
			try {
				List<StremioStream> copy = List.copyOf(
						Objects.requireNonNull(streams, "provider streams"));
				if (copy.size() > MAX_STREAMS_PER_PROVIDER) {
					copy = List.copyOf(copy.subList(0, MAX_STREAMS_PER_PROVIDER));
				}
				long now = clock.getAsLong();
				List<PlaybackDescriptorFactory.Candidate> candidates = new ArrayList<>();
				for (StremioStream stream : copy) {
					try {
						candidates.add(descriptorFactory.candidate(
								request, providers.get(index), stream, now));
					} catch (RuntimeException ignored) {
						// A malformed choice must not hide healthy choices from the same provider.
					}
				}
				candidates.sort(Comparator
						.comparingInt(PlaybackDescriptorFactory.Candidate::rank)
						.thenComparing(PlaybackDescriptorFactory.Candidate::tieBreaker));
				return RawOutcome.success(candidates.stream().map(candidate ->
						new RankedDescriptor(candidate.descriptor(), candidate.dedupeKey())).toList());
			} catch (RuntimeException failure) {
				return RawOutcome.failed();
			}
		}

		private void timeout(int index) {
			ProviderStreamCall call;
			synchronized (this) {
				if (cancelled || (outcomes[index] != null)) return;
				outcomes[index] = RawOutcome.timedOut();
				call = calls[index];
				running--;
				completed++;
				scheduleIncremental();
				pump();
			}
			if (call != null) call.cancel();
		}

		private void settle() {
			RawOutcome[] snapshot;
			synchronized (this) {
				if (cancelled || response.isDone() ||
						initialBuildInProgress || finishing) return;
				initialBuildInProgress = true;
				snapshot = Arrays.copyOf(outcomes, outcomes.length);
			}
			// Outcomes are already normalized; publishing here keeps the interactive deadline
			// independent of a saturated provider executor.
			publishInitial(snapshot);
		}

		private void publishInitial(RawOutcome[] snapshot) {
			BuildResult built = build(snapshot, List.of());
			synchronized (this) {
				if (cancelled || response.isDone()) {
					initialBuildInProgress = false;
					return;
				}
				exposed = built.descriptors;
			}
			response.complete(built.result);
			synchronized (this) {
				initialBuildInProgress = false;
				maybeFinish();
			}
		}

		private synchronized void maybeFinish() {
			if (cancelled || finishing || initialBuildInProgress ||
					(completed != providers.size())) return;
			finishing = true;
			if (settleTimeout != null) settleTimeout.cancel(false);
			RawOutcome[] snapshot = Arrays.copyOf(outcomes, outcomes.length);
			boolean hasInitial = response.isDone();
			List<RankedDescriptor> initial = exposed;
			executor.execute(() -> finish(snapshot, hasInitial, initial));
		}

		private void finish(RawOutcome[] snapshot, boolean hasInitial,
				List<RankedDescriptor> initial) {
			BuildResult built = build(snapshot, hasInitial ? initial : List.of());
			synchronized (this) {
				if (cancelled) return;
			}
			if (!hasInitial) response.complete(built.result);
			publishSnapshot(built.result);
			completion.complete(built.result);
			retire(this);
		}

		private BuildResult build(RawOutcome[] snapshot, List<RankedDescriptor> initial) {
			List<RankedDescriptor> ordered = new ArrayList<>(initial);
			Map<String, Integer> orderedIndexes = new HashMap<>();
			for (int i = 0; i < ordered.size(); i++) {
				orderedIndexes.put(ordered.get(i).dedupeKey, i);
			}
			List<StreamAggregationResult.ProviderGroup> groups =
					new ArrayList<>(providers.size());

			for (int i = 0; i < providers.size(); i++) {
				RawOutcome outcome = snapshot[i];
				if (outcome == null) {
					groups.add(new StreamAggregationResult.ProviderGroup(providers.get(i),
							StreamAggregationResult.ProviderStatus.PENDING, List.of(), operationId));
					continue;
				}
				List<PlaybackDescriptor> descriptors = new ArrayList<>();
				if (outcome.status == StreamAggregationResult.ProviderStatus.SUCCESS) {
					Set<String> groupTargets = new HashSet<>();
					for (RankedDescriptor candidate : outcome.descriptors) {
						if (groupTargets.add(candidate.dedupeKey)) descriptors.add(candidate.descriptor);
						mergeOrdered(ordered, orderedIndexes, candidate);
					}
				}
				groups.add(new StreamAggregationResult.ProviderGroup(
						providers.get(i), outcome.status, descriptors, operationId));
			}

			List<PlaybackDescriptor> descriptorOrder = ordered.stream()
					.map(RankedDescriptor::descriptor).toList();
			return new BuildResult(new StreamAggregationResult(groups, descriptorOrder),
					List.copyOf(ordered));
		}

		private void mergeOrdered(List<RankedDescriptor> ordered,
				Map<String, Integer> indexes, RankedDescriptor candidate) {
			Integer existingIndex = indexes.get(candidate.dedupeKey);
			if (existingIndex == null) {
				indexes.put(candidate.dedupeKey, ordered.size());
				ordered.add(candidate);
				return;
			}
			RankedDescriptor existing = ordered.get(existingIndex);
			PlaybackDescriptor merged = existing.descriptor.mergeTorrentSources(candidate.descriptor);
			if (merged != existing.descriptor) {
				ordered.set(existingIndex, new RankedDescriptor(merged, existing.dedupeKey));
			}
		}

		@Override
		public CompletableFuture<StreamAggregationResult> response() {
			return response;
		}

		@Override
		public CompletableFuture<StreamAggregationResult> completion() {
			return completion;
		}

		@Override
		public AutoCloseable observe(Consumer<StreamAggregationResult> observer) {
			Objects.requireNonNull(observer, "observer");
			StreamAggregationResult snapshot;
			synchronized (this) {
				if (cancelled) return () -> {};
				observers.add(observer);
				snapshot = latestSnapshot;
				if (snapshot == null) {
					snapshot = build(Arrays.copyOf(outcomes, outcomes.length), List.of()).result;
					latestSnapshot = snapshot;
				}
			}
			notifyObserver(observer, snapshot);
			return () -> {
				synchronized (ActiveAggregation.this) {
					observers.remove(observer);
				}
			};
		}

		private synchronized void scheduleIncremental() {
			if (cancelled || finishing || observers.isEmpty() || incrementalTimeout != null) return;
			long elapsed = (lastSnapshotAt == Long.MIN_VALUE) ? INCREMENTAL_THROTTLE_MILLIS :
					Math.max(0L, clock.getAsLong() - lastSnapshotAt);
			long delay = Math.max(0L, INCREMENTAL_THROTTLE_MILLIS - elapsed);
			incrementalTimeout = scheduler.schedule(this::publishIncremental,
					delay, TimeUnit.MILLISECONDS);
		}

		private void publishIncremental() {
			RawOutcome[] snapshot;
			synchronized (this) {
				incrementalTimeout = null;
				if (cancelled || finishing || observers.isEmpty()) return;
				snapshot = Arrays.copyOf(outcomes, outcomes.length);
			}
			publishSnapshot(build(snapshot, List.of()).result);
		}

		private void publishSnapshot(StreamAggregationResult snapshot) {
			List<Consumer<StreamAggregationResult>> listeners;
			synchronized (this) {
				if (cancelled) return;
				latestSnapshot = snapshot;
				lastSnapshotAt = clock.getAsLong();
				listeners = List.copyOf(observers);
			}
			for (Consumer<StreamAggregationResult> observer : listeners) {
				notifyObserver(observer, snapshot);
			}
		}

		private void notifyObserver(Consumer<StreamAggregationResult> observer,
				StreamAggregationResult snapshot) {
			try {
				observer.accept(snapshot);
			} catch (RuntimeException error) {
				Log.e("Stremio stream observer failed: ", error.getClass().getName());
			}
		}

		@Override
		public void cancel() {
			ProviderStreamCall[] pending;
			ScheduledFuture<?>[] pendingTimeouts;
			ScheduledFuture<?> pendingSettle;
			ScheduledFuture<?> pendingIncremental;
			synchronized (this) {
				if (cancelled) return;
				cancelled = true;
				pending = Arrays.copyOf(calls, calls.length);
				pendingTimeouts = Arrays.copyOf(timeouts, timeouts.length);
				pendingSettle = settleTimeout;
				pendingIncremental = incrementalTimeout;
				incrementalTimeout = null;
				CancellationException failure = new CancellationException(
						"Stremio stream aggregation cancelled");
				response.completeExceptionally(failure);
				completion.completeExceptionally(failure);
			}
			if (pendingSettle != null) pendingSettle.cancel(false);
			if (pendingIncremental != null) pendingIncremental.cancel(false);
			for (ScheduledFuture<?> timeout : pendingTimeouts) {
				if (timeout != null) timeout.cancel(false);
			}
			for (ProviderStreamCall call : pending) {
				if (call != null) call.cancel();
			}
			retire(this);
		}
	}

	private record RankedDescriptor(PlaybackDescriptor descriptor, String dedupeKey) {
	}

	private record BuildResult(
			StreamAggregationResult result, List<RankedDescriptor> descriptors) {
	}

	private record RawOutcome(
			StreamAggregationResult.ProviderStatus status, List<RankedDescriptor> descriptors) {
		private static RawOutcome success(List<RankedDescriptor> descriptors) {
			return new RawOutcome(StreamAggregationResult.ProviderStatus.SUCCESS,
					List.copyOf(descriptors));
		}

		private static RawOutcome failed() {
			return new RawOutcome(StreamAggregationResult.ProviderStatus.FAILED, List.of());
		}

		private static RawOutcome timedOut() {
			return new RawOutcome(StreamAggregationResult.ProviderStatus.TIMED_OUT, List.of());
		}
	}
}
