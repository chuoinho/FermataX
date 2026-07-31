package me.aap.fermata.ui.activity;

import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticRecorder;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.utils.async.FutureSupplier;

/** Owns the single foreground content operation presented by the existing activity progress UI. */
public final class AsyncOperationController {
	private final Consumer<Snapshot> listener;
	private final ToLongFunction<OperationType> timeoutMillis;
	private long generation;
	private Snapshot snapshot = new Snapshot(null, State.IDLE, null);
	@Nullable
	private Operation<?> active;

	public AsyncOperationController(Consumer<Snapshot> listener) {
		this(listener, OperationType::timeoutMillis);
	}

	AsyncOperationController(Consumer<Snapshot> listener,
			ToLongFunction<OperationType> timeoutMillis) {
		this.listener = listener;
		this.timeoutMillis = timeoutMillis;
	}

	public <T> Operation<T> start(@NonNull Object owner, @NonNull OperationType type,
			@NonNull FutureSupplier<T> source) {
		Operation<?> previous;
		Snapshot cancelled = null;
		synchronized (this) {
			previous = active;
			if (previous != null) {
				active = null;
				cancelled = snapshot = new Snapshot(previous.token(), State.CANCELLED, null);
			}
		}
		if (previous != null) {
			previous.future().cancel();
			DiagnosticsObserver.contentTerminal(previous.token(), State.CANCELLED, null);
		}
		if (cancelled != null) listener.accept(cancelled);

		FutureSupplier<T> future = applyTimeout(source, type, timeoutMillis.applyAsLong(type));
		Token token;
		Operation<T> operation;
		Snapshot running;
		synchronized (this) {
			token = new Token(++generation, owner, type);
			operation = new Operation<>(token, future);
			active = operation;
			running = snapshot = new Snapshot(token, State.RUNNING, null);
		}
		DiagnosticsObserver.contentStarted(token);
		listener.accept(running);
		future.onCompletion((result, failure) -> complete(operation, failure));
		return operation;
	}

	public boolean cancel(@NonNull FutureSupplier<?> future) {
		Operation<?> operation;
		Snapshot cancelled;
		synchronized (this) {
			operation = active;
			if ((operation == null) || (operation.future() != future)) return false;
			active = null;
			cancelled = snapshot = new Snapshot(operation.token(), State.CANCELLED, null);
		}
		operation.future().cancel();
		DiagnosticsObserver.contentTerminal(operation.token(), State.CANCELLED, null);
		listener.accept(cancelled);
		return true;
	}

	public synchronized boolean isActive(Token token) {
		return (active != null) && active.token().equals(token);
	}

	public synchronized Snapshot getSnapshot() {
		return snapshot;
	}

	private void complete(Operation<?> operation, @Nullable Throwable failure) {
		Snapshot completed;
		synchronized (this) {
			if (active != operation) {
				DiagnosticsObserver.contentCallbackIgnored(operation.token(), failure);
				return;
			}
			active = null;
			State state = completionState(operation.future(), failure);
			completed = snapshot = new Snapshot(operation.token(), state, failure);
		}
		DiagnosticsObserver.contentTerminal(operation.token(), completed.state(), failure);
		listener.accept(completed);
	}

	static State completionState(FutureSupplier<?> future, @Nullable Throwable failure) {
		if ((failure == null) && !future.isCancelled()) return State.SUCCESS;
		if (isTimeout(failure)) return State.TIMED_OUT;
		if (future.isCancelled() || ((failure != null) && isCancellation(failure))) {
			return State.CANCELLED;
		}
		return State.ERROR;
	}

