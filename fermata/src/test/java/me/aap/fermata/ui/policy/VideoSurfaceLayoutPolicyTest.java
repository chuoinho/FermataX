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

	@Test
	public void automotiveContainFitsCommonHeadUnitViewports() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(711, 400),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						800, 400, 1920, 1080, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1024, 576),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						1024, 600, 1920, 1080, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(1280, 720),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						1280, 720, 1920, 1080, 1));
	}

	@Test
	public void automotiveContainIgnoresDuplicateWidescreenPixelCorrection() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(770, 433),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						770, 700, 1920, 1080, 1.4222f));
	}

	@Test
	public void automotiveContainRetainsGenuineAnamorphicPixelCorrection() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(770, 433),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						770, 700, 720, 576, 1.4222f));
	}

	@Test
	public void automotiveContainNeverCropsFourByThreeOrPortraitVideo() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(770, 578),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						770, 700, 640, 480, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(394, 700),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						770, 700, 1080, 1920, 1));
	}

	@Test
	public void automotiveContainStaysInsideExtremeViewportBounds() {
		assertEquals(new VideoSurfaceLayoutPolicy.Size(800, 450),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						800, 800, 1920, 1080, 1));
		assertEquals(new VideoSurfaceLayoutPolicy.Size(533, 300),
				VideoSurfaceLayoutPolicy.resolveAutomotiveContain(
						1200, 300, 1920, 1080, 1));
	}
}
