package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.completedVoid;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.fermata.media.lib.PlaybackProgressItem.ProgressMode;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.LongSupplier;

/**
 * Owns opt-in finite-media progress writes for one media-session playback generation.
 */
final class PlaybackProgressPolicy {
	static final long CHECKPOINT_INTERVAL_MS = 15_000L;
	private static final long COMPLETION_REMAINING_MS = 60_000L;
	private static final double COMPLETION_RATIO = 0.95D;

	private final LongSupplier clock;
	private final Map<String, Long> latestGenerations = new HashMap<>();
	private PlayableItem owner;
	private long generation = -1L;
	private long lastWriteTime;
	private long policyEpoch;
	private long writeToken;
	private boolean playing;
	private FutureSupplier<Void> inFlight = completedVoid();

	PlaybackProgressPolicy() {
		this(SystemClock::elapsedRealtime);
	}

	PlaybackProgressPolicy(@NonNull LongSupplier clock) {
		this.clock = clock;
	}

	synchronized boolean bind(PlayableItem item, long generation, boolean playing) {
		item = unwrap(item);
		if (!isManaged(item)) {
			clearOwner();
			return false;
		}

		if ((owner != item) || (this.generation != generation)) {
			owner = item;
			this.generation = generation;
			lastWriteTime = clock.getAsLong();
		}
		rememberGeneration(item, generation);
		this.playing = playing;
		return true;
	}

	synchronized boolean owns(PlayableItem item, long generation, boolean requirePlaying) {
		item = unwrap(item);
		return (owner == item) && (this.generation == generation) &&
				(!requirePlaying || playing);
	}

	synchronized long getCheckpointDelay(PlayableItem item, long generation) {
		if (!owns(item, generation, true)) return -1L;
		if (!inFlight.isDone()) return 1_000L;
		long elapsed = Math.max(0L, clock.getAsLong() - lastWriteTime);
		return Math.max(0L, CHECKPOINT_INTERVAL_MS - elapsed);
	}

	FutureSupplier<Void> checkpoint(PlayableItem item, long generation, long position,
			long duration) {
		return persist(item, generation, position, duration, false, false);
	}

	FutureSupplier<Void> lifecycle(PlayableItem item, long generation, long position,
			long duration, boolean ownsCurrent, boolean committedOutgoing) {
		if (!ownsCurrent && !committedOutgoing) return completedVoid();
		return persist(item, generation, position, duration, true, committedOutgoing);
	}

	private FutureSupplier<Void> persist(PlayableItem item, long generation, long position,
			long duration, boolean force, boolean committedOutgoing) {
		item = unwrap(item);
		if (!(item instanceof PlaybackProgressItem progress) || !isManaged(progress)) {
			return completedVoid();
		}

		FutureSupplier<Void> pending;
		long acceptedEpoch;
		synchronized (this) {
			boolean sameOwner = owner == item;
			if (force && sameOwner && !committedOutgoing) {
				this.generation = generation;
				rememberGeneration(item, generation);
			}
			boolean accepted = committedOutgoing ? !isSuperseded(item, generation) :
					owns(item, generation, !force);
			if (!accepted) return completedVoid();
			rememberGeneration(item, generation);
			acceptedEpoch = policyEpoch;

			long now = clock.getAsLong();
			if (!force && ((now - lastWriteTime) < CHECKPOINT_INTERVAL_MS)) {
				return completedVoid();
			}

			pending = inFlight;
			if (!pending.isDone()) {
				if (!force) return completedVoid();
				return queueLifecycleWrite(pending, item, generation, position, duration,
						committedOutgoing, acceptedEpoch);
			}
			lastWriteTime = now;
		}

		return execute(progress, generation, position, duration, acceptedEpoch);
	}

