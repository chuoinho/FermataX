package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.View;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.utils.ui.fragment.ActivityFragment;

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
		boolean playerBackOwned = isPlayerBackPresentation(a) && (panel != null) &&
				panel.isVideoControlsVisible();
		return isTopBackVisible(a.getRuntimeHostMode(), a.getBody().isFrameMode(),
				a.isVideoMode(), f instanceof DashboardFragment,
				PlaybackUiPolicy.shouldShowAudioPlayerBar(a), playerBackOwned);
	}

	static boolean isTopBackVisible(RuntimeHostMode hostMode, boolean frameMode,
											 boolean videoMode, boolean dashboardFragment,
											 boolean audioPlayerBarVisible, boolean playerBackOwned) {
		if ((hostMode == null) || dashboardFragment) return false;
		if (!hostMode.usesAutomotivePresentation()) return !playerBackOwned;
		return (frameMode || videoMode) && !playerBackOwned && !audioPlayerBarVisible;
	}

	public static boolean isPlayerBackPresentation(MainActivityDelegate a) {
		return isPlayerBackPresentation(a.getRuntimeHostMode(), a.getBody().isVideoMode(),
				a.isVideoMode());
	}

	static boolean isPlayerBackPresentation(RuntimeHostMode hostMode, boolean bodyVideoMode,
														 boolean appVideoMode) {
		if (hostMode == null) return false;
		return hostMode.usesAutomotivePresentation() ? appVideoMode : bodyVideoMode;
	}

	public static void refreshTopBackButton(MainActivityDelegate a) {
		View back = a.getToolBar().findViewById(me.aap.utils.R.id.tool_bar_back_button);
		if (back == null) return;
		back.setVisibility(getTopBackVisibility(a, a.getActiveFragment()));
	}
}
