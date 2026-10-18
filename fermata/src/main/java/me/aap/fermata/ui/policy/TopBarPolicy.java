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
	public static State resolve(@Nullable RuntimeHostMode hostMode, boolean primaryRoot,
			int activeFragmentId, int playbackOwnerFragmentId,
			@NonNull CharSequence fragmentTitle, @NonNull CharSequence playbackTitle,
			@NonNull CharSequence preparationStatus) {
		BackTarget backTarget = BackNavigationPolicy.resolveTopBarBackTarget(
				hostMode != null, primaryRoot);
		int backVisibility = backTarget == BackTarget.NONE ? GONE : VISIBLE;
		CharSequence title = ToolBarTitlePolicy.resolve(activeFragmentId,
				playbackOwnerFragmentId, fragmentTitle, playbackTitle, preparationStatus);
		return new State(backVisibility, title, backTarget);
	}

	public static boolean isTopBackVisible(@Nullable RuntimeHostMode hostMode,
			boolean primaryRoot) {
		return BackNavigationPolicy.resolveTopBarBackTarget(hostMode != null,
				primaryRoot) != BackTarget.NONE;
	}

	/** Stremio owns its in-page chrome, so it must use the entire content height. */
	public static int resolveTopBarVisibility(int activeFragmentId) {
		return resolveTopBarVisibility(activeFragmentId, false);
	}

	/** In-page chrome routes (Stremio, Car Finder) own their headers and must use full content height. */
	public static int resolveTopBarVisibility(int activeFragmentId, boolean barsHidden) {
		return barsHidden || (activeFragmentId == me.aap.fermata.R.id.stremio_fragment)
				|| (activeFragmentId == me.aap.fermata.R.id.car_finder_fragment) ?
				GONE : VISIBLE;
	}

	public record State(int backVisibility, @NonNull CharSequence title,
			@NonNull BackTarget backTarget) {
	}
}
