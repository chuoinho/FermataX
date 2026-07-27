package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class YoutubeAdSignalTest {
	@Test
	public void parsesEncodedSignalWithGeneration() {
		String page = "https://m.youtube.com/watch?v=video-1&feature=test";
		YoutubeAdSignal signal = YoutubeAdSignal.parse("ytad1|ad-start|pod-1|ad-1|" +
				URLEncoder.encode(page, StandardCharsets.UTF_8) + "|17");

		assertEquals(YoutubeAdSignal.Phase.AD_START, signal.phase());
		assertEquals("pod-1", signal.podId());
		assertEquals("ad-1", signal.adId());
		assertEquals(page, signal.pageUrl());
		assertEquals(17L, signal.generation());
	}

	@Test
	public void rejectsMalformedStaleAndIncompleteSignals() {
		assertNull(YoutubeAdSignal.parse(null));
		assertNull(YoutubeAdSignal.parse("ytad1|ad-start|pod|ad|page"));
		assertNull(YoutubeAdSignal.parse("ytad1|unknown|pod|ad|page|1"));
		assertNull(YoutubeAdSignal.parse("ytad1|ad-start||ad|page|1"));
		assertNull(YoutubeAdSignal.parse("ytad1|ad-start|pod||page|1"));
		assertNull(YoutubeAdSignal.parse("ytad1|content|||page|0"));
		assertNull(YoutubeAdSignal.parse("ytad1|content|||page|bad"));
	}

	@Test
	public void parsesRetryableAdErrorSignal() {
		YoutubeAdSignal signal = YoutubeAdSignal.parse(
				"ytad1|ad-error|pod-1|ad-1|https%3A%2F%2Fm.youtube.com%2Fwatch%3Fv%3Dabc123|9");

		assertNotNull(signal);
		assertEquals(YoutubeAdSignal.Phase.AD_ERROR, signal.phase());
		assertEquals(9L, signal.generation());
	}
}
