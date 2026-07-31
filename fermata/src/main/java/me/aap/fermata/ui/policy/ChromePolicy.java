package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.View;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.utils.ui.fragment.ActivityFragment;

public final class ChromePolicy {
	private ChromePolicy() {
	}

	public static int getAutoTopBackVisibility(MainActivityDelegate a,
																						 @Nullable ActivityFragment f) {
		return isAutoTopBackVisible(a, f) ? VISIBLE : GONE;
	}

	public static boolean isAutoTopBackVisible(MainActivityDelegate a,
															 @Nullable ActivityFragment f) {
		return isAutoTopBackVisible(a.getRuntimeHostMode(), a.getBody().isFrameMode(),
				f instanceof DashboardFragment, PlaybackUiPolicy.shouldShowAudioPlayerBar(a));
	}

	static boolean isAutoTopBackVisible(RuntimeHostMode hostMode, boolean frameMode,
													 boolean dashboardFragment, boolean audioPlayerBarVisible) {
		return (hostMode != null) && hostMode.usesAutomotivePresentation() && frameMode &&
				!dashboardFragment && !audioPlayerBarVisible;
	}

	public static void refreshAutoTopBackButton(MainActivityDelegate a) {
		if (!a.getRuntimeHostMode().usesAutomotivePresentation()) return;
		View back = a.getToolBar().findViewById(me.aap.utils.R.id.tool_bar_back_button);
		if (back == null) return;
		back.setVisibility(getAutoTopBackVisibility(a, a.getActiveFragment()));
	}
}
