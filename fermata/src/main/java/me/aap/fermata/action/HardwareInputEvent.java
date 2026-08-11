package me.aap.fermata.action;

import static android.view.KeyEvent.KEYCODE_ASSIST;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_CALL;
import static android.view.KeyEvent.KEYCODE_CHANNEL_DOWN;
import static android.view.KeyEvent.KEYCODE_CHANNEL_UP;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_ENDCALL;
import static android.view.KeyEvent.KEYCODE_ESCAPE;
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
import static android.view.KeyEvent.KEYCODE_MENU;
import static android.view.KeyEvent.KEYCODE_NAVIGATE_IN;
import static android.view.KeyEvent.KEYCODE_NAVIGATE_NEXT;
import static android.view.KeyEvent.KEYCODE_NAVIGATE_OUT;
import static android.view.KeyEvent.KEYCODE_NAVIGATE_PREVIOUS;
import static android.view.KeyEvent.KEYCODE_SEARCH;
import static android.view.KeyEvent.KEYCODE_STEM_1;
import static android.view.KeyEvent.KEYCODE_STEM_2;
import static android.view.KeyEvent.KEYCODE_STEM_3;
import static android.view.KeyEvent.KEYCODE_STEM_PRIMARY;
import static android.view.KeyEvent.KEYCODE_VOICE_ASSIST;
import static android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_MUTE;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;

import android.view.KeyEvent;

/** Immutable control-key envelope shared by Activity and MediaSession input paths. */
final class HardwareInputEvent {
	enum Origin { ACTIVITY, MEDIA_SESSION }

	private final Origin origin;
	private final int keyCode;
	private final int action;
	private final int repeatCount;
	private final int scanCode;
	private final int deviceId;
	private final int source;
	private final long downTime;
	private final long eventTime;

	private HardwareInputEvent(Origin origin, int keyCode, int action, int repeatCount,
			int scanCode, int deviceId, int source, long downTime, long eventTime) {
		this.origin = origin;
		this.keyCode = keyCode;
		this.action = action;
		this.repeatCount = repeatCount;
		this.scanCode = scanCode;
		this.deviceId = deviceId;
		this.source = source;
		this.downTime = downTime;
		this.eventTime = eventTime;
	}

	static HardwareInputEvent from(Origin origin, KeyEvent event) {
		return new HardwareInputEvent(origin, event.getKeyCode(), event.getAction(),
				event.getRepeatCount(), event.getScanCode(), event.getDeviceId(), event.getSource(),
				event.getDownTime(), event.getEventTime());
	}

	static HardwareInputEvent createForTest(Origin origin, int keyCode, int action,
			int repeatCount, long downTime, long eventTime) {
		return new HardwareInputEvent(origin, keyCode, action, repeatCount,
				0, 0, 0, downTime, eventTime);
	}

	Origin origin() { return origin; }
	int keyCode() { return keyCode; }
	int action() { return action; }
	int repeatCount() { return repeatCount; }
	int scanCode() { return scanCode; }
	int deviceId() { return deviceId; }
	int source() { return source; }
	long downTime() { return downTime; }
	long eventTime() { return eventTime; }

	boolean isDiagnosticControl() {
		return switch (keyCode) {
			case KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_MEDIA_PLAY, KEYCODE_MEDIA_PAUSE,
					KEYCODE_MEDIA_STOP, KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_PREVIOUS,
					KEYCODE_MEDIA_REWIND, KEYCODE_MEDIA_FAST_FORWARD,
					KEYCODE_MEDIA_SKIP_FORWARD, KEYCODE_MEDIA_SKIP_BACKWARD,
					KEYCODE_HEADSETHOOK, KEYCODE_VOLUME_UP, KEYCODE_VOLUME_DOWN,
					KEYCODE_VOLUME_MUTE, KEYCODE_CHANNEL_UP, KEYCODE_CHANNEL_DOWN,
					KEYCODE_SEARCH, KEYCODE_ASSIST, KEYCODE_VOICE_ASSIST,
					KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT,
					KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_CENTER, KEYCODE_BACK,
					KEYCODE_ESCAPE, KEYCODE_MENU, KEYCODE_CALL, KEYCODE_ENDCALL,
					KEYCODE_NAVIGATE_PREVIOUS, KEYCODE_NAVIGATE_NEXT, KEYCODE_NAVIGATE_IN,
					KEYCODE_NAVIGATE_OUT, KEYCODE_STEM_PRIMARY, KEYCODE_STEM_1,
					KEYCODE_STEM_2, KEYCODE_STEM_3 -> true;
			default -> false;
		};
	}
}
