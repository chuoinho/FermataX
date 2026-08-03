package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class PermanentFocusLossTest {
	@Test
	public void cancelPreventsDelayedStop() {
		FakeScheduler scheduler = new FakeScheduler();
		int[] stops = {0};
		PermanentFocusLoss loss = new PermanentFocusLoss(scheduler, () -> false,
				() -> stops[0]++);
		loss.schedule();
		loss.cancel();
		scheduler.runAll();
		assertEquals(0, stops[0]);
	}

	@Test
	public void terminalRuntimeIgnoresDelayedStop() {
		FakeScheduler scheduler = new FakeScheduler();
		boolean[] terminal = {false};
		int[] stops = {0};
		PermanentFocusLoss loss = new PermanentFocusLoss(scheduler, () -> terminal[0],
				() -> stops[0]++);
		loss.schedule();
		terminal[0] = true;
		scheduler.runAll();
		assertEquals(0, stops[0]);
	}

	@Test
	public void rescheduleKeepsOnlyLatestStop() {
		FakeScheduler scheduler = new FakeScheduler();
		int[] stops = {0};
		PermanentFocusLoss loss = new PermanentFocusLoss(scheduler, () -> false,
				() -> stops[0]++);
		loss.schedule();
		loss.schedule();
		scheduler.runAll();
		assertEquals(1, stops[0]);
	}

	private static final class FakeScheduler implements PermanentFocusLoss.Scheduler {
		private final List<Runnable> tasks = new ArrayList<>();

		@Override public void postDelayed(Runnable task, long delayMillis) { tasks.add(task); }
		@Override public void removeCallbacks(Runnable task) { tasks.removeIf(current -> current == task); }
		void runAll() { List.copyOf(tasks).forEach(Runnable::run); tasks.clear(); }
	}
}
