package me.aap.fermata.ui.policy;

import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;
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
	public void fillCoversViewportWithoutStretching() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(3413, 1920),
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

	@Test
	public void headUnitViewportHonorsEveryScaleMode() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(770, 433),
				VideoSurfaceLayoutPolicy.resolve(770, 700, 1920, 1080, SCALE_BEST, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1244, 700),
				VideoSurfaceLayoutPolicy.resolve(770, 700, 1920, 1080, SCALE_FILL, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1920, 1080),
				VideoSurfaceLayoutPolicy.resolve(770, 700, 1920, 1080, SCALE_ORIGINAL, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(770, 578),
				VideoSurfaceLayoutPolicy.resolve(770, 700, 1920, 1080, SCALE_4_3, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(770, 433),
				VideoSurfaceLayoutPolicy.resolve(770, 700, 1920, 1080, SCALE_16_9, 1));
	}

	@Test
	public void commonAndExtremeViewportsUseTheirMeasuredBounds() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(711, 400),
				VideoSurfaceLayoutPolicy.resolve(800, 400, 1920, 1080, SCALE_BEST, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1024, 576),
				VideoSurfaceLayoutPolicy.resolve(1024, 600, 1920, 1080, SCALE_BEST, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1280, 720),
				VideoSurfaceLayoutPolicy.resolve(1280, 720, 1920, 1080, SCALE_BEST, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(533, 300),
				VideoSurfaceLayoutPolicy.resolve(1200, 300, 1920, 1080, SCALE_BEST, 1));
	}

	@Test
	public void genuineAnamorphicRatioIsNeverDiscardedByHostMode() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(770, 433),
				VideoSurfaceLayoutPolicy.resolve(770, 700, 720, 576, SCALE_BEST, 1.4222f));
	}
}
