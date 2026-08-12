package me.aap.fermata.engine.exoplayer;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.media.engine.EnginePrepareWatchdog;

public class ExoPrepareWatchdogContractTest {
	@Test
	public void timeoutIsBoundedAndDeliveredOnce() {
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger errors = new AtomicInteger();
		EnginePrepareWatchdog watchdog = new EnginePrepareWatchdog(scheduler::schedule,
				errors::incrementAndGet);

		watchdog.arm(ExoPlayerEngine.EXO_PREPARE_TIMEOUT_MS);
		scheduler.tasks.get(0).run();
		scheduler.tasks.get(0).run();

		assertEquals(30_000L, scheduler.delay);
		assertEquals(1, errors.get());
	}

	@Test
	public void readyErrorStopAndReplacementInvalidateOldDeadline() {
		for (int terminal = 0; terminal < 3; terminal++) {
			FakeScheduler scheduler = new FakeScheduler();
			AtomicInteger errors = new AtomicInteger();
			EnginePrepareWatchdog watchdog = new EnginePrepareWatchdog(scheduler::schedule,
					errors::incrementAndGet);
			watchdog.arm(30_000L);
			watchdog.cancel();
			scheduler.tasks.get(0).run();
			assertEquals(0, errors.get());
		}

		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger errors = new AtomicInteger();
		EnginePrepareWatchdog watchdog = new EnginePrepareWatchdog(scheduler::schedule,
				errors::incrementAndGet);
		watchdog.arm(30_000L);
		watchdog.arm(30_000L);
		scheduler.tasks.get(0).run();
		scheduler.tasks.get(1).run();
		assertEquals(1, errors.get());
	}

	private static final class FakeScheduler {
		private final List<Runnable> tasks = new ArrayList<>();
		private long delay;

		private void schedule(Runnable task, long delay) {
			tasks.add(task);
			this.delay = delay;
		}
	}
}
