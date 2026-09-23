package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.web.audio.WebAudioProfile;

public class YoutubeWebAudioBridgeTest {
	@Test
	public void isLimitedToTheTwoSupportedTopLevelYoutubeOrigins() {
		assertTrue(YoutubeWebAudioBridge.isAllowedOrigin("https://m.youtube.com"));
		assertTrue(YoutubeWebAudioBridge.isAllowedOrigin("https://www.youtube.com"));
		assertFalse(YoutubeWebAudioBridge.isAllowedOrigin("https://music.youtube.com"));
		assertFalse(YoutubeWebAudioBridge.isAllowedOrigin("http://m.youtube.com"));
		assertTrue(YoutubeWebAudioBridge.isHostedDocument("https://m.youtube.com/watch?v=abc"));
		assertTrue(YoutubeWebAudioBridge.isHostedDocument("https://www.youtube.com/watch?v=abc"));
		assertFalse(YoutubeWebAudioBridge.isHostedDocument("https://m.youtube.com.evil/watch?v=abc"));
		assertFalse(YoutubeWebAudioBridge.isHostedDocument("https://m.youtube.com:8443/watch?v=abc"));
	}

	@Test
	public void scriptHasOneSourceClaimAndNeutralAfterClaimFailureBehavior() {
		String source = YoutubeWebAudioBridge.shimSource(7L, WebAudioProfile.unity());
		assertTrue(source.contains("window.top !== window"));
		assertTrue(source.contains("fermataActiveContentVideo"));
		assertTrue(source.contains("new WeakMap()"));
		assertTrue(source.contains("encrypted"));
		assertTrue(source.contains("neutral(owner)"));
		assertTrue(source.contains("ownership.get(media) || (profile.m && profile.e)"));
		assertTrue(source.contains("SUPPORTED_ACTIVE"));
		assertTrue(source.contains("addEventListener('pagehide'"));
		assertTrue(source.contains("lowshelf"));
		assertTrue(source.contains("highshelf"));
		assertTrue(source.contains("createDynamicsCompressor()"));
		assertTrue(source.contains("dinhDb"));
		assertFalse(source.contains("setInterval("));
		assertFalse(source.contains("googlevideo"));
		assertFalse(source.contains("addJavascriptInterface"));
		assertEquals(1, occurrences(source, "createMediaElementSource("));
	}

	@Test
	public void profileUpdateAndTeardownAreGenerationBound() {
		assertTrue(YoutubeWebAudioBridge.updateProfileSource(7L,
				WebAudioProfile.unity()).contains("updateProfile(7,"));
		assertTrue(YoutubeWebAudioBridge.teardownSource(7L).contains("teardown(7)"));
	}

	private static int occurrences(String value, String needle) {
		int count = 0, index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
