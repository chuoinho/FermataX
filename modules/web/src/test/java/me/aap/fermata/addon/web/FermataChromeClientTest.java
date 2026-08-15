package me.aap.fermata.addon.web;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FermataChromeClientTest {
	@Test
	public void browserBackedFullscreenDoesNotRequireADecoderSurface() {
		assertTrue(FermataChromeClient.isCustomViewSurfaceReady(null));
	}
}
