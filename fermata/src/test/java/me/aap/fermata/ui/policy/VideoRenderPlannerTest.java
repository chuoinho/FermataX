package me.aap.fermata.ui.policy;

import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoRenderPlannerTest {
	private static final VideoFormatSnapshot HD =
			new VideoFormatSnapshot(1920, 1080, 1920, 1080, 1);

	@Test
	public void unmeasuredViewportDefersWithoutInventingGeometry() {
		VideoRenderPlan plan = VideoRenderPlanner.plan(new VideoViewport(0, 700), HD, SCALE_BEST);

		assertTrue(plan.isDeferred());
		assertEquals(0, plan.viewportWidth());
		assertEquals(700, plan.viewportHeight());
		assertEquals(VideoSurfaceLayoutPolicy.MATCH_PARENT, plan.contentWidth());
		assertEquals(VideoSurfaceLayoutPolicy.MATCH_PARENT, plan.surfaceHeight());
		assertFalse(plan.provisional());
	}

	@Test
	public void unknownFormatKeepsTheMeasuredViewportUntilMetadataArrives() {
		VideoRenderPlan plan = VideoRenderPlanner.plan(new VideoViewport(770, 700),
				VideoFormatSnapshot.unknown(), SCALE_BEST);

		assertEquals(new VideoRenderPlan(770, 700, 770, 700,
				VideoSurfaceLayoutPolicy.MATCH_PARENT, VideoSurfaceLayoutPolicy.MATCH_PARENT,
				SCALE_BEST, true), plan);
	}

	@Test
	public void callbackFallbackSuppliesGeometryUntilTheEngineSnapshotArrives() {
		VideoFormatSnapshot callback = new VideoFormatSnapshot(720, 576, 720, 576, 1);
		VideoFormatSnapshot format = VideoFormatSnapshot.unknown().withFallback(callback);

		assertEquals(callback, format);
		assertEquals(new VideoRenderPlan(770, 700, 770, 616, 770, 616,
				SCALE_BEST, false),
				VideoRenderPlanner.plan(new VideoViewport(770, 700), format, SCALE_BEST));
	}

	@Test
	public void engineSnapshotKeepsItsVisibleFrameInsteadOfBeingReplacedByCallbackFallback() {
		VideoFormatSnapshot engine = new VideoFormatSnapshot(1920, 1088, 1920, 1080, 1);
		VideoFormatSnapshot callback = new VideoFormatSnapshot(720, 576, 720, 576, 1);

		assertEquals(engine, engine.withFallback(callback));
	}

	@Test
	public void bestFitUsesVisibleFrameAndPixelAspectRatio() {
		VideoFormatSnapshot format = new VideoFormatSnapshot(720, 576, 704, 576, 16f / 11f);
		VideoRenderPlan plan = VideoRenderPlanner.plan(new VideoViewport(770, 700), format,
				SCALE_BEST);

		assertEquals(770, plan.contentWidth());
		assertEquals(432, plan.contentHeight());
		assertEquals(788, plan.surfaceWidth());
		assertEquals(432, plan.surfaceHeight());
		assertFalse(plan.provisional());
	}

	@Test
	public void completeVisibleFrameIsRetainedWhenCodedFrameHasDecoderPadding() {
		VideoRenderPlan plan = VideoRenderPlanner.plan(new VideoViewport(770, 700),
				new VideoFormatSnapshot(1920, 1088, 1920, 1080, 1), SCALE_BEST);

		assertEquals(770, plan.contentWidth());
		assertEquals(432, plan.contentHeight());
		assertEquals(770, plan.surfaceWidth());
		assertEquals(770, plan.surfaceWidth());
		assertEquals(436, plan.surfaceHeight());
	}

	@Test
	public void everyScaleModeKeepsCurrentPolicySemantics() {
		VideoViewport viewport = new VideoViewport(770, 700);
		assertPlan(viewport, SCALE_BEST, 770, 432);
		assertPlan(viewport, SCALE_FILL, 1244, 700);
		assertPlan(viewport, SCALE_ORIGINAL, 1920, 1080);
		assertPlan(viewport, SCALE_4_3, 770, 578);
		assertPlan(viewport, SCALE_16_9, 770, 432);
	}

	@Test
	public void portraitVideoUsesItsActualViewportInFullscreenAndSplitView() {
		VideoFormatSnapshot portrait = new VideoFormatSnapshot(1080, 1920, 1080, 1920, 1);
		assertEquals(new VideoRenderPlan(770, 700, 394, 700, 394, 700, SCALE_BEST, false),
				VideoRenderPlanner.plan(new VideoViewport(770, 700), portrait, SCALE_BEST));
		assertEquals(new VideoRenderPlan(500, 700, 394, 700, 394, 700, SCALE_BEST, false),
				VideoRenderPlanner.plan(new VideoViewport(500, 700), portrait, SCALE_BEST));
	}

	@Test
	public void invalidFormatAndScaleFallBackSafely() {
		VideoRenderPlan invalid = VideoRenderPlanner.plan(new VideoViewport(770, 700),
				new VideoFormatSnapshot(Float.NaN, 0, -1, Float.POSITIVE_INFINITY, Float.NaN), 99);

		assertEquals(new VideoRenderPlan(770, 700, 770, 700,
				VideoSurfaceLayoutPolicy.MATCH_PARENT, VideoSurfaceLayoutPolicy.MATCH_PARENT,
				SCALE_BEST, true), invalid);
	}

	@Test
	public void normalUncroppedPlansMatchExistingSurfacePolicy() {
		VideoViewport[] viewports = {
				new VideoViewport(770, 700), new VideoViewport(770, 433),
				new VideoViewport(500, 700), new VideoViewport(1200, 300)};
		int[] scales = {SCALE_BEST, SCALE_FILL, SCALE_ORIGINAL, SCALE_4_3, SCALE_16_9};

		for (VideoViewport viewport : viewports) {
			for (int scale : scales) {
				VideoSurfaceLayoutPolicy.Size expected = VideoSurfaceLayoutPolicy.resolve(
						viewport.width(), viewport.height(), 1920, 1080, scale, 1);
				VideoRenderPlan actual = VideoRenderPlanner.plan(viewport, HD, scale);
				assertEquals(even(expected.width()), actual.contentWidth());
				assertEquals(even(expected.height()), actual.contentHeight());
				assertEquals(even(expected.width()), actual.surfaceWidth());
				assertEquals(even(expected.height()), actual.surfaceHeight());
			}
		}
	}

	@Test
	public void plannerIsHostIndependentAndDeterministic() {
		VideoViewport viewport = new VideoViewport(770, 350);
		VideoRenderPlan first = VideoRenderPlanner.plan(viewport, HD, SCALE_BEST);
		VideoRenderPlan second = VideoRenderPlanner.plan(viewport, HD, SCALE_BEST);

		assertEquals(new VideoRenderPlan(770, 350, 622, 350, 622, 350, SCALE_BEST, false), first);
		assertEquals(first, second);
	}

	@Test
	public void finalGeometryIsEvenAcrossScaleViewportAndFormatMatrix() {
		VideoViewport[] viewports = {
				new VideoViewport(1920, 720), new VideoViewport(1280, 720),
				new VideoViewport(800, 480), new VideoViewport(640, 360),
				new VideoViewport(0, 0)};
		VideoFormatSnapshot[] formats = {
				HD,
				new VideoFormatSnapshot(640, 480, 640, 480, 1),
				new VideoFormatSnapshot(1920, 1088, 1920, 1080, 1),
				new VideoFormatSnapshot(720, 576, 704, 576, 16f / 15f),
				VideoFormatSnapshot.unknown()};
		int[] scales = {SCALE_BEST, SCALE_FILL, SCALE_ORIGINAL, SCALE_4_3, SCALE_16_9};

		for (VideoViewport viewport : viewports) {
			for (VideoFormatSnapshot format : formats) {
				for (int scale : scales) {
					VideoRenderPlan plan = VideoRenderPlanner.plan(viewport, format, scale);
					if (plan.isDeferred()) continue;
					if (plan.provisional()) {
						assertEquals(VideoSurfaceLayoutPolicy.MATCH_PARENT, plan.surfaceWidth());
						assertEquals(VideoSurfaceLayoutPolicy.MATCH_PARENT, plan.surfaceHeight());
						continue;
					}
					assertTrue(plan.contentWidth() > 0);
					assertTrue(plan.contentHeight() > 0);
					assertTrue(plan.surfaceWidth() > 0);
					assertTrue(plan.surfaceHeight() > 0);
					assertEquals(0, plan.contentWidth() & 1);
					assertEquals(0, plan.contentHeight() & 1);
					assertEquals(0, plan.surfaceWidth() & 1);
					assertEquals(0, plan.surfaceHeight() & 1);
					assertTrue(plan.surfaceWidth() >= plan.contentWidth());
					assertTrue(plan.surfaceHeight() >= plan.contentHeight());
					if ((format.codedWidth() == format.visibleWidth()) &&
							(format.codedHeight() == format.visibleHeight())) {
						assertEquals(plan.contentWidth(), plan.surfaceWidth());
						assertEquals(plan.contentHeight(), plan.surfaceHeight());
					}
				}
			}
		}
	}

	@Test
	public void provisionalPlanNeverExposesFinalSurfaceSize() {
		VideoRenderPlan plan = VideoRenderPlanner.plan(new VideoViewport(2340, 1080),
				VideoFormatSnapshot.unknown(), SCALE_BEST);

		assertTrue(plan.provisional());
		assertFalse(plan.hasFinalSurfaceSize());
	}

	@Test
	public void knownFormatsAlwaysExposeFinalSurfaceSizeForEveryScaleMode() {
		int[] scales = {SCALE_BEST, SCALE_FILL, SCALE_ORIGINAL, SCALE_4_3, SCALE_16_9};
		for (int scale : scales) {
			VideoRenderPlan plan = VideoRenderPlanner.plan(new VideoViewport(2340, 1080), HD, scale);
			assertTrue(plan.hasFinalSurfaceSize());
			assertTrue(plan.surfaceWidth() > 0);
			assertTrue(plan.surfaceHeight() > 0);
		}
	}

	private static void assertPlan(VideoViewport viewport, int scale, int width, int height) {
		assertEquals(new VideoRenderPlan(viewport.width(), viewport.height(), width, height, width,
				height, scale, false),
				VideoRenderPlanner.plan(viewport, HD, scale));
	}

	private static int even(int dimension) {
		return dimension & ~1;
	}
}
