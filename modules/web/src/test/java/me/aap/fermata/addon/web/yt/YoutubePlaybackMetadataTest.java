package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class YoutubePlaybackMetadataTest {
	@Test
	public void structuredSignalCarriesStableTitleAndUrls() {
		String page = "https://m.youtube.com/watch?v=abc";
		String media = "blob:https://m.youtube.com/media|1";
		String title = "A title | with Unicode tiếng Việt";
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse("ytv1|" +
				encode(page) + '|' + encode(media) + '|' + encode(title), "fallback");

		assertEquals(page, signal.pageUrl());
		assertEquals(media, signal.mediaUrl());
		assertEquals(title, signal.title());
	}

	@Test
	public void legacySignalRemainsSupported() {
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse("media-url",
				"https://m.youtube.com/watch?v=legacy");

		assertEquals("https://m.youtube.com/watch?v=legacy", signal.pageUrl());
		assertEquals("media-url", signal.mediaUrl());
		assertEquals("", signal.title());
	}

	@Test
	public void genericYoutubeTitleCannotReplaceRealTitle() {
		YoutubePlaybackMetadata metadata = new YoutubePlaybackMetadata();
		String page = "https://m.youtube.com/watch?v=abc";

		assertTrue(metadata.apply(new YoutubePlaybackMetadata.Signal(page, "", "Real video - YouTube")));
		assertEquals("Real video", metadata.getTitle());
		assertFalse(metadata.apply(new YoutubePlaybackMetadata.Signal(page, "", "YouTube")));
		assertEquals("Real video", metadata.getTitle());
		assertTrue(metadata.matches("Real video - YouTube"));
	}

	@Test
	public void newPlaybackIdentityClearsPreviousTitle() {
		YoutubePlaybackMetadata metadata = new YoutubePlaybackMetadata();
		metadata.apply(new YoutubePlaybackMetadata.Signal("video-1", "", "First"));

		assertTrue(metadata.apply(new YoutubePlaybackMetadata.Signal("video-2", "", "")));
		assertEquals("", metadata.getTitle());
		assertTrue(metadata.apply(new YoutubePlaybackMetadata.Signal("video-2", "", "(3) Second | YouTube")));
		assertEquals("Second", metadata.getTitle());
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
