package me.aap.fermata.addon.stremio.browse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;

public final class BrowseOperation<T> implements StremioCall<BrowseLoadState<T>> {
	private final BrowseLoadState.Loading<T> loading;
	private final CompletableFuture<BrowseLoadState<T>> result;
	private final Runnable cancellation;
	private final AtomicBoolean closed = new AtomicBoolean();

	BrowseOperation(BrowseLoadState.Loading<T> loading,
			CompletableFuture<BrowseLoadState<T>> result, Runnable cancellation) {
		this.loading = Objects.requireNonNull(loading, "loading");
		this.result = Objects.requireNonNull(result, "result");
		this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
	}

	public BrowseLoadState.Loading<T> loading() {
		return loading;
	}

	public CompletableFuture<BrowseLoadState<T>> result() {
		return result;
	}

	@Override
	public CompletableFuture<BrowseLoadState<T>> completion() {
		return result;
	}

	@Override
	public void cancel() {
		if (!closed.compareAndSet(false, true)) return;
		cancellation.run();
		result.cancel(false);
	}

	public boolean isActive() {
		return !closed.get() && !result.isDone();
	}

	public void close() {
		cancel();
	}
}
