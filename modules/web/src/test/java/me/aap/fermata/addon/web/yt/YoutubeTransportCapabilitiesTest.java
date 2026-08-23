package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeTransportCapabilitiesTest {
	@Test
	public void previousUsesPlaylistPositionInsteadOfTransientDomButton() {
		String script = YoutubeTransportCapabilities.previousAvailabilityScript();

		assertTrue(script.contains("getPlaylistIndex"));
		assertTrue(script.contains("return i>0"));
		assertTrue(script.contains("searchParams.get('index')"));
		assertTrue(script.contains("i>1"));
		assertFalse(script.contains(".ytp-prev-button"));
		assertFalse(script.contains("player-middle-controls-prev-next-button"));
	}
}
