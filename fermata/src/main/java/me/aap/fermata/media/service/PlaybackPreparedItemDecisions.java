package me.aap.fermata.media.service;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;

/** Pure decisions and calculations used by prepared-item orchestration. */
final class PlaybackPreparedItemDecisions {
	private PlaybackPreparedItemDecisions() {
	}

	enum QueueAction { SEEK_SAME_ITEM, KEEP_QUEUE, REFRESH_QUEUE }

	enum OutgoingSource { DIRECT_CURRENT, PREVIOUS_SNAPSHOT }

	record OutgoingPublication(@Nullable MediaMetadataCompat metadata,
			@NonNull PlaybackStateCompat state) {
	}

	record TransitionPublication(@Nullable PlayableItem item, long position,
			@Nullable MediaMetadataCompat metadata, @NonNull PlaybackStateCompat state,
			boolean transportCommand) {
	}

	static QueueAction queueAction(boolean currentPresent, boolean sameItem,
			boolean sameParent) {
		if (!currentPresent) return QueueAction.REFRESH_QUEUE;
		if (sameItem) return QueueAction.SEEK_SAME_ITEM;
		return sameParent ? QueueAction.KEEP_QUEUE : QueueAction.REFRESH_QUEUE;
	}

	static OutgoingSource outgoingSource(boolean currentPresent, boolean directCurrent) {
		return currentPresent && directCurrent ?
				OutgoingSource.DIRECT_CURRENT : OutgoingSource.PREVIOUS_SNAPSHOT;
	}

	static OutgoingPublication outgoingPublication(@NonNull PlayableItem item, long position,
			@Nullable PlaybackSnapshot rollback, @Nullable PlaybackSnapshot currentSnapshot,
			@NonNull PlaybackStateCompat currentState) {
		PlaybackSnapshot snapshot = (rollback != null) ? rollback : currentSnapshot;
		MediaMetadataCompat selectedMetadata = null;
		if (snapshot != null) {
			PlayableItem snapshotItem = snapshot.getItem();
			if ((snapshotItem != null) &&
					(PlayableItemResolver.unwrap(snapshotItem) == item)) {
				selectedMetadata = snapshot.getMetadata();
			}
		}
		PlaybackStateCompat previous = (rollback == null) ? currentState : rollback.getState();
		PlaybackStateCompat state = new PlaybackStateCompat.Builder(previous)
				.setState(previous.getState(), Math.max(position, 0), previous.getPlaybackSpeed())
				.build();
		return new OutgoingPublication(selectedMetadata, state);
	}

	static TransitionPublication transitionPublication(@NonNull PlayableItem target,
			@Nullable PlayableItem current, int transitionState, long targetPosition,
			@NonNull PlaybackStateCompat previousState,
			@Nullable MediaMetadataCompat currentMetadata) {
		boolean transport = target.isPlaybackTransportCommand();
		PlayableItem item = transport ? current : target;
		long position = transport ? previousState.getPosition() : Math.max(targetPosition, 0);
		MediaMetadataCompat metadata = !transport && (target != current) ? null : currentMetadata;
		PlaybackStateCompat state = PlaybackTransition.createPublishedTransitionState(previousState,
				transitionState, position, transport);
		return new TransitionPublication(item, position, metadata, state, transport);
	}

	static boolean shouldPublishQueue(boolean requestCurrent, @Nullable PlayableItem source,
			@NonNull PlayableItem queueItem) {
		return requestCurrent && (source != null) &&
				(PlayableItemResolver.unwrap(source) == queueItem);
	}

	static MediaMetadataCompat buildPreparationMetadata(@NonNull MediaMetadataCompat loaded,
			@NonNull String targetName, @Nullable String progress) {
		MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder(loaded)
				.putString(METADATA_KEY_DISPLAY_TITLE, targetName);
		if (!TextUtils.isEmpty(progress)) {
			builder.putString(METADATA_KEY_DISPLAY_SUBTITLE, progress);
		}
		return builder.build();
	}
}
