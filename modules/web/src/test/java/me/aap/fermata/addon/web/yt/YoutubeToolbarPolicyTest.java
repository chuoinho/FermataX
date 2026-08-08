package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeToolbarPolicyTest {
	@Test
	public void directWatchPageShowsBackWithoutWebHistory() {
		assertTrue(YoutubeToolbarPolicy.showBack(false, false, false, true,
				"https://m.youtube.com/watch?v=nufLYe9Bg0E"));
	}

	@Test
	public void shortsPageShowsBackWithoutWebHistory() {
		assertTrue(YoutubeToolbarPolicy.showBack(false, false, false, true,
				"https://m.youtube.com/shorts/nufLYe9Bg0E"));
	}

	@Test
	public void homeDoesNotReusePlaybackTitleOrForceBack() {
		String home = "https://m.youtube.com/";
		assertFalse(YoutubeToolbarPolicy.showBack(false, false, false, true, home));
		assertFalse(YoutubeToolbarPolicy.usePlaybackTitle(home, true));
	}

	@Test
	public void playbackTitleRequiresCurrentYoutubeOwner() {
		String watch = "https://m.youtube.com/watch?v=nufLYe9Bg0E";
		assertTrue(YoutubeToolbarPolicy.usePlaybackTitle(watch, true));
		assertFalse(YoutubeToolbarPolicy.usePlaybackTitle(watch, false));
	}
}
