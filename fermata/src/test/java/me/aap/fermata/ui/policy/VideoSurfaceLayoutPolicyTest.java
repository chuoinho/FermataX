package me.aap.fermata.ui.policy;

import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VideoSurfaceLayoutPolicyTest {
	@Test
	public void unknownVideoSizeKeepsSurfaceVisibleUntilMetadataArrives() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(-1, -1),
				VideoSurfaceLayoutPolicy.resolve(1080, 1920, 0, 0, SCALE_BEST, 1));
	}

	@Test
	public void bestFitPreservesMovieAspectRatio() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1080, 608),
				VideoSurfaceLayoutPolicy.resolve(1080, 1920, 1920, 1080, SCALE_BEST, 1));
	}

	@Test
	public void pixelAspectRatioIsIncluded() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1080, 304),
				VideoSurfaceLayoutPolicy.resolve(1080, 1920, 1920, 1080, SCALE_BEST, 2));
	}

	@Test
	public void fillKeepsRatioEvenWhenSurfaceIsPortrait() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1080, 608),
				VideoSurfaceLayoutPolicy.resolve(1080, 1920, 1920, 1080, SCALE_FILL, 1));
	}
}
