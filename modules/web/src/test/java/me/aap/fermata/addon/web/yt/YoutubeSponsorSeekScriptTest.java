package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeSponsorSeekScriptTest {
	@Test
	public void convertsNativeMillisecondsToHtmlMediaSeconds() {
		String script = YoutubeWebView.sponsorSeekScript(7L, "video-id", 880_789L);

		assertTrue(script.contains("v.currentTime = targetMillis / 1000;"));
		assertTrue(script.contains(") (7, "));
		assertTrue(script.contains(", 880789);"));
	}
}
