package me.aap.fermata.addon.stremio.lifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import me.aap.utils.app.App;

/** Supplies UI deadlines even when a controller is created before Application startup. */
public final class StremioDeadlineScheduler {
	private StremioDeadlineScheduler() {
	}

	public static ScheduledExecutorService get() {
		App app = App.get();
		return (app != null) ? app.getScheduler() : FallbackHolder.INSTANCE;
	}

	private static final class FallbackHolder {
		private static final ScheduledExecutorService INSTANCE =
				Executors.newSingleThreadScheduledExecutor(task -> {
					Thread thread = new Thread(task, "StremioDeadline");
					thread.setDaemon(true);
					return thread;
				});
	}
}
