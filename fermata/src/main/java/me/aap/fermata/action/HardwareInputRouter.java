package me.aap.fermata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.Nullable;

import me.aap.fermata.diagnostics.DiagnosticEvent;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticScope;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.log.Log;

/** One input authority per MediaSessionCallback. */
public final class HardwareInputRouter {
	private static final int DBL_CLICK_INTERVAL = 500;
	private static final int LONG_CLICK_INTERVAL = 1000;
	private final MediaSessionCallback callback;
	private final HardwareInputDeduplicator deduplicator = new HardwareInputDeduplicator();
	private final VoiceTriggerController voiceTrigger = new VoiceTriggerController();
	private Worker worker;
	private boolean closed;

	public HardwareInputRouter(MediaSessionCallback callback) {
		this.callback = callback;
	}

	public boolean handleMediaEvent(KeyEvent event,
			IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return handle(null, HardwareInputEvent.Origin.MEDIA_SESSION, event, defaultHandler);
	}

	public boolean handleActivityEvent(MainActivityDelegate activity, KeyEvent event,
			IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return handle(activity, HardwareInputEvent.Origin.ACTIVITY, event, defaultHandler);
	}

	public synchronized void close() {
		closed = true;
		worker = null;
		deduplicator.clear();
		voiceTrigger.close();
	}

	public void onHostResumed(MainActivityDelegate activity) {
		voiceTrigger.onHostResumed(activity);
	}

	public void onHostPaused(MainActivityDelegate activity) {
		voiceTrigger.onHostPaused(activity);
	}

	public me.aap.utils.async.FutureSupplier<Integer> beginVoiceTriggerCapture(
			MainActivityDelegate activity) {
		return voiceTrigger.beginCapture(activity);
	}

	public void clearVoiceTriggerBinding() {
		voiceTrigger.clearBinding();
	}

	public int getVoiceTriggerBinding() {
		return voiceTrigger.getBinding();
	}

	private synchronized boolean handle(@Nullable MainActivityDelegate activity,
			HardwareInputEvent.Origin origin, KeyEvent event,
			IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		Log.i((activity == null) ? "Media: " : "Activity: ", event);
		HardwareInputEvent input = HardwareInputEvent.from(origin, event);
		diagnostic("input_received", input, null, null, null);
		HardwareInputTestSession.observe(input, "received");

		if (closed) {
			diagnostic("input_rejected", input, null, null, "router_closed");
			HardwareInputTestSession.observe(input, "router_closed");
			return defaultHandler.apply(event.getKeyCode(), event);
		}
		if (deduplicator.isDuplicate(input)) {
			diagnostic("input_deduplicated", input, Key.get(event.getKeyCode()), null,
					"cross_path_duplicate");
			HardwareInputTestSession.observe(input, "deduplicated");
			return true;
		}
		if (event.isCanceled()) {
			worker = null;
			voiceTrigger.onCancelled(input);
			diagnostic("input_rejected", input, null, null, "cancelled");
			HardwareInputTestSession.observe(input, "cancelled");
			return defaultHandler.apply(event.getKeyCode(), event);
		}
		if (voiceTrigger.intercept(activity, input)) {
			worker = null;
			diagnostic("input_executed", input, Key.get(input.keyCode()),
					Action.ACTIVATE_VOICE_CTRL, "foreground_voice_trigger");
			HardwareInputTestSession.observe(input, "voice_trigger");
			return true;
		}

		if (worker != null) {
			if (worker.handle(event, input)) return true;
			worker = null;
			return false;
		}

		int code = event.getKeyCode();
		Key key = Key.get(code);
		if (key == null) return delegate(input, event, defaultHandler, "unmapped_key");
		if (!key.isMedia() && (activity != null) &&
				(activity.getCurrentFocus() instanceof EditText)) {
			return delegate(input, event, defaultHandler, "text_input_focused");
		}

		Action doubleAction = key.getDblClickAction();
		if (doubleAction == null) return delegate(input, event, defaultHandler, "invalid_double_action");
		int action = event.getAction();
		if (action == ACTION_MULTIPLE) {
			Log.i(key, " key double click");
			performAction(doubleAction, activity, input, key, uptimeMillis());
			return true;
		}
		if (action != ACTION_DOWN) return delegate(input, event, defaultHandler,
				"non_down_without_gesture");

		Action clickAction = key.getClickAction();
		if (clickAction == null) return delegate(input, event, defaultHandler, "invalid_click_action");
		Action longAction = key.getLongClickAction();
		if (longAction == null) return delegate(input, event, defaultHandler, "invalid_long_action");
		if (key.isCompatibilityOptIn() && (clickAction == Action.NONE) &&
				(doubleAction == Action.NONE) && (longAction == Action.NONE)) {
			return delegate(input, event, defaultHandler, "compatibility_key_disabled");
		}
		diagnostic("input_mapped", input, key, clickAction, null);
		HardwareInputTestSession.observe(input, "mapped:" + clickAction.name());

		if (((clickAction == doubleAction) && (clickAction == longAction)) ||
				((doubleAction == Action.NONE) && (longAction == Action.NONE))) {
			Log.i(key, " key click");
			performAction(clickAction, activity, input, key, uptimeMillis());
			return true;
		}

		worker = new Worker(activity, key, clickAction, doubleAction, longAction, input);
		return true;
	}

