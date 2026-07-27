package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

public class YoutubeAdSkipScriptTest {
	@Test
	public void detectsSkipControlsAndAdVideosAcrossYouTubePlayerVariants() throws Exception {
		Field field = YoutubeWebView.class.getDeclaredField("AD_SKIP_JS");
		field.setAccessible(true);
		String script = (String) field.get(null);

		int skipClick = script.indexOf("if (visible(skip) && state.attempts < 2)");
		int targetLookup = script.indexOf("const video = adTarget();");
		assertTrue(skipClick >= 0);
		assertTrue(targetLookup > skipClick);
		assertTrue(script.contains("function adTarget()"));
		assertTrue(script.contains("source === state.contentSource"));
		assertTrue(script.contains("const ad = adTarget();"));
		assertTrue(script.contains("if (adShowing() && ad && Number.isFinite(ad.duration)"));
		assertTrue(script.contains("emit('ad-error', state.podKey, state.adId)"));
		assertTrue(script.contains("state.retryAd = function()"));
		assertTrue(script.contains("state.contentDuration = Number(video.duration || 0)"));
		assertTrue(script.contains("state.observedRoot = player || null"));
		assertTrue(script.contains("state.watchdog = setInterval"));
		assertTrue(script.contains("function skipButton()"));
		assertTrue(script.contains("getAttribute('aria-label')"));
		assertTrue(script.contains("label.indexOf('skip ad')"));
		assertTrue(script.contains("video.html5-ad-video"));
		assertTrue(script.contains("video.classList.contains('html5-ad-video')"));
		assertTrue(script.contains("state.podAttempts++"));
		assertTrue(script.contains("if (!video) {"));
		assertTrue(script.contains(".ytp-ad-skip-button-slot button"));
	}
}
