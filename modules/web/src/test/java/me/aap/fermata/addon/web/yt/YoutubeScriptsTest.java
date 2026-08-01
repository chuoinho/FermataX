package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeScriptsTest {
	@Test
	public void preservesNullJavascriptFallbacks() {
		assertEquals("", YoutubeScripts.decodeJavascriptString("null"));
		assertEquals("", YoutubeScripts.decodeJavascriptString(null));
	}

	@Test
	public void retainedScriptsExposeTheirBehaviorMarkers() {
		assertTrue(YoutubeScripts.VOICE_RESULTS.contains("result.length >= 3"));
		assertTrue(YoutubeScripts.CLEAR_HIGHEST_VIDEO_QUALITY.contains("window.__fermataQ = null"));
		assertTrue(YoutubeScripts.PLAYBACK_SIGNAL.contains("function fermataVideoSignal(video)"));
		assertTrue(YoutubeScripts.PLAYBACK_SIGNAL.contains("fermataPlaybackIdentityMatchesPage()"));
		assertTrue(YoutubeScripts.AD_SKIP.contains("state.configure = function(skipEnabled, eventCode)"));
		assertTrue(YoutubeScripts.AD_SKIP.contains("state.retryAd = function()"));
	}

	@Test
	public void videoQualityMenuUsesPlayerApiWithoutOpeningYoutubeDomMenus() {
		String script = YoutubeScripts.videoQualities("window.Fermata.event", 42, "Auto");

		assertTrue(script.contains("getAvailableQualityLevels"));
		assertTrue(script.contains("getPlaybackQuality"));
		assertTrue(script.contains("window.__fermataQualityLevels"));
		assertFalse(script.contains(".player-quality-settings"));
		assertFalse(script.contains(".player-settings-icon"));
	}

	@Test
	public void videoQualitySelectionUsesStableLevelCapturedByMenu() {
		String script = YoutubeScripts.setVideoQuality(3);

		assertTrue(script.contains("window.__fermataQualityLevels"));
		assertTrue(script.contains("setPlaybackQualityRange"));
		assertTrue(script.contains("setPlaybackQuality"));
		assertTrue(script.contains("})(3)"));
	}
}
