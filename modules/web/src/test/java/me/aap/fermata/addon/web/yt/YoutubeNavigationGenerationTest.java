package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeNavigationGenerationTest {
	@Test
	public void nextNavigationRejectsPreviousCallbacks() {
		YoutubeNavigationGeneration generations = new YoutubeNavigationGeneration();
		long first = generations.next();
		assertTrue(generations.isCurrent(first));

		long second = generations.next();
		assertFalse(generations.isCurrent(first));
		assertTrue(generations.isCurrent(second));
	}

	@Test
	public void closedRuntimeRejectsCallbacksUntilANewWebViewOpens() {
		YoutubeNavigationGeneration generations = new YoutubeNavigationGeneration();
		long old = generations.next();
		generations.closeRuntime();

		assertFalse(generations.isCurrent(old));
		assertTrue(generations.next() == 0L);

		long reopened = generations.openRuntime();
		assertTrue(generations.isCurrent(reopened));
	}
}
