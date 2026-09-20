package me.aap.fermata.addon.web.yt;

import org.junit.Test;
import static org.junit.Assert.*;

public class YoutubePlaybackHostPolicyTest {
	@Test public void newUserVideoCanForwardFromCurrentPhoneButTransportCannot() {
		assertTrue(YoutubePlaybackHostPolicy.forwardSelection(true, true, false, true));
		assertFalse(YoutubePlaybackHostPolicy.forwardSelection(true, true, true, true));
		assertFalse(YoutubePlaybackHostPolicy.forwardSelection(true, false, false, false));
		assertFalse(YoutubePlaybackHostPolicy.forwardSelection(false, false, false, true));
	}
	@Test public void runtimeNeverFallsBackToWrongHostOrLostAdmission() {
		assertFalse(YoutubePlaybackHostPolicy.attachHost(true, false, true));
		assertFalse(YoutubePlaybackHostPolicy.attachHost(false, true, true));
		assertFalse(YoutubePlaybackHostPolicy.attachHost(true, true, false));
		assertTrue(YoutubePlaybackHostPolicy.attachHost(true, true, true));
	}
	@Test public void offDoesNotPreferVisibleCarOverPhoneSelection() {
		assertTrue(YoutubePlaybackHostPolicy.prefersHost(false, false));
		assertFalse(YoutubePlaybackHostPolicy.prefersHost(true, false));
		assertTrue(YoutubePlaybackHostPolicy.prefersHost(true, true));
		assertFalse(YoutubePlaybackHostPolicy.prefersHost(false, true));
	}
}
