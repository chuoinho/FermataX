package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FermataServiceUiBinderTest {
	@Test
	public void playbackErrorAlwaysHasDisplayableText() {
		assertEquals("fallback", FermataServiceUiBinder.normalizePlaybackError(null, "fallback"));
		assertEquals("fallback", FermataServiceUiBinder.normalizePlaybackError("", "fallback"));
		assertEquals("network error",
				FermataServiceUiBinder.normalizePlaybackError("network error", "fallback"));
	}

	@Test
	public void selectingCurrentItemDoesNotCreateAnUnfinishablePlaybackRequest() {
		assertFalse(FermataServiceUiBinder.shouldCreatePlaybackRequest(true, -1));
		assertFalse(FermataServiceUiBinder.shouldCreatePlaybackRequest(true, 0));
		assertTrue(FermataServiceUiBinder.shouldCreatePlaybackRequest(true, 1));
		assertTrue(FermataServiceUiBinder.shouldCreatePlaybackRequest(false, -1));
	}
}
