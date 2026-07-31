package me.aap.fermata.ui.view;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackTimerMenuTest {
	@Test
	public void preservesLegacyHourMinuteConversion() {
		assertArrayEquals(new int[]{2, 5}, PlaybackTimerMenu.splitSeconds(7_530));
		assertEquals(7_500, PlaybackTimerMenu.toSeconds(2, 5));
	}
}