	private static boolean isTimeout(@Nullable Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof TimeoutException) return true;
			if (current.getCause() == current) break;
		}
		return false;
	}

	private static <T> FutureSupplier<T> applyTimeout(FutureSupplier<T> source,
			OperationType type, long timeout) {
		if (timeout <= 0L) return source;
		FutureSupplier<T> timed = source.timeout(timeout, () -> {
			throw new TimeoutException(type.name() + " operation timed out");
		});
		return (source.getExecutor() == null) ? timed :
				timed.withExecutor(source.getExecutor(), false);
	}

	public enum State {
		IDLE,
		RUNNING,
		SUCCESS,
		ERROR,
		CANCELLED,
		TIMED_OUT
	}

	public enum OperationType {
		LEGACY(0L),
		BROWSE(30_000L),
		SEARCH(30_000L),
		REFRESH(60_000L),
		INSTALL(180_000L),
		STREAM_PREPARE(120_000L);

		private final long timeoutMillis;

		OperationType(long timeoutMillis) {
			this.timeoutMillis = timeoutMillis;
		}

		public long timeoutMillis() {
			return timeoutMillis;
		}
	}

	public record Token(long generation, Object owner, OperationType type) {
	}

	public record Snapshot(@Nullable Token token, State state, @Nullable Throwable failure) {
	}

	public record Operation<T>(Token token, FutureSupplier<T> future) {
	}

	/** Typed, local-only diagnostics sink shared by the allowed lifecycle boundaries. */
	public static final class DiagnosticsObserver {
		private static final AtomicLong IDS = new AtomicLong();

		private DiagnosticsObserver() {
		}

		public enum ActivityEvent {
			CREATED("activity_created"), RESUMED("activity_resumed"),
			PAUSED("activity_paused"), DESTROYED("activity_destroyed");

			private final String code;

			ActivityEvent(String code) {
				this.code = code;
			}
		}

		public enum StartupEvent {
			STARTED("aa_startup_started"), SERVICE_ATTEMPT_STARTED("service_attempt_started"),
			SERVICE_READY("service_ready"), SERVICE_ATTEMPT_CALLBACK("service_attempt_callback"),
			SERVICE_FAILED("service_failed"), UI_ATTACH_STARTED("ui_attach_started"),
			UI_READY("ui_ready"), UI_FAILED("ui_failed"), ACTIVITY_DESTROYED("aa_activity_destroyed");

			private final String code;

			StartupEvent(String code) {
				this.code = code;
			}
		}

		public enum NavigationEvent {
			STARTED("navigation_started"), COMPLETED("navigation_completed"),
			FAILED("navigation_failed");

			private final String code;

			NavigationEvent(String code) {
				this.code = code;
			}
		}

		public enum VideoModeEvent {
			CHANGED("video_mode_changed"), FAILED("video_mode_failed");

			private final String code;

			VideoModeEvent(String code) {
				this.code = code;
			}
		}

		public enum AddonEvent {
			INSTALL_STARTED("addon_install_started"), INSTALL_COMPLETED("addon_install_completed"),
			INSTALL_CANCELLED("addon_install_cancelled"),
			INSTALL_FAILED("addon_install_failed"),
			LOAD_STARTED("addon_load_started"), LOAD_CANCELLED("addon_load_cancelled"),
			LOAD_FAILED("addon_load_failed"),
			LOAD_COMMITTED("addon_load_committed"), REPLAY_COMPLETED("addon_replay_completed"),
			UNLOAD_STARTED("addon_unload_started"), UNLOAD_COMPLETED("addon_unload_completed"),
			TOKEN_CREATED("addon_lifecycle_token_created"), TOKEN_REUSED("addon_lifecycle_token_reused"),
			TOKEN_INVALIDATED("addon_lifecycle_token_invalidated"), ACTIVITY_CREATE("addon_activity_create"),
			ACTIVITY_RESUME("addon_activity_resume"), ACTIVITY_PAUSE("addon_activity_pause"),
			ACTIVITY_DESTROY("addon_activity_destroy"), SERVICE_CREATE("addon_service_create"),
			SERVICE_DESTROY("addon_service_destroy"), CALLBACK_FAILED("addon_callback_failed");

			private final String code;

			AddonEvent(String code) {
				this.code = code;
			}
		}

		public enum ContentEvent {
			STARTED("content_operation_started"), COMPLETED("content_operation_completed"),
			FAILED("content_operation_failed"), CANCELLED("content_operation_cancelled"),
			TIMED_OUT("content_operation_timed_out"), CALLBACK_IGNORED("content_callback_ignored");

			private final String code;

			ContentEvent(String code) {
				this.code = code;
			}
		}

		public static long nextId() {
			return IDS.incrementAndGet();
		}

		public static void activity(ActivityEvent event, long activityId) {
			recordEssential("lifecycle", event.code, String.valueOf(activityId),
					attributes("activity_id", activityId));
		}

		public static long navigationStarted(long activityId, int fromId, int targetId) {
			long operationId = nextId();
			recordEssential("navigation", NavigationEvent.STARTED.code, String.valueOf(operationId),
					attributes("activity_id", activityId, "from_id", fromId, "target_id", targetId));
			return operationId;
		}

		public static void navigationCompleted(long operationId, int targetId) {
			recordEssential("navigation", NavigationEvent.COMPLETED.code, String.valueOf(operationId),
					attributes("target_id", targetId));
		}

		public static void navigationFailed(long operationId, int targetId, Throwable failure) {
			recordFailure("navigation", NavigationEvent.FAILED.code, String.valueOf(operationId),
					attributes("target_id", targetId), failure);
		}

		public static void videoMode(VideoModeEvent event, long operationId, boolean enabled,
				boolean automotive, boolean split) {
			recordEssential("playback", event.code, String.valueOf(operationId),
					attributes("enabled", enabled, "automotive", automotive, "split", split));
		}

		public static void startup(StartupEvent event, long generation, long epoch,
				int attempt, Throwable failure) {
			MapBuilder values = new MapBuilder();
			values.put("generation", generation).put("epoch", epoch).put("attempt", attempt);
			boolean failed = (event == StartupEvent.SERVICE_FAILED) ||
					(event == StartupEvent.UI_FAILED);
			if ((failure == null) && !failed) recordEssential("aa_startup", event.code,
					String.valueOf(generation), values.values());
			else recordFailure("aa_startup", event.code, String.valueOf(generation), values.values(), failure);
		}

		public static void addon(AddonEvent event, int addonId, long tokenGeneration,
				long operationId, Throwable failure) {
			MapBuilder values = new MapBuilder();
			values.put("addon_id", addonId).put("token_generation", tokenGeneration);
			String id = (operationId == 0L) ? null : String.valueOf(operationId);
			boolean failed = (event == AddonEvent.INSTALL_FAILED) ||
					(event == AddonEvent.LOAD_FAILED) || (event == AddonEvent.CALLBACK_FAILED);
			boolean cancelled = (event == AddonEvent.INSTALL_CANCELLED) ||
					(event == AddonEvent.LOAD_CANCELLED);
			if (cancelled || ((failure == null) && !failed)) {
				recordEssential("addon", event.code, id, values.values());
			}
			else recordFailure("addon", event.code, id, values.values(), failure);
		}

		public static void startupDetail(StartupEvent event, long generation, long epoch,
				int attempt) {
			MapBuilder values = new MapBuilder();
			values.put("generation", generation).put("epoch", epoch).put("attempt", attempt);
			recordDetailed("aa_startup", event.code, String.valueOf(generation), values.values());
		}

		public static void contentStarted(Token token) {
			recordEssential("content", ContentEvent.STARTED.code, String.valueOf(token.generation()),
					attributes("generation", token.generation(), "type", token.type().ordinal()));
		}

		public static void contentTerminal(Token token, State state, Throwable failure) {
			ContentEvent event = switch (state) {
				case SUCCESS -> ContentEvent.COMPLETED;
				case ERROR -> ContentEvent.FAILED;
				case CANCELLED -> ContentEvent.CANCELLED;
				case TIMED_OUT -> ContentEvent.TIMED_OUT;
				default -> null;
			};
			if (event == null) return;
			MapBuilder values = new MapBuilder();
			values.put("generation", token.generation()).put("type", token.type().ordinal());
			if (failure == null) recordEssential("content", event.code, String.valueOf(token.generation()),
					values.values());
			else recordFailure("content", event.code, String.valueOf(token.generation()),
					values.values(), failure);
		}

		public static void contentCallbackIgnored(Token token, Throwable failure) {
			MapBuilder values = new MapBuilder();
			values.put("generation", token.generation()).put("type", token.type().ordinal());
			if (failure != null) values.put("failure_class_id", failureClassId(failure));
			recordDetailed("content", ContentEvent.CALLBACK_IGNORED.code,
					String.valueOf(token.generation()), values.values());
		}

		private static void recordEssential(String category, String event, String operationId,
				Map<String, ?> values) {
			try {
				AndroidDiagnosticsRuntime runtime = FermataApplication.get().getDiagnostics();
				runtime.recordEssential(category, event, DiagnosticPriority.STATE, operationId, values);
			} catch (Throwable ignored) {
			}
		}

		private static void recordDetailed(String category, String event, String operationId,
				Map<String, ?> values) {
			try {
				AndroidDiagnosticsRuntime runtime = FermataApplication.get().getDiagnostics();
				DiagnosticRecorder recorder = runtime.getRecorder();
				if (recorder != null) recorder.recordDetailed(category, event, operationId, values);
			} catch (Throwable ignored) {
			}
		}

		private static void recordFailure(String category, String event, String operationId,
				Map<String, ?> values, Throwable failure) {
			MapBuilder safe = new MapBuilder();
			if (values != null) safe.putAll(values);
			if (failure != null) safe.put("failure_class_id", failureClassId(failure));
			try {
				AndroidDiagnosticsRuntime runtime = FermataApplication.get().getDiagnostics();
				runtime.recordEssential(category, event, DiagnosticPriority.ERROR, operationId,
						safe.values());
			} catch (Throwable ignored) {
			}
		}

		private static int failureClassId(Throwable failure) {
			return (failure == null) ? 0 : failure.getClass().getName().hashCode();
		}

		private static Map<String, Object> attributes(Object... pairs) {
			MapBuilder values = new MapBuilder();
			for (int i = 0; i + 1 < pairs.length; i += 2) values.put(String.valueOf(pairs[i]), pairs[i + 1]);
			return values.values();
		}

		private static final class MapBuilder {
			private final java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();

			MapBuilder put(String key, Object value) {
				values.put(key, value);
				return this;
			}

			MapBuilder putAll(Map<String, ?> values) {
				if (values != null) this.values.putAll(values);
				return this;
			}

			Map<String, Object> values() {
				return java.util.Collections.unmodifiableMap(values);
			}
		}
	}
}
