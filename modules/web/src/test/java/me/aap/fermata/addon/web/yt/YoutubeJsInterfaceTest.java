package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeJsInterfaceTest {

	@Test
	public void playbackIntentAcceptsOnlyYoutubePages() {
		assertTrue(YoutubeJsInterface.isYoutubePageUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
		assertTrue(YoutubeJsInterface.isYoutubePageUrl("https://m.youtube.com/shorts/dQw4w9WgXcQ"));
		assertTrue(YoutubeJsInterface.isYoutubePageUrl("https://youtu.be/dQw4w9WgXcQ"));

		assertFalse(YoutubeJsInterface.isYoutubePageUrl("javascript:alert(document.cookie)"));
		assertFalse(YoutubeJsInterface.isYoutubePageUrl("file:///data/data/x/shared_prefs/a.xml"));
		assertFalse(YoutubeJsInterface.isYoutubePageUrl("https://evil.example/watch?v=dQw4w9WgXcQ"));
		assertFalse(YoutubeJsInterface.isYoutubePageUrl("https://youtube.com.evil.example/watch"));
		assertFalse(YoutubeJsInterface.isYoutubePageUrl("intent://x#Intent;end"));
		assertFalse(YoutubeJsInterface.isYoutubePageUrl("not a url"));
		assertFalse(YoutubeJsInterface.isYoutubePageUrl(null));
	}
}
