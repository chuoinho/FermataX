package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class PlaybackStopTimerTest {
	@Test
	public void reportsRemainingSecondsUsingLegacyTruncation() {
		AtomicLong clock = new AtomicLong(1_000L);
		List<Runnable> tasks = new ArrayList<>();
		PlaybackStopTimer timer = new PlaybackStopTimer(
				(task, delay) -> tasks.add(task), clock::get, () -> {});

		timer.setSeconds(10);
		assertEquals(10, timer.getRemainingSeconds());
		clock.set(2_001L);
		assertEquals(8, timer.getRemainingSeconds());
	}

	@Test
	public void cancelledCallbackCannotStopPlayback() {
		List<Runnable> tasks = new ArrayList<>();
		AtomicInteger stops = new AtomicInteger();
		PlaybackStopTimer timer = new PlaybackStopTimer(
				(task, delay) -> tasks.add(task), () -> 0L, stops::incrementAndGet);

		timer.setSeconds(10);
		timer.setSeconds(0);
		tasks.get(0).run();

		assertEquals(0, stops.get());
		assertEquals(0, timer.getRemainingSeconds());
	}

	@Test
	public void replacementRejectsOldCallbackAndAcceptsCurrentOne() {
		List<Runnable> tasks = new ArrayList<>();
		AtomicInteger stops = new AtomicInteger();
		PlaybackStopTimer timer = new PlaybackStopTimer(
				(task, delay) -> tasks.add(task), () -> 0L, stops::incrementAndGet);

		timer.setSeconds(10);
		timer.setSeconds(20);
		tasks.get(0).run();
		assertEquals(0, stops.get());
		tasks.get(1).run();
		assertEquals(1, stops.get());
	}

	@Test
	public void expiryCallbackFiresExactlyOnce() {
		List<Runnable> tasks = new ArrayList<>();
		AtomicInteger stops = new AtomicInteger();
		PlaybackStopTimer timer = new PlaybackStopTimer(
				(task, delay) -> tasks.add(task), () -> 0L, stops::incrementAndGet);

		timer.setSeconds(10);
		tasks.get(0).run();
		tasks.get(0).run();

		assertEquals(1, stops.get());
		assertEquals(0, timer.getRemainingSeconds());
	}

	@Test
	public void remainingSecondsNeverBecomesNegativeAfterDeadline() {
		AtomicLong clock = new AtomicLong(1_000L);
		PlaybackStopTimer timer = new PlaybackStopTimer(
				(task, delay) -> {}, clock::get, () -> {});

		timer.setSeconds(1);
		clock.set(2_001L);

		assertEquals(0, timer.getRemainingSeconds());
	}
}
