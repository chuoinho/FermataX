package me.aap.fermata.media.engine;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class EnginePrepareWatchdogTest {
	@Test
	public void activeRequestTimesOutOnce() {
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger timeouts = new AtomicInteger();
		EnginePrepareWatchdog watchdog =
				new EnginePrepareWatchdog(scheduler, timeouts::incrementAndGet);

		watchdog.arm(20_000L);
		scheduler.tasks.get(0).task.run();
		scheduler.tasks.get(0).task.run();

		assertEquals(1, timeouts.get());
		assertEquals(20_000L, scheduler.tasks.get(0).delayMillis);
	}

	@Test
	public void cancelRejectsScheduledTimeout() {
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger timeouts = new AtomicInteger();
		EnginePrepareWatchdog watchdog =
				new EnginePrepareWatchdog(scheduler, timeouts::incrementAndGet);

		watchdog.arm(20_000L);
		watchdog.cancel();
		scheduler.tasks.get(0).task.run();

		assertEquals(0, timeouts.get());
	}

	@Test
	public void rearmRejectsOlderRequest() {
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger timeouts = new AtomicInteger();
		EnginePrepareWatchdog watchdog =
				new EnginePrepareWatchdog(scheduler, timeouts::incrementAndGet);

		watchdog.arm(20_000L);
		watchdog.arm(20_000L);
		scheduler.tasks.get(0).task.run();
		scheduler.tasks.get(1).task.run();

		assertEquals(1, timeouts.get());
	}

	private static final class FakeScheduler implements EnginePrepareWatchdog.Scheduler {
		private final List<ScheduledTask> tasks = new ArrayList<>();

		@Override
		public void postDelayed(Runnable task, long delayMillis) {
			tasks.add(new ScheduledTask(task, delayMillis));
		}
	}

	private record ScheduledTask(Runnable task, long delayMillis) {
	}
}
