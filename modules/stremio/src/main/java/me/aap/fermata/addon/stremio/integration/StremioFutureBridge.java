package me.aap.fermata.addon.stremio.integration;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Converts JDK completion stages at the runtime boundary into Fermata futures. */
public final class StremioFutureBridge {
	private StremioFutureBridge() {
	}

	public static <T> FutureSupplier<T> from(CompletionStage<T> stage) {
		return from(stage, () -> {
			if (stage instanceof Future<?> future) future.cancel(true);
		});
	}

	public static <T> FutureSupplier<T> from(CompletionStage<T> stage, Runnable cancellation) {
		java.util.Objects.requireNonNull(stage, "stage");
		java.util.Objects.requireNonNull(cancellation, "cancellation");
		AtomicBoolean cancelled = new AtomicBoolean();
		Promise<T> result = new Promise<>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				boolean changed = super.cancel(mayInterruptIfRunning);
				if (changed && cancelled.compareAndSet(false, true)) cancellation.run();
				return changed;
			}
		};
		stage.whenComplete((value, error) -> {
			if (error == null) result.complete(value);
			else result.completeExceptionally(unwrap(error));
		});
		return result;
	}

	static <T> CompletableFuture<T> toCompletable(FutureSupplier<T> supplier) {
		CompletableFuture<T> result = new CompletableFuture<>();
		supplier.onCompletion((value, error) -> {
			if (error == null) result.complete(value);
			else result.completeExceptionally(error);
		});
		return result;
	}

	private static Throwable unwrap(Throwable error) {
		while ((error instanceof CompletionException) && (error.getCause() != null)) {
			error = error.getCause();
		}
		return error;
	}
}
