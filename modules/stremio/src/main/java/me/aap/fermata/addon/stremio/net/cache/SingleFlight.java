package me.aap.fermata.addon.stremio.net.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;

public final class SingleFlight<K, V> {
	private final Map<K, Shared<V>> active = new HashMap<>();

	public Call<V> execute(K key, Supplier<Operation<V>> factory) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(factory, "factory");
		Shared<V> shared;
		synchronized (active) {
			@SuppressWarnings("unchecked") Shared<V> found = active.get(key);
			shared = found;
			if (shared == null) {
				shared = new Shared<>(Objects.requireNonNull(factory.get(), "operation"));
				active.put(key, shared);
				Shared<V> created = shared;
				shared.operation.response().whenComplete((value, error) -> {
					synchronized (active) {
						active.remove(key, created);
					}
				});
			}
			shared.subscribers++;
		}
		return subscribe(shared);
	}

	public int activeCount() {
		synchronized (active) {
			return active.size();
		}
	}

	private Call<V> subscribe(Shared<V> shared) {
		var result = new CompletableFuture<V>();
		var released = new AtomicBoolean();
		shared.operation.response().whenComplete((value, error) -> {
			if (released.compareAndSet(false, true)) release(shared, false);
			if (error == null) result.complete(value);
			else result.completeExceptionally(error);
		});
		return new Call<>() {
			@Override
			public CompletableFuture<V> response() {
				return result;
			}

			@Override
			public void cancel() {
				if (!released.compareAndSet(false, true)) return;
				result.completeExceptionally(new CancellationException("Single-flight subscriber cancelled"));
				release(shared, true);
			}
		};
	}

	private void release(Shared<V> shared, boolean mayCancelOperation) {
		boolean cancel = false;
		synchronized (active) {
			if (shared.subscribers > 0) shared.subscribers--;
			if (mayCancelOperation && (shared.subscribers == 0) &&
					!shared.operation.response().isDone()) cancel = true;
		}
		if (cancel) shared.operation.cancel();
	}

	public interface Operation<V> extends StremioCall<V> {
		CompletableFuture<V> response();

		@Override
		default CompletableFuture<V> completion() {
			return response();
		}

		void cancel();
	}

	public interface Call<V> extends StremioCall<V> {
		CompletableFuture<V> response();

		@Override
		default CompletableFuture<V> completion() {
			return response();
		}

		void cancel();
	}

	private static final class Shared<V> {
		private final Operation<V> operation;
		private int subscribers;

		private Shared(Operation<V> operation) {
			this.operation = operation;
		}
	}
}
