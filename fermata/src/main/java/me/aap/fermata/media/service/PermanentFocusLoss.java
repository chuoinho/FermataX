package me.aap.fermata.media.service;

import java.util.function.BooleanSupplier;

/** Delays terminal player release after a permanent audio-focus loss. */
final class PermanentFocusLoss {
	private static final long STOP_DELAY_MS = 30_000L;
	private final Scheduler scheduler;
	private final Runnable delayedStop;

	PermanentFocusLoss(Scheduler scheduler, BooleanSupplier terminal, Runnable stop) {
		this.scheduler = scheduler;
		delayedStop = () -> {
			if (!terminal.getAsBoolean()) stop.run();
		};
	}

	void schedule() {
		cancel();
		scheduler.postDelayed(delayedStop, STOP_DELAY_MS);
	}

	void cancel() {
		scheduler.removeCallbacks(delayedStop);
	}

	interface Scheduler {
		void postDelayed(Runnable task, long delayMillis);

		void removeCallbacks(Runnable task);
	}
}
