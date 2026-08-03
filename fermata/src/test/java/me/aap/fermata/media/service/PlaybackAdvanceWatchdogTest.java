package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public class PlaybackAdvanceWatchdogTest {
	@Test
	public void resolvedDurationSchedulesExpectedDelay() {
		Promise<Long> duration = completedDuration(10_000L);
		FakeScheduler scheduler = new FakeScheduler();
		PlaybackAdvanceWatchdog watchdog = watchdog(scheduler, queue(duration), () -> {});

		watchdog.arm(item(), 2_000L, 2F);

		assertEquals(1, scheduler.tasks.size());
		assertEquals(4_000L, scheduler.tasks.get(0).delayMillis);
	}

	@Test
	public void cancelAfterSchedulingRejectsCallback() {
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger advances = new AtomicInteger();
		PlaybackAdvanceWatchdog watchdog = watchdog(scheduler,
				queue(completedDuration(10_000L)), advances::incrementAndGet);
		watchdog.arm(item(), 0L, 1F);

		watchdog.cancel();
		scheduler.tasks.get(0).task.run();

		assertEquals(0, advances.get());
	}

	@Test
	public void cancelWhileDurationPendingPreventsSchedulingAndAdvance() {
		Promise<Long> duration = new Promise<>();
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger advances = new AtomicInteger();
		PlaybackAdvanceWatchdog watchdog = watchdog(scheduler, queue(duration),
				advances::incrementAndGet);
		watchdog.arm(item(), 0L, 1F);

		watchdog.cancel();
		duration.complete(10_000L);

		assertEquals(0, scheduler.tasks.size());
		assertEquals(0, advances.get());
	}

	@Test
	public void rearmWhileDurationPendingSupersedesOldRequest() {
		Promise<Long> oldDuration = new Promise<>();
		Promise<Long> currentDuration = new Promise<>();
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger advances = new AtomicInteger();
		PlaybackAdvanceWatchdog watchdog = watchdog(scheduler,
				queue(oldDuration, currentDuration), advances::incrementAndGet);

		watchdog.arm(item(), 0L, 1F);
		watchdog.arm(item(), 1_000L, 1F);
		oldDuration.complete(10_000L);
		currentDuration.complete(10_000L);

		assertEquals(1, scheduler.tasks.size());
		assertEquals(9_000L, scheduler.tasks.get(0).delayMillis);
		scheduler.tasks.get(0).task.run();
		assertEquals(1, advances.get());
	}

	@Test
	public void rearmAfterSchedulingSupersedesOldCallback() {
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger advances = new AtomicInteger();
		PlaybackAdvanceWatchdog watchdog = watchdog(scheduler,
				queue(completedDuration(10_000L), completedDuration(20_000L)),
				advances::incrementAndGet);

		watchdog.arm(item(), 0L, 1F);
		watchdog.arm(item(), 0L, 1F);
		scheduler.tasks.get(0).task.run();
		assertEquals(0, advances.get());
		scheduler.tasks.get(1).task.run();
		assertEquals(1, advances.get());
	}

	@Test
	public void activeCallbackAdvancesExactlyOnce() {
		FakeScheduler scheduler = new FakeScheduler();
		AtomicInteger advances = new AtomicInteger();
		PlaybackAdvanceWatchdog watchdog = watchdog(scheduler,
				queue(completedDuration(10_000L)), advances::incrementAndGet);
		watchdog.arm(item(), 0L, 1F);

		scheduler.tasks.get(0).task.run();
		scheduler.tasks.get(0).task.run();

		assertEquals(1, advances.get());
	}

	private static PlaybackAdvanceWatchdog watchdog(FakeScheduler scheduler,
			Queue<FutureSupplier<Long>> durations, Runnable advance) {
		return new PlaybackAdvanceWatchdog(scheduler,
				ignored -> durations.remove(), advance);
	}

	@SafeVarargs
	private static Queue<FutureSupplier<Long>> queue(FutureSupplier<Long>... durations) {
		Queue<FutureSupplier<Long>> queue = new ArrayDeque<>();
		for (FutureSupplier<Long> duration : durations) queue.add(duration);
		return queue;
	}

	private static Promise<Long> completedDuration(long duration) {
		Promise<Long> completed = new Promise<>();
		completed.complete(duration);
		return completed;
	}

	private static PlayableItem item() {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> "watchdog-item";
					default -> null;
				});
	}

	private static final class FakeScheduler implements PlaybackAdvanceWatchdog.Scheduler {
		private final List<ScheduledTask> tasks = new ArrayList<>();

		@Override
		public void postDelayed(Runnable task, long delayMillis) {
			tasks.add(new ScheduledTask(task, delayMillis));
		}
	}

	private record ScheduledTask(Runnable task, long delayMillis) {
	}
}
