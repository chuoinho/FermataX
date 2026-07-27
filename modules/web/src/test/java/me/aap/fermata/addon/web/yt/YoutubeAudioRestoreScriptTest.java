package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeAudioRestoreScriptTest {
	@Test
	public void restoreIsScopedToExpectedVideoAndClampedVolume() {
		String script = YoutubeWebView.audibleRestoreScript("video-id", 150);

		assertTrue(script.contains("fermataPageVideoId()!==expectedId"));
		assertTrue(script.contains("unMute"));
		assertTrue(script.endsWith(",100)"));
		assertFalse(script.contains("location.reload"));
	}
}
