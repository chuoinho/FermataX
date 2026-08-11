package me.aap.fermata.action;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_ASSIST;
import static android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
import static android.view.KeyEvent.KEYCODE_UNKNOWN;
import static android.view.KeyEvent.KEYCODE_VOICE_ASSIST;
import static me.aap.fermata.action.VoiceTriggerController.Decision.CAPTURE;
import static me.aap.fermata.action.VoiceTriggerController.Decision.PASS;
import static me.aap.fermata.action.VoiceTriggerController.Decision.TRIGGER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class VoiceTriggerControllerTest {
	@Test
	public void foregroundBindingTriggersOnlyOnInitialDown() {
		assertEquals(TRIGGER, decide(false, KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_NEXT,
				ACTION_DOWN, 0, true, true));
		assertEquals(PASS, decide(false, KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_NEXT,
				ACTION_DOWN, 1, true, true));
		assertEquals(PASS, decide(false, KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_NEXT,
				ACTION_UP, 0, true, true));
	}

	@Test
	public void backgroundDisabledAndUnassignedBindingsPassThrough() {
		assertEquals(PASS, decide(false, KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_NEXT,
				ACTION_DOWN, 0, true, false));
		assertEquals(PASS, decide(false, KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_NEXT,
				ACTION_DOWN, 0, false, true));
		assertEquals(PASS, decide(false, KEYCODE_UNKNOWN, KEYCODE_MEDIA_NEXT,
				ACTION_DOWN, 0, true, true));
	}

	@Test
	public void captureAcceptsMediaKeyButRejectsAssistantKeys() {
		assertEquals(CAPTURE, decide(true, KEYCODE_UNKNOWN, KEYCODE_MEDIA_NEXT,
				ACTION_DOWN, 0, true, true));
		assertEquals(PASS, decide(true, KEYCODE_UNKNOWN, KEYCODE_ASSIST,
				ACTION_DOWN, 0, true, true));
		assertEquals(PASS, decide(true, KEYCODE_UNKNOWN, KEYCODE_VOICE_ASSIST,
				ACTION_DOWN, 0, true, true));
		assertFalse(VoiceTriggerController.isSupportedKeyCode(KEYCODE_ASSIST));
		assertFalse(VoiceTriggerController.isSupportedKeyCode(KEYCODE_VOICE_ASSIST));
	}

	private static VoiceTriggerController.Decision decide(boolean capture, int binding,
			int keyCode, int action, int repeat, boolean enabled, boolean foreground) {
		return VoiceTriggerController.decide(capture, binding, keyCode, action, repeat,
				enabled, foreground);
	}
}
