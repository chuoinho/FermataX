package me.aap.fermata.media.pref;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackControlPrefsTest {
	@Test
	public void videoControlsDefaultToEightSecondTouchTimeout() {
		assertEquals(8, PlaybackControlPrefs.VIDEO_CONTROL_TOUCH_DELAY
				.getDefaultValue().getAsInt());
	}
}
