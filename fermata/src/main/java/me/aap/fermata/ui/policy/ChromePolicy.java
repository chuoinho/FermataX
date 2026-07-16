package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.View;

import androidx.annotation.Nullable;

import me.aap.fermata.BuildConfig;
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
		return isAutoTopBackVisible(BuildConfig.AUTO, a.getBody().isFrameMode(),
				f instanceof DashboardFragment);
	}

	static boolean isAutoTopBackVisible(boolean auto, boolean frameMode,
														 boolean dashboardFragment) {
		return auto && frameMode && !dashboardFragment;
	}

	public static void refreshAutoTopBackButton(MainActivityDelegate a) {
		if (!BuildConfig.AUTO) return;
		View back = a.getToolBar().findViewById(me.aap.utils.R.id.tool_bar_back_button);
		if (back == null) return;
		back.setVisibility(getAutoTopBackVisibility(a, a.getActiveFragment()));
	}
}
