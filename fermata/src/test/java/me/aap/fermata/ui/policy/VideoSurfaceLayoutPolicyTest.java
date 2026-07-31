package me.aap.fermata.ui.policy;

import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.media.pref.MediaPrefs;

public class VideoSurfaceLayoutPolicyTest {
	@Test
	public void globalVideoScaleDefaultsToBestFit() {
		assertEquals(SCALE_BEST, MediaPrefs.VIDEO_SCALE.getDefaultValue().getAsInt());
	}

	@Test
	public void unknownVideoSizeUsesBestFitPlaceholderUntilMetadataArrives() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1080, 608),
				VideoSurfaceLayoutPolicy.resolve(1080, 1920, 0, 0, SCALE_BEST, 1));
	}

	@Test
	public void bestFitPreservesMovieAspectRatio() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1080, 608),
				VideoSurfaceLayoutPolicy.resolve(1080, 1920, 1920, 1080, SCALE_BEST, 1));
	}

	@Test
	public void bestFitPreservesPortraitVideoOnLandscapeSurface() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(608, 1080),
				VideoSurfaceLayoutPolicy.resolve(1920, 1080, 1080, 1920, SCALE_BEST, 1));
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

	@Test
	public void forcedRatioWorksBeforeStreamMetadataArrives() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1080, 810),
				VideoSurfaceLayoutPolicy.resolve(1080, 1920, 0, 0, SCALE_4_3, 1));
	}

	@Test
	public void invalidScaleFallsBackToBestFit() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1080, 608),
				VideoSurfaceLayoutPolicy.resolve(1080, 1920, 1920, 1080, 99, 1));
	}
}
