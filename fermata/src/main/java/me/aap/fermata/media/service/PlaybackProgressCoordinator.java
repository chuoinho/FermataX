package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.completedVoid;

import android.os.Handler;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.fermata.media.lib.PersistentMediaItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.media.service.ProgressOwnership.LastPlayedLease;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

/** Coordinates progress persistence while delegating live playback ownership to its owner. */
final class PlaybackProgressCoordinator {
	private final ProgressOwnership ownership;
	private final PlaybackProgressPolicy progressPolicy;
	private final Scheduler scheduler;
	private final PositionResolver positionResolver;
	private final AudioPositionOwner audioPositionOwner;
	private final MediaLib lib;
	private final boolean marshalToMain;
	private Runnable progressCheckpoint;

	PlaybackProgressCoordinator(ProgressOwnership ownership, PlaybackProgressPolicy progressPolicy,
			Handler handler, PositionResolver positionResolver, AudioPositionOwner audioPositionOwner,
			MediaLib lib) {
		this(ownership, progressPolicy, handler::postDelayed, positionResolver,
				audioPositionOwner, lib, true);
	}

	PlaybackProgressCoordinator(ProgressOwnership ownership, PlaybackProgressPolicy progressPolicy,
			Scheduler scheduler, PositionResolver positionResolver,
			AudioPositionOwner audioPositionOwner, MediaLib lib) {
		this(ownership, progressPolicy, scheduler, positionResolver, audioPositionOwner, lib, false);
	}

	private PlaybackProgressCoordinator(ProgressOwnership ownership,
			PlaybackProgressPolicy progressPolicy, Scheduler scheduler,
			PositionResolver positionResolver, AudioPositionOwner audioPositionOwner, MediaLib lib,
			boolean marshalToMain) {
		this.ownership = ownership;
		this.progressPolicy = progressPolicy;
		this.scheduler = scheduler;
		this.positionResolver = positionResolver;
		this.audioPositionOwner = audioPositionOwner;
		this.lib = lib;
		this.marshalToMain = marshalToMain;
	}

	void cancelCheckpoint() {
		progressCheckpoint = null;
	}

	void applyNegativeProgress(PlayableItem item, LastPlayedLease lease) {
		if (!ownership.isStillLastPlayedOwner(lease)) return;
		String id = PersistentMediaItem.idOf(item);
		lib.getPrefs().setLastPlayedPosPref(0);
		lib.getPrefs().setLastPlayedItemPref(id);
		item.getParent().getPrefs().setLastPlayedPosPref(0);
		item.getParent().getPrefs().setLastPlayedItemPref(id);
	}

	void applyResolvedProgress(PlayableItem item, long position, long duration,
			LastPlayedLease lease, boolean committedOutgoing, long generation) {
		boolean ownsCurrent = ownership.isStillLastPlayedOwner(lease);
		persistResolvedPlaybackProgress(item, position, duration, ownsCurrent, committedOutgoing,
				generation).onFailure(error -> Log.e(error,
				"Failed to save playback progress for ", item.getId()));
		if (!ownsCurrent) return;
		String id;
		MediaLibPrefs libPrefs = lib.getPrefs();
		BrowsableItemPrefs prefs;
		boolean completed = PlaybackProgressPolicy.isCompleted(item, position, duration);

		if (item.isStream() || (duration <= 0)) {
			id = PersistentMediaItem.idOf(item);
			prefs = item.getParent().getPrefs();
			libPrefs.setLastPlayedItemPref(id);
			libPrefs.setLastPlayedPosPref(0);
			prefs.setLastPlayedItemPref(id);
			prefs.setLastPlayedPosPref(0);
			return;
		}

		if (completed) {
			item.getNextPlayable().onCompletion((next, fail) -> {
				if (!ownership.isStillLastPlayedOwner(lease)) return;
				if (next == null) next = item;

				String nextId = PersistentMediaItem.idOf(next);
				BrowsableItemPrefs nextPrefs = next.getParent().getPrefs();
				libPrefs.setLastPlayedItemPref(nextId);
				libPrefs.setLastPlayedPosPref(0);
				nextPrefs.setLastPlayedItemPref(nextId);
				nextPrefs.setLastPlayedPosPref(0);
				item.getPrefs().setPositionPref(0);
			});
			return;
		} else {
			id = PersistentMediaItem.idOf(item);
			prefs = item.getParent().getPrefs();
		}

		if (item.isVideo()) {
			PlayableItemPrefs itemPrefs = item.getPrefs();
			float threshold = itemPrefs.getWatchedThresholdPref() / 100F;
			if (threshold > 0) {
				if (position > (duration * threshold)) itemPrefs.setWatchedPref(true);
				else itemPrefs.setPositionPref(position);
			}
		} else if (position == 0) {
			item.getPrefs().setPositionPref(0);
		} else if (audioPositionOwner.owns(item)) {
			item.getPrefs().setPositionPref(position);
		}

		libPrefs.setLastPlayedItemPref(id);
		libPrefs.setLastPlayedPosPref(position);
		prefs.setLastPlayedItemPref(id);
		prefs.setLastPlayedPosPref(position);
	}

