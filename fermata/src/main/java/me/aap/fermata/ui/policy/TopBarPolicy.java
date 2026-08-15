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

	/**
	 * Back is route chrome, not playback chrome. Whenever a non-Dashboard top bar is rendered it has
	 * the same Back semantics on PHONE, AA projection and mirror/DHU hosts. Player-bar ownership and
	 * audio/video presentation affect whether chrome is on-screen, never the Back meaning inside it.
	 */
	public static boolean isTopBackVisible(@Nullable RuntimeHostMode hostMode,
			boolean frameMode, boolean videoMode, boolean dashboardFragment,
			boolean audioPlayerBarVisible, boolean playerBackOwned) {
		return (hostMode != null) && !dashboardFragment;
	}

	public record State(int backVisibility, @NonNull CharSequence title) {
	}
}
