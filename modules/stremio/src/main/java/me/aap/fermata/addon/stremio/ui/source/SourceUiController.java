package me.aap.fermata.addon.stremio.ui.source;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import me.aap.fermata.addon.stremio.lifecycle.StremioDeadlineScheduler;

/** Lifecycle-aware state machine shared by mobile and Android Auto source screens. */
public final class SourceUiController implements AutoCloseable {
	private static final long DEFAULT_OPERATION_TIMEOUT_MILLIS = 30_000L;
	private final SourceUiGateway gateway;
	private final Listener listener;
	private final Executor callbackExecutor;
	private final ScheduledExecutorService scheduler;
	private final long operationTimeoutMillis;
	private SourceUiState state = SourceUiState.initial();
	private SourceUiOperation operation;
	private CompletableFuture<?> draftLoad;
	private CompletableFuture<?> initialLoad;
	private ScheduledFuture<?> deadline;
	private AutoCloseable observation;
	private long generation;
	private boolean started;

	public SourceUiController(SourceUiGateway gateway, Listener listener,
			Executor callbackExecutor) {
		this(gateway, listener, callbackExecutor, StremioDeadlineScheduler.get(),
				DEFAULT_OPERATION_TIMEOUT_MILLIS);
	}

	SourceUiController(SourceUiGateway gateway, Listener listener,
			Executor callbackExecutor, ScheduledExecutorService scheduler,
			long operationTimeoutMillis) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.listener = Objects.requireNonNull(listener, "listener");
		this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		if (operationTimeoutMillis <= 0L) throw new IllegalArgumentException(
				"operationTimeoutMillis must be positive");
		this.operationTimeoutMillis = operationTimeoutMillis;
	}

	public void start() {
		if (started) return;
		started = true;
		long expected = ++generation;
		state = new SourceUiState(state.snapshot(), true, null);
		render();
		try {
			observation = gateway.observe(snapshot -> callbackExecutor.execute(() -> {
				if (!isCurrent(expected)) return;
				acceptSnapshot(snapshot);
			}));
		} catch (RuntimeException error) {
			state = new SourceUiState(state.snapshot(), false, null);
			render();
			listener.showError(SourceUiError.CLOSED);
			return;
		}

		final CompletableFuture<SourceUiSnapshot> loading;
		try {
			loading = Objects.requireNonNull(gateway.load(), "load operation");
		} catch (RuntimeException error) {
			state = new SourceUiState(state.snapshot(), false, null);
			render();
			listener.showError(normalize(error));
			return;
		}
		initialLoad = loading;
		scheduleDeadline(expected, loading, () -> {
			if (initialLoad != loading) return;
			initialLoad = null;
			state = new SourceUiState(state.snapshot(), false, null);
			render();
			listener.showError(SourceUiError.UNKNOWN);
		});
		loading.whenCompleteAsync((snapshot, error) -> {
			if (!isCurrent(expected) || (initialLoad != loading)) return;
			initialLoad = null;
			cancelDeadline();
			if (error == null) {
				acceptSnapshot(snapshot);
			} else {
				state = new SourceUiState(state.snapshot(), false, null);
				render();
				listener.showError(normalize(error));
			}
		}, callbackExecutor);
	}

	public void requestAdd() {
		if (!started || isBusy()) return;
		listener.showEditor(new EditorRequest(null,
				new SourceUiDraft("", "", SourceUiConsent.STRICT)));
	}

	public void requestEdit(String sourceUuid) {
		if (!started || isBusy() || (state.snapshot().source(sourceUuid) == null)) return;
		long expected = generation;
		setPending(new Pending(Action.LOAD_EDITOR, sourceUuid, true));
		final CompletableFuture<SourceUiDraft> load;
		try {
			load = Objects.requireNonNull(gateway.loadDraft(sourceUuid), "draft operation");
		} catch (RuntimeException error) {
			setPending(null);
			listener.showError(normalize(error));
			return;
		}
		draftLoad = load;
		scheduleDeadline(expected, load, () -> {
			if (draftLoad != load) return;
			draftLoad = null;
			setPending(null);
			listener.showError(SourceUiError.UNKNOWN);
		});
		load.whenCompleteAsync((draft, error) -> {
			if (!isCurrent(expected) || (draftLoad != load)) return;
			draftLoad = null;
			cancelDeadline();
			setPending(null);
			if (error == null) listener.showEditor(new EditorRequest(sourceUuid, draft));
			else if (!isCancellation(error)) listener.showError(normalize(error));
		}, callbackExecutor);
	}

	public boolean submit(EditorRequest request, SourceUiDraft draft) {
		Objects.requireNonNull(request, "request");
		if (!started) return false;
		SourceUiError validation = SourceFormValidator.validate(draft);
		if (validation != SourceUiError.NONE) {
			listener.showError(validation);
			return false;
		}
		if (isBusy()) return false;
		if (request.isEdit()) {
			return beginSafely(Action.EDIT, request.sourceUuid(),
					() -> gateway.edit(request.sourceUuid(), draft));
		} else {
			return beginSafely(Action.ADD, null, () -> gateway.add(draft));
		}
	}

	public void setEnabled(String sourceUuid, boolean enabled) {
		if (!canMutate(sourceUuid)) return;
		beginSafely(enabled ? Action.ENABLE : Action.DISABLE, sourceUuid,
				() -> gateway.setEnabled(sourceUuid, enabled));
	}

	public void refresh(String sourceUuid) {
		if (!canMutate(sourceUuid)) return;
		beginSafely(Action.REFRESH, sourceUuid, () -> gateway.refresh(sourceUuid));
	}

	public void requestRemove(String sourceUuid) {
		if (!canMutate(sourceUuid)) return;
		listener.confirmRemove(state.snapshot().source(sourceUuid));
	}

	public void removeConfirmed(String sourceUuid) {
		if (!canMutate(sourceUuid)) return;
		beginSafely(Action.REMOVE, sourceUuid, () -> gateway.remove(sourceUuid));
	}

	public void reorder(List<String> orderedSourceUuids) {
		if (!started || isBusy()) return;
		List<String> order = List.copyOf(orderedSourceUuids);
		if (order.equals(state.snapshot().sources().stream()
				.map(SourceUiItem::sourceUuid).toList())) return;
		beginSafely(Action.REORDER, null, () -> gateway.reorder(order));
	}

	public void cancelPending() {
		CompletableFuture<?> load = draftLoad;
		if (load != null) {
			draftLoad = null;
			load.cancel(true);
			cancelDeadline();
			setPending(null);
			return;
		}
		SourceUiOperation current = operation;
		if (current == null) return;
		operation = null;
		cancelDeadline();
		current.cancel();
		setPending(null);
	}

	public SourceUiState state() {
		return state;
	}

	@Override
	public void close() {
		if (!started && (observation == null)) return;
		started = false;
		generation++;
		cancelDeadline();
		CompletableFuture<?> bootstrap = initialLoad;
		if (bootstrap != null) bootstrap.cancel(true);
		CompletableFuture<?> load = draftLoad;
		if (load != null) load.cancel(true);
		SourceUiOperation current = operation;
		if (current != null) current.cancel();
		operation = null;
		initialLoad = null;
		draftLoad = null;
		state = new SourceUiState(state.snapshot(), false, null);
		AutoCloseable closeable = observation;
		observation = null;
		if (closeable != null) {
			try {
				closeable.close();
			} catch (Exception ignored) {
				// Observation cleanup must not break the containing Fragment lifecycle.
			}
		}
	}

	private void begin(Action action, String sourceUuid, SourceUiOperation next) {
		Objects.requireNonNull(next, "operation");
		long expected = generation;
		operation = next;
		setPending(new Pending(action, sourceUuid, true));
		scheduleDeadline(expected, next.completion(), () -> {
			if (operation != next) return;
			operation = null;
			setPending(null);
			listener.showError(SourceUiError.UNKNOWN);
			next.cancel();
		});
		next.completion().whenCompleteAsync((result, error) -> {
			if (!isCurrent(expected) || (operation != next)) return;
			operation = null;
			cancelDeadline();
			setPending(null);
			if (error != null) {
				if (!isCancellation(error)) listener.showError(normalize(error));
				return;
			}
			if (result == null) {
				listener.showError(SourceUiError.UNKNOWN);
				return;
			}
			if (result.snapshot() != null) {
				acceptSnapshot(result.snapshot());
			}
			if ((result.status() == SourceUiResult.Status.FAILED) &&
					(result.error() != SourceUiError.NONE)) {
				listener.showError(result.error());
			}
		}, callbackExecutor);
	}

	private boolean beginSafely(Action action, String sourceUuid,
			Supplier<SourceUiOperation> supplier) {
		try {
			begin(action, sourceUuid, Objects.requireNonNull(supplier.get(), "operation"));
			return true;
		} catch (RuntimeException error) {
			listener.showError(normalize(error));
			return false;
		}
	}

	private boolean canMutate(String sourceUuid) {
		return started && !isBusy() && (state.snapshot().source(sourceUuid) != null);
	}

	private boolean isBusy() {
		return state.pending() != null;
	}

	private boolean isCurrent(long expected) {
		return started && (generation == expected);
	}

	private void setPending(Pending pending) {
		state = new SourceUiState(state.snapshot(), state.initialLoading(), pending);
		render();
	}

	private void render() {
		listener.render(state);
	}

	private void acceptSnapshot(SourceUiSnapshot snapshot) {
		Objects.requireNonNull(snapshot, "snapshot");
		if (snapshot.revision() < state.snapshot().revision()) return;
		CompletableFuture<?> bootstrap = initialLoad;
		if (bootstrap != null) {
			initialLoad = null;
			cancelDeadline();
			if (!bootstrap.isDone()) bootstrap.cancel(true);
		}
		state = new SourceUiState(snapshot, false, state.pending());
		render();
	}

	private void scheduleDeadline(long expected, CompletableFuture<?> future, Runnable timeout) {
		cancelDeadline();
		deadline = scheduler.schedule(() -> callbackExecutor.execute(() -> {
			if (!isCurrent(expected) || future.isDone()) return;
			// Invalidate the owned UI operation before cancellation can invoke completion inline.
			timeout.run();
			future.cancel(true);
		}), operationTimeoutMillis, TimeUnit.MILLISECONDS);
	}

	private void cancelDeadline() {
		ScheduledFuture<?> current = deadline;
		deadline = null;
		if (current != null) current.cancel(false);
	}

	private static SourceUiError normalize(Throwable error) {
		while ((error instanceof CompletionException) && (error.getCause() != null)) {
			error = error.getCause();
		}
		if (error instanceof SourceUiFailure failure) return failure.error();
		if (error instanceof CancellationException) return SourceUiError.CANCELLED;
		return SourceUiError.UNKNOWN;
	}

	private static boolean isCancellation(Throwable error) {
		while ((error instanceof CompletionException) && (error.getCause() != null)) {
			error = error.getCause();
		}
		return error instanceof CancellationException;
	}

	public interface Listener {
		void render(SourceUiState state);

		void showEditor(EditorRequest request);

		void confirmRemove(SourceUiItem source);

		void showError(SourceUiError error);
	}

	public record SourceUiState(
			SourceUiSnapshot snapshot, boolean initialLoading, Pending pending) {
		public SourceUiState {
			Objects.requireNonNull(snapshot, "snapshot");
		}

		static SourceUiState initial() {
			return new SourceUiState(SourceUiSnapshot.empty(), false, null);
		}
	}

	public record Pending(Action action, String sourceUuid, boolean cancellable) {
		public Pending {
			Objects.requireNonNull(action, "action");
		}
	}

	public record EditorRequest(String sourceUuid, SourceUiDraft initialDraft) {
		public EditorRequest {
			Objects.requireNonNull(initialDraft, "initialDraft");
		}

		public boolean isEdit() {
			return sourceUuid != null;
		}

		@Override
		public String toString() {
			return "EditorRequest[edit=" + isEdit() + ", draft=redacted]";
		}
	}

	public enum Action {
		LOAD_EDITOR, ADD, EDIT, ENABLE, DISABLE, REFRESH, REMOVE, REORDER
	}
}
