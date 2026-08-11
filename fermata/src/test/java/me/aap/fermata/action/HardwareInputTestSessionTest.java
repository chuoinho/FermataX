package me.aap.fermata.action;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class HardwareInputTestSessionTest {
	@After
	public void tearDown() {
		HardwareInputTestSession.resetForTest();
	}

	@Test
	public void crossPathObservationReportsOnePhysicalPress() {
		assertTrue(HardwareInputTestSession.toggle().isStarted());
		HardwareInputEvent activity = HardwareInputEvent.createForTest(
				HardwareInputEvent.Origin.ACTIVITY, KEYCODE_MEDIA_NEXT, ACTION_DOWN, 0,
				100L, 100L);
		HardwareInputEvent media = HardwareInputEvent.createForTest(
				HardwareInputEvent.Origin.MEDIA_SESSION, KEYCODE_MEDIA_NEXT, ACTION_DOWN, 0,
				100L, 101L);

		HardwareInputTestSession.observe(activity, "executed:NEXT");
		HardwareInputTestSession.observe(media, "deduplicated");
		HardwareInputTestSession.Result result = HardwareInputTestSession.toggle();

		assertFalse(result.isStarted());
		assertTrue(result.getSummary().contains("Activity + MediaSession"));
		assertTrue(result.getSummary().contains("executed:NEXT/deduplicated"));
		assertTrue(result.getSummary().contains("presses=1"));
	}
}
