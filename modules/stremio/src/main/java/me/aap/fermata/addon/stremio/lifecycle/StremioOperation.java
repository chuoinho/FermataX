package me.aap.fermata.addon.stremio.lifecycle;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.utils.log.Log;

/** Owns cancellation, deadlines and acquired resources for one logical Stremio operation. */
public final class StremioOperation implements AutoCloseable {
	private final long id;
	private final String logicalKey;
	private final RequestGeneration.Token generation;
	private final ScheduledExecutorService scheduler;
	private final Runnable retired;
	private final Object lock = new Object();
	private final Deque<Owned> owned = new ArrayDeque<>();
	private final AtomicBoolean closed = new AtomicBoolean();
	private AutoCloseable generationObservation = () -> {};

	StremioOperation(long id, String logicalKey, RequestGeneration.Token generation,
			ScheduledExecutorService scheduler, Runnable retired) {
		this.id = id;
		this.logicalKey = requireKey(logicalKey);
		this.generation = generation;
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		this.retired = Objects.requireNonNull(retired, "retired");
		if (generation != null) {
			AutoCloseable observation = generation.onInvalidated(this::close);
			generationObservation = observation;
			if (closed.get()) close(observation);
		}
	}

	public long id() {
		return id;
	}

	public String logicalKey() {
		return logicalKey;
	}

	public boolean isCurrent() {
		return !closed.get() && ((generation == null) || generation.isCurrent());
	}

	public void throwIfStale() {
		if (closed.get()) throw new java.util.concurrent.CancellationException(
				"Stremio operation is closed");
		if (generation != null) generation.throwIfStale();
	}

	public <T extends StremioCall<?>> T own(T call) {
		Objects.requireNonNull(call, "call");
		Owned ownership = ownCleanup(call::cancel);
		call.completion().whenComplete((value, error) -> ownership.detach());
		return call;
	}

	public <T extends AutoCloseable> T own(T resource) {
		Objects.requireNonNull(resource, "resource");
		ownCleanup(() -> close(resource));
		return resource;
	}

	public ScheduledFuture<?> deadline(Duration timeout, Runnable action) {
		Objects.requireNonNull(timeout, "timeout");
		Objects.requireNonNull(action, "action");
		if (timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("Operation deadline must be positive");
		}
		throwIfStale();
		ScheduledFuture<?> future = scheduler.schedule(() -> {
			if (isCurrent()) action.run();
		}, timeout.toMillis(), TimeUnit.MILLISECONDS);
		ownCleanup(() -> future.cancel(false));
		return future;
	}

	private Owned ownCleanup(Runnable cleanup) {
		Owned ownership = new Owned(cleanup);
		synchronized (lock) {
			if (!closed.get()) {
				owned.addFirst(ownership);
				return ownership;
			}
		}
		ownership.close();
		return ownership;
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		try {
			generationObservation.close();
		} catch (Exception error) {
			report(error);
		}
		while (true) {
			Owned ownership;
			synchronized (lock) {
				ownership = owned.pollFirst();
			}
			if (ownership == null) break;
			ownership.close();
		}
		retired.run();
	}

	@Override
	public String toString() {
		return "StremioOperation[id=" + id + ", key=" + logicalKey +
				", current=" + isCurrent() + ']';
	}

	private static String requireKey(String key) {
		Objects.requireNonNull(key, "logicalKey");
		String normalized = key.trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException("logicalKey is blank");
		return normalized;
	}

	private static void close(AutoCloseable closeable) {
		try {
			closeable.close();
		} catch (Exception error) {
			report(error);
		}
	}

	private static void report(Throwable error) {
		Log.e("Stremio operation cleanup failed: ", error.getClass().getName());
	}

	private final class Owned implements AutoCloseable {
		private final Runnable cleanup;
		private final AtomicBoolean active = new AtomicBoolean(true);

		private Owned(Runnable cleanup) {
			this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
		}

		private void detach() {
			if (!active.compareAndSet(true, false)) return;
			synchronized (lock) {
				owned.remove(this);
			}
		}

		@Override
		public void close() {
			if (!active.compareAndSet(true, false)) return;
			try {
				cleanup.run();
			} catch (Throwable error) {
				report(error);
			}
		}
	}
}