	private synchronized FutureSupplier<Void> queueLifecycleWrite(FutureSupplier<Void> pending,
			PlayableItem item, long generation, long position, long duration,
			boolean committedOutgoing, long acceptedEpoch) {
		Promise<Void> queued = new Promise<>();
		long token = ++writeToken;
		inFlight = queued;
		pending.onCompletion((result, failure) -> {
			FutureSupplier<Void> retry;
			synchronized (PlaybackProgressPolicy.this) {
				boolean accepted = (acceptedEpoch == policyEpoch) &&
						(committedOutgoing ? !isSuperseded(item, generation) :
								owns(item, generation, false));
				if (!accepted) {
					if ((acceptedEpoch == policyEpoch) && (token == writeToken))
						inFlight = completedVoid();
					queued.complete(null);
					return;
				}
				lastWriteTime = clock.getAsLong();
			}

			PlaybackProgressItem progress = (PlaybackProgressItem) unwrap(item);
			retry = execute(progress, generation, position, duration, acceptedEpoch);
			retry.onCompletion((ignored, retryFailure) -> {
				if (retryFailure == null) queued.complete(null);
				else queued.completeExceptionally(retryFailure);
			});
		});
		return queued;
	}

	private FutureSupplier<Void> execute(PlaybackProgressItem progress, long generation, long position,
			long duration, long acceptedEpoch) {
		ProgressValue value = normalize(progress, position, duration);
		FutureSupplier<Void> write;
		long token;
		synchronized (this) {
			if (acceptedEpoch != policyEpoch) return completedVoid();
			token = ++writeToken;
			try {
				write = progress.savePlaybackProgress(
						value.position(), value.completed(), generation);
			} catch (Throwable failure) {
				Promise<Void> failed = new Promise<>();
				failed.completeExceptionally(failure);
				write = failed;
			}
			inFlight = write;
		}

		write.onCompletion((result, failure) -> {
			synchronized (PlaybackProgressPolicy.this) {
				if ((acceptedEpoch == policyEpoch) && (token == writeToken))
					inFlight = completedVoid();
			}
		});
		return write;
	}

	synchronized void clear() {
		policyEpoch++;
		clearOwner();
		latestGenerations.clear();
	}

	private void rememberGeneration(PlayableItem item, long generation) {
		String key = item.getId();
		Long latest = latestGenerations.get(key);
		if ((latest == null) || (generation > latest)) latestGenerations.put(key, generation);
	}

	private boolean isSuperseded(PlayableItem item, long generation) {
		Long latest = latestGenerations.get(item.getId());
		return (latest != null) && (latest > generation);
	}

	private void clearOwner() {
		owner = null;
		generation = -1L;
		playing = false;
		lastWriteTime = 0L;
	}

	static boolean isManaged(PlayableItem item) {
		item = unwrap(item);
		return (item instanceof PlaybackProgressItem progress) && isManaged(progress);
	}

	private static boolean isManaged(PlaybackProgressItem progress) {
		return progress.getPlaybackProgressMode() == ProgressMode.MANAGED;
	}

	static ProgressValue normalize(PlaybackProgressItem progress, long position, long duration) {
		long normalizedPosition = Math.max(position, 0L);
		boolean completed;
		if (isManaged(progress)) {
			completed = (duration > 0L) &&
					(normalizedPosition >= completionThreshold(duration));
		} else {
			completed = (duration > 0L) && ((duration - normalizedPosition) <= 1_000L);
		}
		return new ProgressValue(completed ? 0L : normalizedPosition, completed);
	}

	static boolean isCompleted(PlayableItem item, long position, long duration) {
		item = unwrap(item);
		if (!(item instanceof PlaybackProgressItem progress)) {
			return (duration > 0L) && ((duration - position) <= 1_000L);
		}
		return normalize(progress, position, duration).completed();
	}

	static long completionThreshold(long duration) {
		if (duration <= 0L) return Long.MAX_VALUE;
		long remainingThreshold = Math.max(0L, duration - COMPLETION_REMAINING_MS);
		long ratioThreshold = (long) Math.ceil(duration * COMPLETION_RATIO);
		return Math.max(remainingThreshold, ratioThreshold);
	}

	private static PlayableItem unwrap(PlayableItem item) {
		return (item == null) ? null : PlayableItemResolver.unwrap(item);
	}

	record ProgressValue(long position, boolean completed) {}
}
