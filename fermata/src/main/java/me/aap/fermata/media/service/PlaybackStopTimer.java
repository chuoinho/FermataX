package me.aap.fermata.media.service;

import java.util.function.LongSupplier;

final class PlaybackStopTimer {
	private final Scheduler scheduler;
	private final LongSupplier clock;
	private final Runnable stopPlayback;
	private Timer active;

	PlaybackStopTimer(Scheduler scheduler, LongSupplier clock, Runnable stopPlayback) {
		this.scheduler = scheduler;
		this.clock = clock;
		this.stopPlayback = stopPlayback;
	}

	int getRemainingSeconds() {
		Timer timer = active;
		return (timer == null) ? 0 :
				Math.max((int) (timer.deadline - clock.getAsLong()) / 1000, 0);
	}

	void setSeconds(int seconds) {
		if (seconds == 0) {
			active = null;
			return;
		}

		int delay = seconds * 1000;
		Timer timer = active = new Timer(delay + clock.getAsLong());
		scheduler.postDelayed(timer, delay);
	}

	private final class Timer implements Runnable {
		private final long deadline;

		private Timer(long deadline) {
			this.deadline = deadline;
		}

		@Override
		public void run() {
			if (active == this) stopPlayback.run();
		}
	}

	interface Scheduler {
		void postDelayed(Runnable task, long delayMillis);
	}
}
