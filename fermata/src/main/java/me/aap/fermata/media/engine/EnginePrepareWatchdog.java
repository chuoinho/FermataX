package me.aap.fermata.media.engine;

/** Rejects an engine prepare request that never produces a ready or error callback. */
public final class EnginePrepareWatchdog {
	private final Scheduler scheduler;
	private final Runnable timeout;
	private Request active;

	public EnginePrepareWatchdog(Scheduler scheduler, Runnable timeout) {
		this.scheduler = scheduler;
		this.timeout = timeout;
	}

	public void arm(long delayMillis) {
		Request request = active = new Request();
		scheduler.postDelayed(request, delayMillis);
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
