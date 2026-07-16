package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FermataServiceUiBinderTest {
	@Test
	public void playbackErrorAlwaysHasDisplayableText() {
		assertEquals("fallback", FermataServiceUiBinder.normalizePlaybackError(null, "fallback"));
		assertEquals("fallback", FermataServiceUiBinder.normalizePlaybackError("", "fallback"));
		assertEquals("network error",
				FermataServiceUiBinder.normalizePlaybackError("network error", "fallback"));
	}
}
