package me.aap.fermata.media.engine;

/** Rejects a MediaPlayer prepare request that never produces a platform callback. */
final class MediaPlayerPrepareWatchdog {
	private final Scheduler scheduler;
	private final Runnable timeout;
	private Request active;

	MediaPlayerPrepareWatchdog(Scheduler scheduler, Runnable timeout) {
		this.scheduler = scheduler;
		this.timeout = timeout;
	}

	void arm(long delayMillis) {
		Request request = active = new Request();
		scheduler.postDelayed(request, delayMillis);
	}

	void cancel() {
		active = null;
	}

	private final class Request implements Runnable {
		@Override
		public void run() {
			if (active != this) return;
			active = null;
			timeout.run();
		}
	}

	@FunctionalInterface
	interface Scheduler {
		void postDelayed(Runnable task, long delayMillis);
	}
}
