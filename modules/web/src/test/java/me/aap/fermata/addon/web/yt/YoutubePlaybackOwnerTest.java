package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class YoutubePlaybackOwnerTest {
	@Test
	public void recentOwnerSurvivesMatchingWebPlaybackIdentity() {
		YoutubePlaybackOwner<String> owner = new YoutubePlaybackOwner<>();
		owner.prepare("recent-item", "video-1", true);
		owner.retain("video-1");

		assertEquals("recent-item", owner.resolve("web-current"));
	}

	@Test
	public void autoNextReleasesPreviousRecentOwner() {
		YoutubePlaybackOwner<String> owner = new YoutubePlaybackOwner<>();
		owner.prepare("recent-item", "video-1", true);
		owner.retain("video-2");

		assertEquals("web-current", owner.resolve("web-current"));
	}

}
