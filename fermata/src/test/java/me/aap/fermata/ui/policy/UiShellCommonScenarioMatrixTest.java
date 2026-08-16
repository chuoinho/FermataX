package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.ActivityBackAction.HANDLED;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.ActivityBackAction.SHOW_DASHBOARD;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.ActivityBackAction.SHOW_NAV_FRAGMENT;
import static me.aap.fermata.ui.view.BodyLayout.Mode.BOTH;
import static me.aap.fermata.ui.view.BodyLayout.Mode.FRAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UiShellCommonScenarioMatrixTest {
	private static final int DASHBOARD = 1;
	private static final int TV = 2;
	private static final int LOCAL_VIDEO = 3;
	private static final int WEB = 4;
	private static final int AUDIO = 5;

	@Test
	public void dashboardAndTvChromeAreHostIndependent() {
		for (RuntimeHostMode host : RuntimeHostMode.values()) {
			TopBarPolicy.State dashboard = TopBarPolicy.resolve(host, true,
					DASHBOARD, 0, "Dashboard", "", "");
			assertEquals(GONE, dashboard.backVisibility());
			assertEquals("Dashboard", dashboard.title());

			TopBarPolicy.State tvRoot = TopBarPolicy.resolve(host, false,
					TV, 0, "TV", "", "");
			assertEquals(VISIBLE, tvRoot.backVisibility());
			assertEquals("TV", tvRoot.title());

			TopBarPolicy.State tvPlayback = TopBarPolicy.resolve(host, false,
					TV, TV, "TV", "VTV3", "");
			assertEquals(VISIBLE, tvPlayback.backVisibility());
			assertEquals("VTV3", tvPlayback.title());
		}
	}

	@Test
	public void fullscreenSplitAndLocalVideoUseCommonPresentationSemantics() {
		assertEquals(BOTH, BackNavigationPolicy.resolveVideoExitMode(true));
		assertEquals(FRAME, BackNavigationPolicy.resolveVideoExitMode(false));

		PlaybackPresentationReducer.State fullscreen =
				PlaybackPresentationReducer.enterVideo(false, true);
		assertTrue(fullscreen.videoMode());
		assertFalse(fullscreen.splitMode());
		assertFalse(fullscreen.controlsVisible());
		assertTrue(fullscreen.barsHidden());

		PlaybackPresentationReducer.State shown =
				PlaybackPresentationReducer.showControls(fullscreen, 2500, true);
		assertTrue(shown.controlsVisible());
		assertFalse(shown.barsHidden());
		assertTrue(shown.timeoutPending());

		PlaybackPresentationReducer.State split =
				PlaybackPresentationReducer.enterVideo(true, true);
		assertTrue(split.splitMode());
		assertTrue(split.controlsVisible());
		assertFalse(split.barsHidden());
		assertFalse(split.timeoutPending());
		assertEquals(split, PlaybackPresentationReducer.toggleControls(split, 2500, true));

		for (RuntimeHostMode host : RuntimeHostMode.values()) {
			TopBarPolicy.State local = TopBarPolicy.resolve(host, false,
					LOCAL_VIDEO, LOCAL_VIDEO, "Videos", "Movie.mkv", "");
			assertEquals("Movie.mkv", local.title());
			assertEquals(VISIBLE, local.backVisibility());
		}
	}

	@Test
	public void nestedAndNonNavPagesReturnThroughTheCommonHierarchy() {
		assertEquals(HANDLED,
				BackNavigationPolicy.resolveActivityBack(true, true, true, true, false));
		assertEquals(SHOW_DASHBOARD,
				BackNavigationPolicy.resolveActivityBack(true, false, true, true, false));
		assertEquals(SHOW_NAV_FRAGMENT,
				BackNavigationPolicy.resolveActivityBack(true, false, true, false, false));
	}

	@Test
	public void unrelatedAudioPlaybackCannotRewriteBrowseChrome() {
		for (RuntimeHostMode host : RuntimeHostMode.values()) {
			TopBarPolicy.State browsing = TopBarPolicy.resolve(host, false,
					WEB, AUDIO, "Web", "Playing song", "Preparing");
			assertEquals("Web", browsing.title());
			assertEquals(VISIBLE, browsing.backVisibility());
		}
	}
}
