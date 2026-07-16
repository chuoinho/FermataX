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
}
