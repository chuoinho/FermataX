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
		assertTrue(YoutubePlaybackMetadata.isStructuredSignal("ytv1|" + encode(page) + '|' +
				encode(media) + '|' + encode(title)));
		assertEquals(0L, YoutubePlaybackMetadata.playbackGeneration("ytv1|" + encode(page) + '|' +
				encode(media) + '|' + encode(title)));
	}

	@Test
	public void v2SignalCarriesAndValidatesPlayerVideoIdentity() {
		String page = "https://m.youtube.com/watch?v=abc123";
		String media = "blob:https://m.youtube.com/media";
		String signal = "ytv2|" + encode(page) + '|' + encode(media) + "|" + encode("Title") +
				"|7|" + encode("abc123");

		YoutubePlaybackMetadata.Signal parsed = YoutubePlaybackMetadata.parse(signal, "fallback");
		assertTrue(YoutubePlaybackMetadata.isStructuredSignal(signal));
		assertEquals(7L, YoutubePlaybackMetadata.playbackGeneration(signal));
		assertEquals("abc123", parsed.videoId());
		assertTrue(YoutubePlaybackMetadata.hasConsistentVideoIdentity(parsed));
		assertTrue(YoutubePlaybackMetadata.hasConsistentVideoIdentity(signal, parsed));

		String missingPlayerId = "ytv2|" + encode(page) + '|' + encode(media) + "|" +
				encode("Title") + "|7|";
		assertFalse(YoutubePlaybackMetadata.hasConsistentVideoIdentity(missingPlayerId,
				YoutubePlaybackMetadata.parse(missingPlayerId, "fallback")));

		YoutubePlaybackMetadata.Signal stale = new YoutubePlaybackMetadata.Signal(
				page, media, "Title", 7L, "stale456");
		assertFalse(YoutubePlaybackMetadata.hasConsistentVideoIdentity(stale));
	}

	@Test
	public void v2SignalCarriesAudioStateForAutoNext() {
		String page = "https://m.youtube.com/watch?v=abc123";
		String audible = "ytv2|" + encode(page) + "||Title|7|abc123|0|0.65";
		String muted = "ytv2|" + encode(page) + "||Title|7|abc123|1|0.65";

		YoutubePlaybackMetadata.Signal audibleSignal =
				YoutubePlaybackMetadata.parse(audible, "fallback");
		assertTrue(YoutubePlaybackMetadata.isStructuredSignal(audible));
		assertTrue(audibleSignal.isAudible());
		assertEquals(0.65d, audibleSignal.volume(), 0.001d);
		assertFalse(YoutubePlaybackMetadata.parse(muted, "fallback").isAudible());
	}

	@Test
	public void legacySignalRemainsSupported() {
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse("media-url",
				"https://m.youtube.com/watch?v=legacy");

		assertEquals("https://m.youtube.com/watch?v=legacy", signal.pageUrl());
		assertEquals("media-url", signal.mediaUrl());
		assertEquals("", signal.title());
		assertFalse(YoutubePlaybackMetadata.isStructuredSignal("media-url"));
		assertTrue(YoutubePlaybackMetadata.isStructuredSignal("ytv1||||"));
		assertTrue(YoutubePlaybackMetadata.hasConsistentVideoIdentity("ytv1||||",
				YoutubePlaybackMetadata.parse("ytv1||||", "fallback")));
		assertEquals(17L, YoutubePlaybackMetadata.playbackGeneration("ytv1||||17"));
		assertEquals(0L, YoutubePlaybackMetadata.playbackGeneration("ytv1||||bad"));
		assertFalse(YoutubePlaybackMetadata.isStructuredSignal("ytv1|only"));
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
