package me.aap.fermata.media.service;

import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;

final class PlaybackTransition {
	@Nullable
	private PlayableItem pendingItem;
	@Nullable
	private PlaybackSnapshot previousSnapshot;
	private long targetPosition = -1;

	static PlaybackStateCompat createPublishedTransitionState(PlaybackStateCompat previousState,
			int transitionState, long position, boolean transportCommand) {
		return MediaSessionCallback.createPlaybackTransitionState(previousState,
				transportCommand ? previousState.getState() : transitionState, position);
	}

	void begin(@NonNull PlayableItem item, @Nullable PlaybackSnapshot previousSnapshot) {
		begin(item, previousSnapshot, -1);
	}

	void begin(@NonNull PlayableItem item, @Nullable PlaybackSnapshot previousSnapshot,
			long targetPosition) {
		item = PlayableItemResolver.unwrap(item);
		if (pendingItem == null) this.previousSnapshot = previousSnapshot;
		pendingItem = item;
		this.targetPosition = targetPosition;
	}

	void complete(@Nullable MediaEngine engine, @NonNull PlayableItem item) {
		item = PlayableItemResolver.unwrap(item);
		PlayableItem source = (engine == null) ? null : engine.getSource();
		if ((pendingItem == item) && (source != null) &&
				(PlayableItemResolver.unwrap(source) == item)) {
			clear();
		}
	}

	boolean cancelIfPending(@NonNull PlayableItem item) {
		item = PlayableItemResolver.unwrap(item);
		if (pendingItem != item) return false;
		clear();
		return true;
	}

	boolean isPending(@NonNull PlayableItem item) {
		return pendingItem == PlayableItemResolver.unwrap(item);
	}

	boolean hasPending() {
		return pendingItem != null;
	}

	boolean isPreviousItem(@NonNull PlayableItem item) {
		PlaybackSnapshot previous = previousSnapshot;
		if (previous == null) return false;
		PlayableItem previousItem = previous.getItem();
		return (previousItem != null) &&
				(PlayableItemResolver.unwrap(previousItem) == PlayableItemResolver.unwrap(item));
	}

	long getTargetPosition(@NonNull PlayableItem item, long fallback) {
		return isPending(item) && (targetPosition >= 0) ? targetPosition : fallback;
	}

	@Nullable
	PlaybackSnapshot getPreviousSnapshot(@NonNull PlayableItem item) {
		item = PlayableItemResolver.unwrap(item);
		return (pendingItem == item) ? previousSnapshot : null;
	}

	@Nullable
	PlaybackSnapshot cancel() {
		if (pendingItem == null) return null;
		PlaybackSnapshot previous = previousSnapshot;
		clear();
		return previous;
	}

	void clear() {
		pendingItem = null;
		previousSnapshot = null;
		targetPosition = -1;
	}

	@Nullable
	PlayableItem getCurrentItem(@Nullable MediaEngine engine) {
		PlayableItem pending = pendingItem;
		if (pending != null) return pending;
		PlayableItem source = (engine == null) ? null : engine.getSource();
		return (source == null) ? null : PlayableItemResolver.unwrap(source);
	}
}
