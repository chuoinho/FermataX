package me.aap.fermata.ui.policy;

import me.aap.fermata.R;
import static me.aap.fermata.ui.view.BodyLayout.Mode.BOTH;
import static me.aap.fermata.ui.view.BodyLayout.Mode.FRAME;
import static me.aap.fermata.ui.view.BodyLayout.Mode.VIDEO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackLayoutPolicyTest {
	@Test
	public void splitRequiresEveryPlaybackAndRouteCondition() {
		assertTrue(PlaybackLayoutPolicy.shouldShowSplit(true, true, true,
				true, true, true, true));
		assertFalse(PlaybackLayoutPolicy.shouldShowSplit(false, true, true,
				true, true, true, true));
		assertFalse(PlaybackLayoutPolicy.shouldShowSplit(true, false, true,
				true, true, true, true));
		assertFalse(PlaybackLayoutPolicy.shouldShowSplit(true, true, false,
				true, true, true, true));
		assertFalse(PlaybackLayoutPolicy.shouldShowSplit(true, true, true,
				false, true, true, true));
		assertFalse(PlaybackLayoutPolicy.shouldShowSplit(true, true, true,
				true, false, true, true));
		assertFalse(PlaybackLayoutPolicy.shouldShowSplit(true, true, true,
				true, true, false, true));
		assertFalse(PlaybackLayoutPolicy.shouldShowSplit(true, true, true,
				true, true, true, false));
	}

	@Test
	public void playableChangesPreserveAcceptedFrameVideoAndSplitModes() {
		assertEquals(FRAME, PlaybackLayoutPolicy.getModeOnPlayableChanged(
				VIDEO, false, false, false, false, false));
		assertEquals(FRAME, PlaybackLayoutPolicy.getModeOnPlayableChanged(
				BOTH, true, false, true, true, true));
		assertEquals(FRAME, PlaybackLayoutPolicy.getModeOnPlayableChanged(
				VIDEO, true, true, true, false, true));
		assertEquals(FRAME, PlaybackLayoutPolicy.getModeOnPlayableChanged(
				VIDEO, true, true, true, true, false));
		assertEquals(VIDEO, PlaybackLayoutPolicy.getModeOnPlayableChanged(
				FRAME, true, true, true, true, true));
		assertEquals(BOTH, PlaybackLayoutPolicy.getModeOnPlayableChanged(
				BOTH, true, true, true, true, true));
	}

	@Test
	public void activeVideoSurfaceRefreshesOnlyOutsideFrameMode() {
		assertTrue(PlaybackLayoutPolicy.shouldRefreshVideoInCurrentMode(
				VIDEO, true, true, true, true, true));
		assertTrue(PlaybackLayoutPolicy.shouldRefreshVideoInCurrentMode(
				BOTH, true, true, true, true, true));
		assertFalse(PlaybackLayoutPolicy.shouldRefreshVideoInCurrentMode(
				FRAME, true, true, true, true, true));
		assertFalse(PlaybackLayoutPolicy.shouldRefreshVideoInCurrentMode(
				VIDEO, true, true, true, false, true));
	}

	@Test
	public void leavingVideoUsesFrameOnCarAndSplitOnPhone() {
		assertEquals(FRAME, PlaybackLayoutPolicy.getModeAfterLeavingVideo(true));
		assertEquals(BOTH, PlaybackLayoutPolicy.getModeAfterLeavingVideo(false));
	}

	@Test
	public void externalWebVideoSurvivesFrameAndConfigurationRestoration() {
		assertTrue(PlaybackLayoutPolicy.shouldKeepExternalVideoMode(true, true,
				true, true, false, true, R.id.youtube_fragment));
		assertTrue(PlaybackLayoutPolicy.shouldKeepExternalVideoMode(true, true,
				true, true, false, true, R.id.web_browser_fragment));
		assertFalse(PlaybackLayoutPolicy.shouldKeepExternalVideoMode(true, true,
				true, true, true, true, R.id.youtube_fragment));
		assertFalse(PlaybackLayoutPolicy.shouldKeepExternalVideoMode(true, true,
				true, true, false, true, R.id.tv_fragment));
		assertFalse(PlaybackLayoutPolicy.shouldKeepExternalVideoMode(false, false,
				true, true, false, true, R.id.youtube_fragment));
	}
}
