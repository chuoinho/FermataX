package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeNavigationRecoveryTest {
	@Test
	public void nextAndPreviousPreferPlayerApiWithCurrentButtonFallbacks() {
		String next = YoutubeScripts.prevNext(true);
		String previous = YoutubeScripts.prevNext(false);

		assertTrue(next.contains("nextVideo"));
		assertTrue(next.contains(".ytp-next-button"));
		assertTrue(next.contains("player-middle-controls-prev-next-button"));
		assertTrue(next.contains("mobile.length > 1 ? mobile[1]"));
		assertTrue(previous.contains("previousVideo"));
		assertTrue(previous.contains(".ytp-prev-button"));
		assertTrue(previous.contains("mobile.length > 0 ? mobile[0]"));
	}

	@Test
	public void reloadDispatchesOnceForCurrentGeneration() {
		assertTrue(YoutubeReloadCoordinator.shouldDispatch(4, 4, -1));
		assertFalse(YoutubeReloadCoordinator.shouldDispatch(4, 4, 4));
		assertFalse(YoutubeReloadCoordinator.shouldDispatch(3, 4, -1));
	}

	@Test
	public void reloadCaptureHasADeadline() {
		assertTrue(YoutubeReloadCoordinator.CAPTURE_TIMEOUT_MS > 0L);
	}
}
