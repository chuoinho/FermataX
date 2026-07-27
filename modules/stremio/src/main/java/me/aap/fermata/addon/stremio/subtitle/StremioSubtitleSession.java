package me.aap.fermata.addon.stremio.subtitle;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Owns discovery and sidecar loading for exactly one active Stremio video identity. */
public final class StremioSubtitleSession {
	private final String videoKey;
	private long generation;
	private FutureSupplier<?> discovery;
	private FutureSupplier<?> load;
	private long staleCallbacks;

	public StremioSubtitleSession(String videoKey) {
		this.videoKey = requireKey(videoKey);
	}

	public String videoKey() {
		return videoKey;
	}

	public synchronized void activate(String videoKey) {
		if (!this.videoKey.equals(requireKey(videoKey))) {
			throw new IllegalArgumentException("Subtitle session identity cannot change");
		}
		generation++;
		cancel(discovery);
		cancel(load);
		discovery = null;
		load = null;
	}

	public <T> FutureSupplier<T> discover(Supplier<FutureSupplier<T>> operation) {
		return start(true, operation);
	}

	public <T> FutureSupplier<T> load(Supplier<FutureSupplier<T>> operation) {
		return start(false, operation);
	}

	public synchronized void cancel() {
		generation++;
		cancel(discovery);
		cancel(load);
		discovery = null;
		load = null;
	}

	public synchronized long staleCallbackCount() {
		return staleCallbacks;
	}

	private <T> FutureSupplier<T> start(boolean discoveryStage,
			Supplier<FutureSupplier<T>> operation) {
		Objects.requireNonNull(operation, "operation");
		final long owner;
		synchronized (this) {
			owner = generation;
			FutureSupplier<?> previous = discoveryStage ? discovery : load;
			cancel(previous);
		}
		FutureSupplier<T> upstream;
		try {
			upstream = Objects.requireNonNull(operation.get(), "subtitle operation");
		} catch (Throwable error) {
			return me.aap.utils.async.Completed.failed(error);
		}
		Promise<T> result = new Promise<>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				upstream.cancel(mayInterruptIfRunning);
				return super.cancel(mayInterruptIfRunning);
			}
		};
		synchronized (this) {
			if (owner != generation) {
				upstream.cancel();
				result.completeExceptionally(new CancellationException(
						"Subtitle session was replaced"));
				return result;
			}
			if (discoveryStage) discovery = result;
			else load = result;
		}
		upstream.onCompletion((value, error) -> {
			synchronized (StremioSubtitleSession.this) {
				FutureSupplier<?> current = discoveryStage ? discovery : load;
				if ((owner != generation) || (current != result)) {
					staleCallbacks++;
					return;
				}
				if (discoveryStage) discovery = null;
				else load = null;
			}
			if (error == null) result.complete(value);
			else result.completeExceptionally(error);
		});
		return result;
	}

	private static void cancel(FutureSupplier<?> operation) {
		if ((operation != null) && !operation.isDone()) operation.cancel();
	}

	private static String requireKey(String value) {
		Objects.requireNonNull(value, "videoKey");
		if (value.isBlank()) throw new IllegalArgumentException("videoKey cannot be blank");
		return value;
	}
}
