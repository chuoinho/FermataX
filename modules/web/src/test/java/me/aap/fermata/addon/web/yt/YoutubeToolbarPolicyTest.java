package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeToolbarPolicyTest {
	@Test
	public void homeDoesNotReusePlaybackTitle() {
		assertFalse(YoutubeToolbarPolicy.usePlaybackTitle("https://m.youtube.com/", true));
	}

	@Test
	public void watchAndShortsPagesUsePlaybackTitleForCurrentYoutubeOwner() {
		assertTrue(YoutubeToolbarPolicy.usePlaybackTitle(
				"https://m.youtube.com/watch?v=nufLYe9Bg0E", true));
		assertTrue(YoutubeToolbarPolicy.usePlaybackTitle(
				"https://m.youtube.com/shorts/nufLYe9Bg0E", true));
	}

	@Test
	public void playbackTitleRequiresCurrentYoutubeOwner() {
		String watch = "https://m.youtube.com/watch?v=nufLYe9Bg0E";
		assertFalse(YoutubeToolbarPolicy.usePlaybackTitle(watch, false));
	}
}
