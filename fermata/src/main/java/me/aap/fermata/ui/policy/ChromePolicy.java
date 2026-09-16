package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivityDelegate;
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
		return TopBarPolicy.isTopBackVisible(a.getRuntimeHostMode(), (f != null) &&
				PhoneRootPolicy.isPrimaryRoot(a.getRuntimeHostMode(), a.getPhoneRootId(),
						f.getFragmentId()));
	}

	/**
	 * Legacy primitive signature retained while callers/tests migrate. Playback and layout inputs no
	 * longer affect route Back semantics.
	 */
	static boolean isTopBackVisible(RuntimeHostMode hostMode, boolean frameMode,
			boolean videoMode, boolean primaryRoot,
			boolean audioPlayerBarVisible, boolean playerBackOwned) {
		return TopBarPolicy.isTopBackVisible(hostMode, primaryRoot);
	}

	public static void refreshTopBackButton(MainActivityDelegate a) {
		TopBarController.refresh(a);
	}
}
