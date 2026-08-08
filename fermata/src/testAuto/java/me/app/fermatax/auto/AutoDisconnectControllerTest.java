package me.app.fermatax.auto;

import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_NOT_CONNECTED;
import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_PROJECTION;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class AutoDisconnectControllerTest {
	@Test
	public void initialDisconnectedValueNeverSchedulesShutdown() {
		FakeScheduler scheduler = new FakeScheduler();
		int[] shutdowns = {0};
		AutoDisconnectController controller = new AutoDisconnectController(1500L, scheduler,
				() -> shutdowns[0]++);
		controller.onConnectionType(CONNECTION_TYPE_NOT_CONNECTED, false);
		scheduler.runAll();
		assertEquals(0, shutdowns[0]);
	}

	@Test
	public void reconnectCancelsPendingShutdown() {
		FakeScheduler scheduler = new FakeScheduler();
		int[] shutdowns = {0};
		AutoDisconnectController controller = new AutoDisconnectController(1500L, scheduler,
				() -> shutdowns[0]++);
		controller.onConnectionType(CONNECTION_TYPE_PROJECTION, true);
		controller.onConnectionType(CONNECTION_TYPE_NOT_CONNECTED, false);
		controller.onConnectionType(CONNECTION_TYPE_PROJECTION, true);
		scheduler.runAll();
		assertEquals(0, shutdowns[0]);
	}

	@Test
	public void stableDisconnectRunsExactlyOnce() {
		FakeScheduler scheduler = new FakeScheduler();
		int[] shutdowns = {0};
		AutoDisconnectController controller = new AutoDisconnectController(1500L, scheduler,
				() -> shutdowns[0]++);
		controller.onConnectionType(CONNECTION_TYPE_PROJECTION, true);
		controller.onConnectionType(CONNECTION_TYPE_NOT_CONNECTED, false);
		scheduler.runAll();
		scheduler.runAll();
		assertEquals(1, shutdowns[0]);
	}

	private static final class FakeScheduler implements AutoDisconnectController.Scheduler {
		private final List<Runnable> tasks = new ArrayList<>();
		@Override public void schedule(Runnable task, long delayMillis) { tasks.add(task); }
		@Override public void cancel(Runnable task) { tasks.removeIf(current -> current == task); }
		void runAll() { List.copyOf(tasks).forEach(Runnable::run); tasks.clear(); }
	}
}