	private boolean delegate(HardwareInputEvent input, KeyEvent rawEvent,
			IntObjectFunction<KeyEvent, Boolean> defaultHandler, String reason) {
		diagnostic("input_delegated", input, null, null, reason);
		HardwareInputTestSession.observe(input, "delegated:" + reason);
		return defaultHandler.apply(input.keyCode(), rawEvent);
	}

	private void performAction(Action action, @Nullable MainActivityDelegate activity,
			HardwareInputEvent input, Key key, long timestamp) {
		worker = null;
		if ((activity != null) && (activity.getAppActivity().isDestroyed() ||
				activity.getAppActivity().isFinishing())) activity = null;
		Log.i("Performing action ", action);
		action.getHandler().handle(callback, activity, timestamp);
		diagnostic("input_executed", input, key, action, null);
		HardwareInputTestSession.observe(input, "executed:" + action.name());
	}

	private void diagnostic(String name, HardwareInputEvent input, @Nullable Key key,
			@Nullable Action action, @Nullable String reason) {
		if (!input.isDiagnosticControl()) return;
		try {
			AndroidDiagnosticsRuntime runtime = AndroidDiagnosticsRuntime.get();
			if (!runtime.isDetailedEnabled()) return;
			DiagnosticEvent.Builder event = DiagnosticEvent.builder("hardware_input", name)
					.scope(DiagnosticScope.DETAILED)
					.priority(DiagnosticPriority.DETAIL)
					.put("key_code", input.keyCode())
					.put("key_action", input.action())
					.put("repeat_count", input.repeatCount())
					.put("scan_code", input.scanCode())
					.put("device_id", input.deviceId())
					.put("input_source", input.source())
					.put("input_origin", input.origin().name())
					.put("session_active", callback.getSession().isActive());
			PlaybackSnapshot snapshot = callback.getPlaybackSnapshot();
			if (snapshot != null) {
				event.put("playback_revision", snapshot.getRevision())
						.put("playback_state", snapshot.getState().getState())
						.put("supported_actions", snapshot.getState().getActions())
						.put("owns_playback", snapshot.isCanonical());
			}
			if (key != null) event.put("mapped_key", key.name());
			if (action != null) event.put("media_action", action.name());
			if (reason != null) event.put("reason_code", reason);
			runtime.record(event.build());
		} catch (Throwable ignored) {
			// Input handling must remain independent from diagnostics availability.
		}
	}

	private final class Worker implements Runnable {
		@Nullable private MainActivityDelegate activity;
		private final Key key;
		private final Action clickAction;
		private final Action doubleAction;
		private final Action longAction;
		private final HardwareInputEvent initialInput;
		private final long time;
		private long longClickTime;
		private boolean up;

		Worker(@Nullable MainActivityDelegate activity, Key key, Action clickAction,
				Action doubleAction, Action longAction, HardwareInputEvent initialInput) {
			this.activity = activity;
			this.key = key;
			this.clickAction = clickAction;
			this.doubleAction = doubleAction;
			this.longAction = longAction;
			this.initialInput = initialInput;
			time = longClickTime = uptimeMillis();
			schedule(DBL_CLICK_INTERVAL);
		}

		@Override
		public void run() {
			synchronized (HardwareInputRouter.this) {
				if (closed || (worker != this)) return;
				if (up) {
					Log.i(key, " key click");
					handle(clickAction);
					return;
				}
				long now = uptimeMillis();
				long diff = now - longClickTime;
				if (diff < LONG_CLICK_INTERVAL) {
					schedule(LONG_CLICK_INTERVAL - diff);
				} else if (diff > 15_000L) {
					worker = null;
				} else {
					longClickTime = time;
					Log.i(key, " key long click");
					handle(longAction);
					worker = this;
					schedule(LONG_CLICK_INTERVAL);
				}
			}
		}

		boolean handle(KeyEvent event, HardwareInputEvent input) {
			if (event.getKeyCode() != key.getCode()) return false;
			switch (event.getAction()) {
				case ACTION_DOWN -> {
					if (!up && ((longAction == clickAction) || (longAction == Action.NONE))) {
						Log.i(key, " key click");
						handle(clickAction);
					}
					return true;
				}
				case ACTION_UP -> {
					long holdTime = uptimeMillis() - time;
					if (holdTime <= DBL_CLICK_INTERVAL) {
						if (up) {
							Log.i(key, " key double click");
							handle(doubleAction);
						} else if (doubleAction == clickAction) {
							Log.i(key, " key click");
							handle(clickAction);
						} else {
							up = true;
						}
					} else if (holdTime >= LONG_CLICK_INTERVAL) {
						worker = null;
					} else {
						worker = null;
						if (longClickTime == time) {
							Log.i(key, " key click");
							handle(clickAction);
						}
					}
					return true;
				}
				case ACTION_MULTIPLE -> {
					Log.i(key, " key double click");
					handle(doubleAction);
					return true;
				}
				default -> {
					return false;
				}
			}
		}

		private void handle(Action action) {
			performAction(action, activity, initialInput, key, time);
		}

		private void schedule(long delay) {
			callback.getHandler().postDelayed(this, delay);
		}
	}
}
