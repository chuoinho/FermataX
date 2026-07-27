package me.aap.fermata.ui.policy;

import androidx.annotation.NonNull;

public final class ToolBarTitlePolicy {
	private ToolBarTitlePolicy() {
	}

	@NonNull
	public static CharSequence resolve(int activeFragmentId, int playbackOwnerFragmentId,
			@NonNull CharSequence fragmentTitle, @NonNull CharSequence playbackTitle) {
		return (activeFragmentId == playbackOwnerFragmentId) ? playbackTitle : fragmentTitle;
	}

	@NonNull
	public static CharSequence resolve(int activeFragmentId, int playbackOwnerFragmentId,
			@NonNull CharSequence fragmentTitle, @NonNull CharSequence playbackTitle,
			@NonNull CharSequence preparationStatus) {
		CharSequence title = resolve(activeFragmentId, playbackOwnerFragmentId,
				fragmentTitle, playbackTitle);
		if ((activeFragmentId != playbackOwnerFragmentId) ||
				(preparationStatus.length() == 0)) return title;
		return title + " | " + preparationStatus;
	}
}
