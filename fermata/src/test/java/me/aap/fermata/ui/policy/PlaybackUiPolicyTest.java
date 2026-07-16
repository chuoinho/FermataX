package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackUiPolicyTest {
	@Test
	public void audioPlayerBarRequiresMatchingAudioRoute() {
		assertTrue(PlaybackUiPolicy.shouldShowAudioPlayerBar(true, false, true, 10, 10));
		assertTrue(PlaybackUiPolicy.shouldShowAudioPlayerBar(true, false, true, 0, 0));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(false, false, true, 10, 10));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(true, true, true, 10, 10));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(true, false, false, 0, 0));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(true, false, true, 10, 11));
	}
}
