package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.policy.RuntimeHostMode;

public class YoutubeRuntimePolicyTest {
	@Test
	public void autoApkOnPhoneKeepsAutoPlaybackWithoutAutomotiveChrome() {
		YoutubeRuntimePolicy policy = YoutubeRuntimePolicy.resolve(true, RuntimeHostMode.PHONE);

		assertTrue(policy.supportsAutomaticFullscreen());
		assertTrue(policy.keepsPlaybackWhenUiPauses());
		assertFalse(policy.automotivePresentation());
	}

	@Test
	public void mobileApkOnPhoneUsesMobilePlaybackContract() {
		YoutubeRuntimePolicy policy = YoutubeRuntimePolicy.resolve(false, RuntimeHostMode.PHONE);

		assertFalse(policy.supportsAutomaticFullscreen());
		assertFalse(policy.keepsPlaybackWhenUiPauses());
		assertFalse(policy.automotivePresentation());
	}

	@Test
	public void autoProjectionUsesAutoPlaybackAndAutomotiveChrome() {
		YoutubeRuntimePolicy policy =
				YoutubeRuntimePolicy.resolve(true, RuntimeHostMode.AA_PROJECTION);

		assertTrue(policy.supportsAutomaticFullscreen());
		assertTrue(policy.keepsPlaybackWhenUiPauses());
		assertTrue(policy.automotivePresentation());
	}
}
