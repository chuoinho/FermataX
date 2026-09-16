package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import me.aap.fermata.R;

import org.junit.Test;

public class PlaybackUiPolicyTest {
	@Test
	public void audioPlayerBarShowsOnAddonRoutesButNeverOnPhoneRoots() {
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
	public void phoneRootsNeverShowTheGlobalAudioPlayerBar() {
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(RuntimeHostMode.PHONE,
				true, false, true, R.id.control_fragment));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(RuntimeHostMode.PHONE,
				true, false, true, R.id.dashboard_fragment));
		assertFalse(PlaybackUiPolicy.shouldShowAudioPlayerBar(RuntimeHostMode.PHONE,
				true, false, true, R.id.settings_fragment));
		assertTrue(PlaybackUiPolicy.shouldShowAudioPlayerBar(RuntimeHostMode.PHONE,
				true, false, true, R.id.radio_fragment));
		assertTrue(PlaybackUiPolicy.shouldShowAudioPlayerBar(RuntimeHostMode.AA_PROJECTION,
				true, false, true, R.id.settings_fragment));
	}

	@Test
	public void modalTextInputSuppressesRenderingWithoutLosingPresentationVisibility() {
		assertTrue(PlaybackUiPolicy.shouldRenderPlayerBar(true, false));
		assertFalse(PlaybackUiPolicy.shouldRenderPlayerBar(true, true));
		assertFalse(PlaybackUiPolicy.shouldRenderPlayerBar(false, false));
	}
}
