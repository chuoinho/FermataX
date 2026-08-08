package me.app.fermatax.auto;

import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_NOT_CONNECTED;

/** Applies debounce scheduling to {@link AutoDisconnectPolicy} without Android timing dependencies. */
final class AutoDisconnectController {
	private final AutoDisconnectPolicy policy;
	private final Scheduler scheduler;
	private final Runnable shutdown;
	private final Runnable timeout = this::timeout;
	private final long graceMillis;
	private int lastType = Integer.MIN_VALUE;

	AutoDisconnectController(long graceMillis, Scheduler scheduler, Runnable shutdown) {
		this(new AutoDisconnectPolicy(), graceMillis, scheduler, shutdown);
	}

	AutoDisconnectController(AutoDisconnectPolicy policy, long graceMillis, Scheduler scheduler,
			Runnable shutdown) {
		this.policy = policy;
		this.graceMillis = graceMillis;
		this.scheduler = scheduler;
		this.shutdown = shutdown;
	}

	void onConnectionType(int type, boolean projectionAccepted) {
		lastType = type;
		switch (policy.onConnectionType(type, projectionAccepted)) {
			case SCHEDULE_SHUTDOWN -> {
				scheduler.cancel(timeout);
				scheduler.schedule(timeout, graceMillis);
			}
			case CANCEL_SHUTDOWN -> scheduler.cancel(timeout);
			case NONE -> {
			}
		}
	}

	private void timeout() {
		if (policy.onDisconnectTimeout(lastType == CONNECTION_TYPE_NOT_CONNECTED)) shutdown.run();
	}

	interface Scheduler {
		void schedule(Runnable task, long delayMillis);

		void cancel(Runnable task);
	}
}
