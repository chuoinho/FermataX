package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.ui.policy.BackNavigationPolicy.BackTarget;

/** Pure semantic reducer for the common Fermata top bar. */
public final class TopBarPolicy {
	private TopBarPolicy() {
	}

	@NonNull
	public static State resolve(@Nullable RuntimeHostMode hostMode, boolean dashboardFragment,
			int activeFragmentId, int playbackOwnerFragmentId,
			@NonNull CharSequence fragmentTitle, @NonNull CharSequence playbackTitle,
			@NonNull CharSequence preparationStatus) {
		BackTarget backTarget = BackNavigationPolicy.resolveTopBarBackTarget(
				hostMode != null, dashboardFragment);
		int backVisibility = backTarget == BackTarget.NONE ? GONE : VISIBLE;
		CharSequence title = ToolBarTitlePolicy.resolve(activeFragmentId,
				playbackOwnerFragmentId, fragmentTitle, playbackTitle, preparationStatus);
		return new State(backVisibility, title, backTarget);
	}

	public static boolean isTopBackVisible(@Nullable RuntimeHostMode hostMode,
			boolean dashboardFragment) {
		return BackNavigationPolicy.resolveTopBarBackTarget(hostMode != null,
				dashboardFragment) != BackTarget.NONE;
	}

	public record State(int backVisibility, @NonNull CharSequence title,
			@NonNull BackTarget backTarget) {
	}
}
