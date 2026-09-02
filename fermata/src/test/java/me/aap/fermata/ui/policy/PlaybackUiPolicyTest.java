package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackUiPolicyTest {
	@Test
	public void audioPlayerBarShowsOutsideDashboardRegardlessOfAddonRoute() {
		int dashboard = 1;
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(
				true, false, true, dashboard, dashboard));
		assertTrue(PlaybackUiPolicy.shouldShowAudioPlayerBar(
				true, false, true, 20, dashboard));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(
				true, true, true, 20, dashboard));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(
				false, false, true, 20, dashboard));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(
				true, false, false, 20, dashboard));
	}

	@Test
	public void modalTextInputSuppressesRenderingWithoutLosingPresentationVisibility() {
		assertTrue(PlaybackUiPolicy.shouldRenderPlayerBar(true, false));
		assertFalse(PlaybackUiPolicy.shouldRenderPlayerBar(true, true));
		assertFalse(PlaybackUiPolicy.shouldRenderPlayerBar(false, false));
	}
}
