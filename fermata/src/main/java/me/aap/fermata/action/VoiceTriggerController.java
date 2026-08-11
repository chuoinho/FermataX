package me.aap.fermata.action;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_CHANNEL_DOWN;
import static android.view.KeyEvent.KEYCODE_CHANNEL_UP;
import static android.view.KeyEvent.KEYCODE_HEADSETHOOK;
import static android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
import static android.view.KeyEvent.KEYCODE_MEDIA_PAUSE;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
import static android.view.KeyEvent.KEYCODE_MEDIA_REWIND;
import static android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_STOP;
import static android.view.KeyEvent.KEYCODE_SEARCH;
import static android.view.KeyEvent.KEYCODE_UNKNOWN;

import androidx.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Cancellable;

/** Foreground-only, single-press voice trigger and one-shot Settings capture session. */
final class VoiceTriggerController {
	static final long CAPTURE_TIMEOUT_MS = 10_000L;
	private final Set<MainActivityDelegate> resumedHosts = new LinkedHashSet<>();
	@Nullable private Capture capture;
	private int activeKeyCode = KEYCODE_UNKNOWN;
	private long activeDownTime = Long.MIN_VALUE;

	synchronized void onHostResumed(MainActivityDelegate host) {
		resumedHosts.remove(host);
		resumedHosts.add(host);
	}

	synchronized void onHostPaused(MainActivityDelegate host) {
		resumedHosts.remove(host);
		Capture current = capture;
		if ((current != null) && (current.host == host)) cancelCapture(current);
	}

	synchronized FutureSupplier<Integer> beginCapture(MainActivityDelegate host) {
		Capture previous = capture;
		if (previous != null) cancelCapture(previous);
		Promise<Integer> promise = new Promise<>();
		Capture next = new Capture(host, promise);
		capture = next;
		next.timeout = host.postDelayed(() -> timeout(next), CAPTURE_TIMEOUT_MS);
		return promise;
	}

	synchronized void clearBinding() {
		MainActivityPrefs.get().applyIntPref(MainActivityPrefs.VOICE_TRIGGER_KEY_CODE,
				KEYCODE_UNKNOWN);
	}

	synchronized int getBinding() {
		return MainActivityPrefs.get().getIntPref(MainActivityPrefs.VOICE_TRIGGER_KEY_CODE);
	}

	synchronized boolean intercept(@Nullable MainActivityDelegate activity,
			HardwareInputEvent input) {
		if (ownsActiveGesture(input)) {
			if (input.action() == ACTION_UP) clearActiveGesture();
			return true;
		}

		Capture current = capture;
		MainActivityDelegate host = foregroundHost(activity);
		Decision decision = decide(current != null, getBinding(), input.keyCode(), input.action(),
				input.repeatCount(), MainActivityPrefs.get().getVoiceControlEnabledPref(), host != null);
		if (decision == Decision.CAPTURE) {
			claimGesture(input);
			capture = null;
			current.timeout.cancel();
			MainActivityPrefs.get().applyIntPref(MainActivityPrefs.VOICE_TRIGGER_KEY_CODE,
					input.keyCode());
			current.host.post(() -> current.promise.complete(input.keyCode()));
			return true;
		}
		if (decision != Decision.TRIGGER) return false;
		assert host != null;
		claimGesture(input);
		host.post(() -> {
			if (isUsable(host)) host.startGlobalVoiceControl();
		});
		return true;
	}

	synchronized void onCancelled(HardwareInputEvent input) {
		if (ownsActiveGesture(input)) clearActiveGesture();
	}

	synchronized void close() {
		Capture current = capture;
		if (current != null) cancelCapture(current);
		resumedHosts.clear();
		clearActiveGesture();
	}

	static boolean isSupportedKeyCode(int keyCode) {
		return switch (keyCode) {
			case KEYCODE_MEDIA_PLAY, KEYCODE_MEDIA_PAUSE, KEYCODE_MEDIA_PLAY_PAUSE,
					KEYCODE_MEDIA_STOP, KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_PREVIOUS,
					KEYCODE_MEDIA_REWIND, KEYCODE_MEDIA_FAST_FORWARD,
					KEYCODE_MEDIA_SKIP_BACKWARD, KEYCODE_MEDIA_SKIP_FORWARD,
					KEYCODE_HEADSETHOOK, KEYCODE_SEARCH, KEYCODE_CHANNEL_UP,
					KEYCODE_CHANNEL_DOWN -> true;
			default -> false;
		};
	}

	static Decision decide(boolean captureActive, int binding, int keyCode, int action,
			int repeatCount, boolean voiceEnabled, boolean foreground) {
		if ((action != ACTION_DOWN) || (repeatCount != 0)) return Decision.PASS;
		if (captureActive) {
			return isSupportedKeyCode(keyCode) ? Decision.CAPTURE : Decision.PASS;
		}
		return voiceEnabled && foreground && (binding != KEYCODE_UNKNOWN) &&
				(binding == keyCode) ? Decision.TRIGGER : Decision.PASS;
	}

	enum Decision { PASS, CAPTURE, TRIGGER }

	private MainActivityDelegate foregroundHost(@Nullable MainActivityDelegate activity) {
		if ((activity != null) && resumedHosts.contains(activity) && isUsable(activity)) {
			return activity;
		}
		MainActivityDelegate fallback = null;
		for (MainActivityDelegate host : resumedHosts) {
			if (!isUsable(host)) continue;
			if (host.isCarActivityNotMirror()) return host;
			fallback = host;
		}
		return fallback;
	}

	private static boolean isUsable(MainActivityDelegate host) {
		return host.isHostResumed() && !host.getAppActivity().isDestroyed() &&
				!host.getAppActivity().isFinishing();
	}

	private boolean ownsActiveGesture(HardwareInputEvent input) {
		return (activeKeyCode == input.keyCode()) && (activeDownTime == input.downTime());
	}

	private void claimGesture(HardwareInputEvent input) {
		activeKeyCode = input.keyCode();
		activeDownTime = input.downTime();
	}

	private void clearActiveGesture() {
		activeKeyCode = KEYCODE_UNKNOWN;
		activeDownTime = Long.MIN_VALUE;
	}

	private synchronized void timeout(Capture expected) {
		if (capture != expected) return;
		capture = null;
		expected.promise.completeExceptionally(
				new TimeoutException("Voice trigger capture timed out"));
	}

	private void cancelCapture(Capture current) {
		if (capture == current) capture = null;
		current.timeout.cancel();
		current.promise.cancel();
	}

	private static final class Capture {
		private final MainActivityDelegate host;
		private final Promise<Integer> promise;
		private Cancellable timeout = Cancellable.CANCELED;

		private Capture(MainActivityDelegate host, Promise<Integer> promise) {
			this.host = host;
			this.promise = promise;
		}
	}
}
