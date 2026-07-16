package me.aap.fermata.media.lib;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.async.ProxySupplier;
import me.aap.utils.function.CheckedSupplier;

/** Coordinates keyed refresh work without coupling observer cancellation to shared work. */
public final class RefreshCoordinator<K> {
	private final long cooldownMillis;
	private final LongSupplier clock;
	private final Map<K, Entry> inFlight = new HashMap<>();
	private final Map<K, Long> lastSuccess = new HashMap<>();
	private boolean active = true;

	public RefreshCoordinator(long cooldownMillis) {
		this(cooldownMillis, System::currentTimeMillis);
	}

	public RefreshCoordinator(long cooldownMillis, LongSupplier clock) {
		if (cooldownMillis < 0L) throw new IllegalArgumentException("Negative cooldown");
		this.cooldownMillis = cooldownMillis;
		this.clock = Objects.requireNonNull(clock);
	}

	public FutureSupplier<Result<K>> auto(K key, Operation operation) {
		return request(key, Trigger.AUTO, Policy.JOIN, true, operation);
	}

	public FutureSupplier<Result<K>> manual(K key, Operation operation) {
		return request(key, Trigger.MANUAL, Policy.JOIN, false, operation);
	}

	public FutureSupplier<Result<K>> replace(K key, Operation operation) {
		return request(key, Trigger.EDIT, Policy.REPLACE, false, operation);
	}

	public void start() {
		synchronized (this) {
			active = true;
		}
	}

	public void stop() {
		List<Entry> entries;

		synchronized (this) {
			active = false;
			entries = new ArrayList<>(inFlight.values());
			inFlight.clear();
		}

		for (Entry entry : entries) cancel(entry);
	}

	public void reset() {
		stop();
		synchronized (this) {
			lastSuccess.clear();
			active = true;
		}
	}

	boolean isDue(K key, long now) {
		synchronized (this) {
			Long last = lastSuccess.get(key);
			return (last == null) || ((now - last) >= cooldownMillis);
		}
	}

	private FutureSupplier<Result<K>> request(K key, Trigger trigger, Policy policy,
																		 boolean checkCooldown, Operation operation) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(operation);
		Entry previous = null;
		Entry entry;

		synchronized (this) {
			if (!active) return completed(Result.inactive(key, trigger));

			Entry current = inFlight.get(key);
			if ((current != null) && (policy == Policy.JOIN)) return observe(current.result);

			if (checkCooldown) {
				Long last = lastSuccess.get(key);
				long now = clock.getAsLong();
				if ((last != null) && ((now - last) < cooldownMillis))
					return completed(Result.skipped(key, trigger));
			}

			if (current != null) previous = current;
			entry = new Entry(key, trigger);
			inFlight.put(key, entry);
		}

		if (previous != null) cancel(previous);
		start(entry, operation);
		return observe(entry.result);
	}

	private void start(Entry entry, Operation operation) {
		FutureSupplier<?> task = null;
		Throwable failure = null;

		synchronized (this) {
			if (!active || (inFlight.get(entry.key) != entry)) return;
			try {
				task = Objects.requireNonNull(operation.get(), "Refresh operation returned null");
				entry.task = task;
			} catch (Throwable error) {
				failure = error;
			}
		}

		if (failure != null) {
			finish(entry, failure);
			return;
		}
		task.onCompletion((result, error) -> finish(entry, error));
	}

	private void finish(Entry entry, @Nullable Throwable error) {
		synchronized (this) {
			if (inFlight.get(entry.key) != entry) return;
			inFlight.remove(entry.key);
			if (error == null) lastSuccess.put(entry.key, clock.getAsLong());
		}

		if (error == null) {
			entry.result.complete(Result.success(entry.key, entry.trigger));
		} else if (isCancellation(error)) {
			entry.result.complete(Result.cancelled(entry.key, entry.trigger));
		} else {
			entry.result.complete(Result.failed(entry.key, entry.trigger, error));
		}
	}

	private void cancel(Entry entry) {
		entry.result.complete(Result.cancelled(entry.key, entry.trigger));
		FutureSupplier<?> task = entry.task;
		if (task != null) task.cancel();
	}

	private FutureSupplier<Result<K>> observe(Promise<Result<K>> result) {
		return ProxySupplier.create(result);
	}

	public enum Trigger {
		AUTO,
		MANUAL,
		EDIT
	}

	public enum Status {
		SUCCESS,
		FAILED,
		CANCELLED,
		SKIPPED_COOLDOWN,
		INACTIVE
	}

	public enum FailureKind {
		NETWORK,
		PROVIDER
	}

	private enum Policy {
		JOIN,
		REPLACE
	}

	@FunctionalInterface
	public interface Operation extends CheckedSupplier<FutureSupplier<?>, Throwable> {
	}

	public record Result<K>(@NonNull K key, @NonNull Trigger trigger, @NonNull Status status,
										@Nullable FailureKind failureKind, @Nullable Throwable error) {
		static <K> Result<K> success(K key, Trigger trigger) {
			return new Result<>(key, trigger, Status.SUCCESS, null, null);
		}

		static <K> Result<K> failed(K key, Trigger trigger, Throwable error) {
			return new Result<>(key, trigger, Status.FAILED, classify(error), error);
		}

		static <K> Result<K> cancelled(K key, Trigger trigger) {
			return new Result<>(key, trigger, Status.CANCELLED, null, null);
		}

		static <K> Result<K> skipped(K key, Trigger trigger) {
			return new Result<>(key, trigger, Status.SKIPPED_COOLDOWN, null, null);
		}

		static <K> Result<K> inactive(K key, Trigger trigger) {
			return new Result<>(key, trigger, Status.INACTIVE, null, null);
		}

		public boolean isSuccess() {
			return status == Status.SUCCESS;
		}

		public boolean isFailure() {
			return status == Status.FAILED;
		}

		private static FailureKind classify(Throwable error) {
			for (Throwable e = error; e != null; e = e.getCause()) {
				if ((e instanceof UnknownHostException) || (e instanceof ConnectException) ||
						(e instanceof NoRouteToHostException) || (e instanceof SocketTimeoutException) ||
						(e instanceof IOException)) return FailureKind.NETWORK;
			}
			return FailureKind.PROVIDER;
		}
	}

	private final class Entry {
		final K key;
		final Trigger trigger;
		final Promise<Result<K>> result = new Promise<>();
		FutureSupplier<?> task;

		Entry(K key, Trigger trigger) {
			this.key = key;
			this.trigger = trigger;
		}
	}
}
