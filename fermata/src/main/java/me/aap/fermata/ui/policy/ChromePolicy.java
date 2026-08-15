package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.TopBarController;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * Compatibility facade for legacy chrome call sites.
 *
 * <p>Top-bar semantics live in {@link TopBarPolicy}; rendering lives in
 * {@link TopBarController}. New code should use those authorities directly.</p>
 */
public final class ChromePolicy {
	private ChromePolicy() {
	}

	public static int getTopBackVisibility(MainActivityDelegate a,
														 @Nullable ActivityFragment f) {
		return isTopBackVisible(a, f) ? VISIBLE : GONE;
	}

	public static boolean isTopBackVisible(MainActivityDelegate a,
												 @Nullable ActivityFragment f) {
		ControlPanelView panel = a.getControlPanel();
		boolean playerBackOwned = a.isVideoMode() && (panel != null) &&
				panel.isVideoControlsVisible();
		return TopBarPolicy.isTopBackVisible(a.getRuntimeHostMode(), a.getBody().isFrameMode(),
				a.isVideoMode(), f instanceof DashboardFragment,
				PlaybackUiPolicy.shouldShowAudioPlayerBar(a), playerBackOwned);
	}

	static boolean isTopBackVisible(RuntimeHostMode hostMode, boolean frameMode,
											 boolean videoMode, boolean dashboardFragment,
											 boolean audioPlayerBarVisible, boolean playerBackOwned) {
		return TopBarPolicy.isTopBackVisible(hostMode, frameMode, videoMode, dashboardFragment,
				audioPlayerBarVisible, playerBackOwned);
	}

	public static void refreshTopBackButton(MainActivityDelegate a) {
		TopBarController.refresh(a);
	}
}
