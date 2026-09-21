package me.aap.fermata.addon.web;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pure navigation reducer. Android callback binding belongs to the browser host. */
public final class WebUrlSyncObserver implements AutoCloseable {
	private static final long DEBOUNCE_MILLIS = 150;

	private final Supplier<DispatchState> stateSource;
	private final Scheduler scheduler;
	private final Consumer<Candidate> sink;
	private Cancellation pending;
	private long pendingGeneration;
	private long sourceGeneration = Long.MIN_VALUE;
	private long navigationSequence = Long.MIN_VALUE;
	private String currentUrl;
	private boolean closed;

	public WebUrlSyncObserver(Supplier<DispatchState> stateSource, Scheduler scheduler,
			Consumer<Candidate> sink) {
		this.stateSource = Objects.requireNonNull(stateSource);
		this.scheduler = Objects.requireNonNull(scheduler);
		this.sink = Objects.requireNonNull(sink);
	}

	public synchronized void baseline(NavigationEvent event) {
		Objects.requireNonNull(event);
		if (closed) return;
		cancelPending();
		sourceGeneration = event.sourceGeneration();
		navigationSequence = event.navigationSequence();
		currentUrl = event.url();
	}

	public void onStarted(NavigationEvent event) {
		Objects.requireNonNull(event);
		if ((event.provenance() == Provenance.APP_GET) ||
				(event.provenance() == Provenance.REQUEST_GET)) accept(event);
		else reject(event);
	}

	public void onHistory(NavigationEvent event) {
		Objects.requireNonNull(event);
		if (event.provenance().transportable) accept(event);
		else reject(event);
	}

	/** Completion is acknowledgement-only and never creates a request candidate. */
	public void onFinished(NavigationEvent event) {
		Objects.requireNonNull(event);
	}

	public synchronized void onFailed(NavigationEvent event) {
		Objects.requireNonNull(event);
		if ((event.sourceGeneration() == sourceGeneration) &&
				(event.navigationSequence() == navigationSequence) &&
				Objects.equals(event.url(), currentUrl)) cancelPending();
	}

	public synchronized void cancel() {
		cancelPending();
	}

	DispatchState captureState() {
		return stateSource.get();
	}

	/** Cancels pending source work when the owning browser fragment is hidden. */
	public synchronized void onHidden() {
		cancelPending();
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;
		cancelPending();
	}

	private synchronized void accept(NavigationEvent event) {
		if (closed) return;
		DispatchState state = Objects.requireNonNull(stateSource.get());
		if (!state.enabled() || (state.sourceGeneration() != event.sourceGeneration())) {
			cancelPending();
			return;
		}
		if (!event.mainFrame()) return;
		if (!WebUrlSyncPolicy.accepts(event.url())) {
			reject(event);
			return;
		}

		if (event.sourceGeneration() != sourceGeneration) {
			cancelPending();
			sourceGeneration = event.sourceGeneration();
			navigationSequence = Long.MIN_VALUE;
			currentUrl = null;
		}
		if (event.navigationSequence() < navigationSequence) return;
		if ((event.navigationSequence() == navigationSequence) &&
				Objects.equals(event.url(), currentUrl)) return;

		cancelPending();
		navigationSequence = event.navigationSequence();
		currentUrl = event.url();
		Candidate candidate = new Candidate(event.url(), event.sourceGeneration(),
				event.navigationSequence(), event.reload(), state);
		long generation = ++pendingGeneration;
		pending = scheduler.schedule(() -> dispatch(generation, candidate), DEBOUNCE_MILLIS);
	}

	private synchronized void reject(NavigationEvent event) {
		if (closed) return;
		DispatchState state = Objects.requireNonNull(stateSource.get());
		if (!state.enabled() || (state.sourceGeneration() != event.sourceGeneration())) {
			cancelPending();
			return;
		}
		if (!event.mainFrame()) return;
		if (event.sourceGeneration() != sourceGeneration) {
			cancelPending();
			sourceGeneration = event.sourceGeneration();
			navigationSequence = Long.MIN_VALUE;
		}
		if (event.navigationSequence() < navigationSequence) return;
		cancelPending();
		navigationSequence = event.navigationSequence();
		currentUrl = event.url();
	}

	private synchronized void dispatch(long generation, Candidate candidate) {
		if (closed || (generation != pendingGeneration)) return;
		pending = null;
		DispatchState current = Objects.requireNonNull(stateSource.get());
		if (current.enabled() && candidate.state().equals(current) &&
				(current.sourceGeneration() == candidate.sourceGeneration())) sink.accept(candidate);
	}

	private void cancelPending() {
		pendingGeneration++;
		if (pending != null) {
			pending.cancel();
			pending = null;
		}
	}

	public enum Provenance {
		APP_GET(true),
		REQUEST_GET(true),
		SAME_DOCUMENT(true),
		POST(false),
		UNKNOWN(false),
		RECOVERY(false);

		private final boolean transportable;

		Provenance(boolean transportable) {
			this.transportable = transportable;
		}
	}

	public record NavigationEvent(long sourceGeneration, long navigationSequence,
			Provenance provenance, boolean reload, boolean mainFrame, String url) {
		public NavigationEvent {
			Objects.requireNonNull(provenance);
		}
	}

	public record DispatchState(boolean enabled, long token, long sourceGeneration,
			long hostGeneration, long modeRevision, long connectionEpoch) {
	}

	public record Candidate(String url, long sourceGeneration, long navigationSequence,
			boolean reload, DispatchState state) {
		public Candidate {
			Objects.requireNonNull(url);
			Objects.requireNonNull(state);
		}
	}

	@FunctionalInterface
	public interface Scheduler {
		Cancellation schedule(Runnable runnable, long delayMillis);
	}

	@FunctionalInterface
	public interface Cancellation {
		void cancel();
	}
}
