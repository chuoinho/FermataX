package me.aap.fermata.action;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HardwareInputDeduplicatorTest {
	@Test
	public void suppressesSameGestureAcrossActivityAndMediaSession() {
		HardwareInputDeduplicator deduplicator = new HardwareInputDeduplicator();
		HardwareInputEvent activity = event(HardwareInputEvent.Origin.ACTIVITY,
				ACTION_DOWN, 0, 100L, 110L);
		HardwareInputEvent media = event(HardwareInputEvent.Origin.MEDIA_SESSION,
				ACTION_DOWN, 0, 100L, 120L);

		assertFalse(deduplicator.isDuplicate(activity));
		assertTrue(deduplicator.isDuplicate(media));
	}

	@Test
	public void keepsDistinctPressesAndSameOriginEvents() {
		HardwareInputDeduplicator deduplicator = new HardwareInputDeduplicator();
		assertFalse(deduplicator.isDuplicate(event(HardwareInputEvent.Origin.ACTIVITY,
				ACTION_DOWN, 0, 100L, 110L)));
		assertFalse(deduplicator.isDuplicate(event(HardwareInputEvent.Origin.ACTIVITY,
				ACTION_DOWN, 0, 100L, 115L)));
		assertFalse(deduplicator.isDuplicate(event(HardwareInputEvent.Origin.MEDIA_SESSION,
				ACTION_DOWN, 0, 200L, 210L)));
	}

	@Test
	public void downAndUpAreDeduplicatedIndependently() {
		HardwareInputDeduplicator deduplicator = new HardwareInputDeduplicator();
		assertFalse(deduplicator.isDuplicate(event(HardwareInputEvent.Origin.ACTIVITY,
				ACTION_DOWN, 0, 100L, 100L)));
		assertTrue(deduplicator.isDuplicate(event(HardwareInputEvent.Origin.MEDIA_SESSION,
				ACTION_DOWN, 0, 100L, 101L)));
		assertFalse(deduplicator.isDuplicate(event(HardwareInputEvent.Origin.ACTIVITY,
				ACTION_UP, 0, 100L, 180L)));
		assertTrue(deduplicator.isDuplicate(event(HardwareInputEvent.Origin.MEDIA_SESSION,
				ACTION_UP, 0, 100L, 181L)));
	}

	@Test
	public void tenThousandPressesExecuteExactlyOncePerCrossPathPair() {
		HardwareInputDeduplicator deduplicator = new HardwareInputDeduplicator();
		int accepted = 0;
		for (int i = 0; i < 10_000; i++) {
			long time = 1_000L + (i * 10L);
			if (!deduplicator.isDuplicate(event(HardwareInputEvent.Origin.ACTIVITY,
					ACTION_DOWN, 0, time, time))) accepted++;
			if (!deduplicator.isDuplicate(event(HardwareInputEvent.Origin.MEDIA_SESSION,
					ACTION_DOWN, 0, time, time + 1L))) accepted++;
		}
		assertTrue(accepted == 10_000);
	}

	private static HardwareInputEvent event(HardwareInputEvent.Origin origin, int action,
			int repeat, long downTime, long eventTime) {
		return HardwareInputEvent.createForTest(origin, KEYCODE_MEDIA_NEXT, action,
				repeat, downTime, eventTime);
	}
}
