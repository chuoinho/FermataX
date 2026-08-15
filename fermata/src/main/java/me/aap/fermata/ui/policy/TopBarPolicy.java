package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pure semantic reducer for the common Fermata top bar. */
public final class TopBarPolicy {
	private TopBarPolicy() {
	}

	@NonNull
	public static State resolve(@Nullable RuntimeHostMode hostMode,
			boolean frameMode, boolean videoMode, boolean dashboardFragment,
			boolean audioPlayerBarVisible, boolean playerBackOwned,
			int activeFragmentId, int playbackOwnerFragmentId,
			@NonNull CharSequence fragmentTitle, @NonNull CharSequence playbackTitle,
			@NonNull CharSequence preparationStatus) {
		int backVisibility = isTopBackVisible(hostMode, frameMode, videoMode,
				dashboardFragment, audioPlayerBarVisible, playerBackOwned) ? VISIBLE : GONE;
		CharSequence title = ToolBarTitlePolicy.resolve(activeFragmentId,
				playbackOwnerFragmentId, fragmentTitle, playbackTitle, preparationStatus);
		return new State(backVisibility, title);
	}

	public static boolean isTopBackVisible(@Nullable RuntimeHostMode hostMode,
			boolean frameMode, boolean videoMode, boolean dashboardFragment,
			boolean audioPlayerBarVisible, boolean playerBackOwned) {
		if ((hostMode == null) || dashboardFragment) return false;
		if (!hostMode.usesAutomotivePresentation()) return true;
		return (frameMode || videoMode) && !playerBackOwned && !audioPlayerBarVisible;
	}

	public record State(int backVisibility, @NonNull CharSequence title) {
	}
}
