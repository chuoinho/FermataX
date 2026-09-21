package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Guards the player-header timeline mapping restored from the former Control screen. */
public class PhonePlaybackCarHeaderTimeTest {
	@Test
	public void displayUsesElapsedSecondsWhileSeekProgressRoundsUp() {
		assertEquals(0, PhonePlaybackCarHeaderController.displaySeconds(0L));
		assertEquals(1, PhonePlaybackCarHeaderController.progressSeconds(0L));
		assertEquals(1, PhonePlaybackCarHeaderController.displaySeconds(1_999L));
		assertEquals(2, PhonePlaybackCarHeaderController.progressSeconds(1_001L));
	}
}
