package me.aap.fermata.media.service;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

/** Advances duration-sliced playback when the underlying engine has no natural end event. */
final class PlaybackAdvanceWatchdog {
	private final Scheduler scheduler;
	private final DurationResolver durationResolver;
	private final Runnable advance;
	private Request active;

	PlaybackAdvanceWatchdog(Scheduler scheduler, Runnable advance) {
		this(scheduler, item -> item.getDuration().main(), advance);
	}

	PlaybackAdvanceWatchdog(Scheduler scheduler, DurationResolver durationResolver,
			Runnable advance) {
		this.scheduler = scheduler;
		this.durationResolver = durationResolver;
		this.advance = advance;
	}

	void arm(PlayableItem item, long position, float speed) {
		Request request = active = new Request(position, speed);
		durationResolver.resolve(item).onSuccess(request::durationResolved);
	}

	void cancel() {
		active = null;
	}

	private final class Request implements Runnable {
		private final long position;
		private final float speed;

		private Request(long position, float speed) {
			this.position = position;
			this.speed = speed;
		}

		private void durationResolved(long duration) {
			if (active != this) return;
			long delay = (long) ((duration - position) / speed);
			scheduler.postDelayed(this, delay);
		}

		@Override
		public void run() {
			if (active != this) return;
			active = null;
			advance.run();
		}
	}

	@FunctionalInterface
	interface Scheduler {
		void postDelayed(Runnable task, long delayMillis);
	}

	@FunctionalInterface
	interface DurationResolver {
		FutureSupplier<Long> resolve(PlayableItem item);
	}
}
