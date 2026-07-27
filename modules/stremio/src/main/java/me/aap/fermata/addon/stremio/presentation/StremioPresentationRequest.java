package me.aap.fermata.addon.stremio.presentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import me.aap.utils.async.FutureSupplier;

/** Owns cancellation and stale-result rejection for one presentation route load. */
final class StremioPresentationRequest implements StremioPresenter.Request {
	private final CompletableFuture<StremioPresentationPage> result = new CompletableFuture<>();
	private final List<FutureSupplier<?>> suppliers =
			Collections.synchronizedList(new ArrayList<>());
	private final StremioRoute route;
	private final BooleanSupplier ownerClosed;
	private volatile boolean cancelled;
	private Consumer<StremioPresentationPage> updateListener;
	private StremioPresentationPage pendingUpdate;

	StremioPresentationRequest(StremioRoute route, BooleanSupplier ownerClosed) {
		this.route = Objects.requireNonNull(route, "route");
		this.ownerClosed = Objects.requireNonNull(ownerClosed, "ownerClosed");
	}

	StremioRoute route() {
		return route;
	}

	<T> CompletableFuture<T> track(FutureSupplier<T> supplier) {
		Objects.requireNonNull(supplier, "supplier");
		if (!isOwnerActive()) {
			supplier.cancel();
			return cancelledFuture();
		}
		suppliers.add(supplier);
		CompletableFuture<T> future = new CompletableFuture<>();
		supplier.onCompletion((value, failure) -> {
			suppliers.remove(supplier);
			completeTracked(future, value, failure);
		});
		return future;
	}

	<T> CompletableFuture<T> track(CompletionStage<T> stage) {
		Objects.requireNonNull(stage, "stage");
		if (!isOwnerActive()) return cancelledFuture();
		CompletableFuture<T> future = new CompletableFuture<>();
		stage.whenComplete((value, failure) -> completeTracked(future, value, failure));
		return future;
	}

	void ensureActive() {
		if (!isOwnerActive()) throw new CancellationException();
	}

	void publishUpdate(StremioPresentationPage page) {
		Consumer<StremioPresentationPage> listener;
		synchronized (this) {
			if (!isOwnerActive()) return;
			listener = updateListener;
			if (listener == null) {
				pendingUpdate = page;
				return;
			}
		}
		listener.accept(page);
	}

	@Override
	public void onUpdate(Consumer<StremioPresentationPage> listener) {
		StremioPresentationPage pending;
		synchronized (this) {
			if (!isOwnerActive()) return;
			updateListener = Objects.requireNonNull(listener, "listener");
			pending = pendingUpdate;
			pendingUpdate = null;
		}
		if (pending != null) listener.accept(pending);
	}

	void finish(CompletionStage<StremioPresentationPage> stage) {
		Objects.requireNonNull(stage, "stage").whenComplete((page, failure) ->
				completeTracked(result, page, failure));
	}

	void fail(Throwable failure) {
		result.completeExceptionally(Objects.requireNonNull(failure, "failure"));
	}

	@Override
	public CompletionStage<StremioPresentationPage> result() {
		return result;
	}

	@Override
	public CompletionStage<StremioPresentationPage> completion() {
		return result;
	}

	@Override
	public boolean isActive() {
		return isOwnerActive() && !result.isDone();
	}

	@Override
	public void cancel() {
		if (cancelled) return;
		cancelled = true;
		synchronized (this) {
			updateListener = null;
			pendingUpdate = null;
		}
		List<FutureSupplier<?>> active;
		synchronized (suppliers) {
			active = List.copyOf(suppliers);
			suppliers.clear();
		}
		for (FutureSupplier<?> supplier : active) supplier.cancel();
		result.completeExceptionally(new CancellationException());
	}

	private boolean isOwnerActive() {
		return !cancelled && !ownerClosed.getAsBoolean();
	}

	private <T> void completeTracked(CompletableFuture<T> future, T value, Throwable failure) {
		if (!isOwnerActive()) future.completeExceptionally(new CancellationException());
		else if (failure != null) future.completeExceptionally(failure);
		else future.complete(value);
	}

	private static <T> CompletableFuture<T> cancelledFuture() {
		return CompletableFuture.failedFuture(new CancellationException());
	}
}
