package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VoiceUiPolicyTest {
	@Test
	public void toolbarButtonFollowsVoicePreferenceOnCarAndMobile() {
		assertTrue(VoiceUiPolicy.showToolbarButton(true, true));
		assertFalse(VoiceUiPolicy.showToolbarButton(true, false));
		assertTrue(VoiceUiPolicy.showToolbarButton(false, true));
		assertFalse(VoiceUiPolicy.showToolbarButton(false, false));
	}
}
