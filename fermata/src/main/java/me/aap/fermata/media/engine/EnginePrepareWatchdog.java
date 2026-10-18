package me.aap.fermata.media.engine;

/** Rejects an engine prepare request that never produces a ready or error callback. */
public final class EnginePrepareWatchdog {
	public static final long DEFAULT_MAX_TIMEOUT_MS = 60_000L;
	private final Scheduler scheduler;
	private final Runnable timeout;
	private Request active;

	public EnginePrepareWatchdog(Scheduler scheduler, Runnable timeout) {
		this.scheduler = scheduler;
		this.timeout = timeout;
	}

	public void arm(long delayMillis) {
		cancel();
		long boundedDelay = Math.max(0L, Math.min(delayMillis, DEFAULT_MAX_TIMEOUT_MS));
		Request request = active = new Request();
		scheduler.postDelayed(request, boundedDelay);
	}

	public void cancel() {
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
	public interface Scheduler {
		void postDelayed(Runnable task, long delayMillis);
	}
}
