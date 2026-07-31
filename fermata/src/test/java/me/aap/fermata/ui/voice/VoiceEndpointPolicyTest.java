package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VoiceEndpointPolicyTest {
	@Test
	public void keepsIndependentTimeouts() {
		VoiceEndpointPolicy p = new VoiceEndpointPolicy(10, 20, 30, 40, 3);
		assertEquals(10, p.getNoSpeechTimeoutMs());
		assertEquals(20, p.getFinalResultTimeoutMs());
		assertEquals(30, p.getHardSessionTimeoutMs());
		assertEquals(40, p.getStablePartialDelayMs());
		assertEquals(3, p.getStablePartialRepetitions());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsSinglePartialAsStable() {
		new VoiceEndpointPolicy(10, 20, 30, 40, 1);
	}

	@Test
	public void onlyClosedPlaybackCommandsMayFinalizeAdaptively() {
		VoiceEndpointPolicy p = VoiceEndpointPolicy.DEFAULT;
		assertTrue(p.isAdaptiveCandidate(VoiceIntent.playback(VoiceIntent.PlaybackAction.PAUSE)));
		assertTrue(p.isAdaptiveCandidate(VoiceIntent.playback(VoiceIntent.PlaybackAction.STOP)));
		assertTrue(p.isAdaptiveCandidate(
				VoiceIntent.playback(VoiceIntent.PlaybackAction.OPEN_CURRENT)));
		assertFalse(p.isAdaptiveCandidate(VoiceIntent.playback(VoiceIntent.PlaybackAction.PLAY)));
		assertFalse(p.isAdaptiveCandidate(
				VoiceIntent.playback(VoiceIntent.PlaybackAction.PLAY_FAVORITES)));
		assertFalse(p.isAdaptiveCandidate(
				VoiceIntent.search(VoiceIntent.SearchAction.PLAY, "youtube", "numb")));
	}
}
