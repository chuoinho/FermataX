package me.aap.fermata.media.service;

import android.text.TextUtils;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;

/** Wrapper-safe identity comparison shared by Dashboard transport and playback UI. */
final class DashboardPlaybackIdentity {
	private DashboardPlaybackIdentity() {
	}

	static boolean same(PlayableItem first, PlayableItem second) {
		first = PlayableItemResolver.unwrap(first);
		second = PlayableItemResolver.unwrap(second);
		return TextUtils.equals(first.getOrigId(), second.getOrigId()) ||
				TextUtils.equals(first.getId(), second.getId());
	}
}