	static FutureSupplier<Void> persistPlaybackProgress(PlayableItem item, long position,
			long duration) {
		item = PlayableItemResolver.unwrap(item);
		if (!(item instanceof PlaybackProgressItem progress)) return completedVoid();
		PlaybackProgressPolicy.ProgressValue value =
				PlaybackProgressPolicy.normalize(progress, position, duration);
		return progress.savePlaybackProgress(value.position(), value.completed());
	}

	static FutureSupplier<Void> persistResolvedPlaybackProgress(PlayableItem item, long position,
			long duration, boolean ownsLastPlayed, boolean committedOutgoing) {
		return (ownsLastPlayed || committedOutgoing) ?
				persistPlaybackProgress(item, position, duration) : completedVoid();
	}

	FutureSupplier<Void> persistResolvedPlaybackProgress(PlayableItem item, long position,
			long duration, boolean ownsLastPlayed, boolean committedOutgoing, long generation) {
		if (!PlaybackProgressPolicy.isManaged(item)) {
			return persistResolvedPlaybackProgress(item, position, duration, ownsLastPlayed,
					committedOutgoing);
		}
		return progressPolicy.lifecycle(item, generation, position, duration, ownsLastPlayed,
				committedOutgoing);
	}

	void scheduleProgressCheckpoint(PlayableItem item, long generation, long minDelay) {
		long delay = progressPolicy.getCheckpointDelay(item, generation);
		if (delay < 0L) return;
		delay = Math.max(delay, minDelay);
		Runnable checkpoint = new Runnable() {
			@Override
			public void run() {
				if ((progressCheckpoint != this) ||
						!progressPolicy.owns(item, generation, true) ||
						!ownership.isCurrentEngineSource(item)) return;
				FutureSupplier<Long> position = positionResolver.resolve(item);
				if (position == null) return;

				var read = position.and(item.getDuration());
				if (marshalToMain) read = read.main();
				read.onCompletion((value, failure) -> {
					if (progressCheckpoint != this) return;
					progressCheckpoint = null;
					if ((failure != null) || !progressPolicy.owns(item, generation, true)) {
						if (progressPolicy.owns(item, generation, true)) {
							scheduleProgressCheckpoint(item, generation, 1_000L);
						}
						return;
					}

					progressPolicy.checkpoint(item, generation, value.value1, value.value2)
							.onCompletion((ignored, writeFailure) -> {
						if (writeFailure != null) {
							Log.e(writeFailure, "Failed to checkpoint playback progress for ",
									item.getId());
						}
						if (progressPolicy.owns(item, generation, true)) {
							scheduleProgressCheckpoint(item, generation, 0L);
						}
					});
				});
			}
		};
		progressCheckpoint = checkpoint;
		scheduler.postDelayed(checkpoint, delay);
	}

	@FunctionalInterface
	interface Scheduler {
		void postDelayed(Runnable task, long delayMillis);
	}

	@FunctionalInterface
	interface PositionResolver {
		FutureSupplier<Long> resolve(PlayableItem item);
	}

	@FunctionalInterface
	interface AudioPositionOwner {
		boolean owns(PlayableItem item);
	}
}
