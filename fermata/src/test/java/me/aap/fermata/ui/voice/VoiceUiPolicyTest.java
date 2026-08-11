package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static me.aap.fermata.ui.policy.RuntimeHostMode.AA_PROJECTION;
import static me.aap.fermata.ui.policy.RuntimeHostMode.MIRROR;
import static me.aap.fermata.ui.policy.RuntimeHostMode.PHONE;

import org.junit.Test;

public class VoiceUiPolicyTest {
	@Test
	public void everyHostUsesTheSharedNavVoiceEntry() {
		assertFalse(VoiceUiPolicy.showToolbarButton(AA_PROJECTION, true));
		assertTrue(VoiceUiPolicy.showNavBarButton(AA_PROJECTION, true));
		assertFalse(VoiceUiPolicy.showToolbarButton(PHONE, true));
		assertTrue(VoiceUiPolicy.showNavBarButton(PHONE, true));
		assertFalse(VoiceUiPolicy.showToolbarButton(MIRROR, true));
		assertTrue(VoiceUiPolicy.showNavBarButton(MIRROR, true));
	}

	@Test
	public void disabledVoiceHasNoSharedTouchEntry() {
		for (var host : new me.aap.fermata.ui.policy.RuntimeHostMode[]{
				PHONE, AA_PROJECTION, MIRROR}) {
			assertFalse(VoiceUiPolicy.showToolbarButton(host, false));
			assertFalse(VoiceUiPolicy.showNavBarButton(host, false));
		}
	}

	@Test
	public void enabledVoiceHasExactlyOnePrimarySurfacePerHost() {
		for (var host : new me.aap.fermata.ui.policy.RuntimeHostMode[]{
				PHONE, AA_PROJECTION, MIRROR}) {
			int surfaces = (VoiceUiPolicy.showToolbarButton(host, true) ? 1 : 0) +
					(VoiceUiPolicy.showNavBarButton(host, true) ? 1 : 0);
			assertEquals(host.name(), 1, surfaces);
		}
	}
}
