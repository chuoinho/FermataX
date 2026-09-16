package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeVideoScaleControllerTest {
	@Test
	public void browserFullscreenScaleChangesObjectFitWithoutOwningGeometry() {
		String script = YoutubeVideoScaleController.script(YoutubeAddon.VideoScale.CONTAIN);

		assertTrue(script.contains(":fullscreen video"));
		assertTrue(script.contains(":-webkit-full-screen video"));
		assertTrue(script.contains("object-fit:contain !important"));
		assertFalse(script.contains("position:"));
		assertFalse(script.contains("transform:"));
	}
}
