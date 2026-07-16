package me.aap.fermata.media.sub;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.IntConsumer;
import me.aap.utils.function.LongSupplier;

/**
 * @author Andrey Pavlenko
 */
public class SubtitlesTest extends Assert {

	@Test
	public void testGetNext() {
		int n = 1000;
		var rnd = new Random();
		Subtitles s = new Subtitles(b -> {});
		assertNull(s.getNext(1));

		s = new Subtitles(b -> {
			for (int i = 0; i < n; i++) {
				b.add("Text " + i, rnd.nextInt(100000), rnd.nextInt(100) + 1);
			}
		});

		for (int i = 0, c = s.size(); i < c; i++) {
			assertEquals(i, s.get(i).getIndex());
		}
		for (var t : s) {
			assertSame(t, s.getNext(t.getTime()));
		}
		s.get(s.size() / 2);
		for (int i = s.size() - 1; i >= 0; i--) {
			Subtitles.Text t = s.get(i);
			assertSame(t, s.getNext(t.getTime()));
		}
	}

	@Test
	public void testSrtSubtitles() throws IOException {
		// @formatter:off
		var text =
"""
WEBVTT
Kind: captions
Language: en

1
00:00:00.900 --> 00:00:01.100
{\\an7}TOP_LEFT

2
00:01:01,000 --> 00:01:03,000
{\\an5}MIDDLE_CENTER

3
09:59:59,999 --> 10:59:59.999
{\\an2}BOTTOM_CENTER

4
11:00:00.000 --> 11:00:00,001
BOTTOM_CENTER

5
12:00:00,000 --> 12:00:00,001
BOTTOM_CENTER
Multi line
""";
		// @formatter:on

		var sg = FileSubtitles.load(new ByteArrayInputStream(text.getBytes()));
		var s = sg.get(SubGrid.Position.TOP_LEFT).get(0);
		assertEquals(900, s.getTime());
		assertEquals(200, s.getDuration());
		assertEquals("TOP_LEFT", s.getText());
		s = sg.get(SubGrid.Position.MIDDLE_CENTER).get(0);
		assertEquals(61000, s.getTime());
		assertEquals(2000, s.getDuration());
		assertEquals("MIDDLE_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(0);
		assertEquals(9 * 60 * 60000 + 59 * 60000 + 59000 + 999, s.getTime());
		assertEquals(3600000, s.getDuration());
		assertEquals("BOTTOM_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(1);
		assertEquals(11 * 60 * 60000, s.getTime());
		assertEquals(1, s.getDuration());
		assertEquals("BOTTOM_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(2);
		assertEquals(12 * 60 * 60000, s.getTime());
		assertEquals(1, s.getDuration());
		assertEquals("BOTTOM_CENTER\nMulti line", s.getText());
	}

	@Test
	public void testScheduler() throws Exception {
		StringBuilder sb = new StringBuilder();
		IntConsumer itos = i -> {
			if (i < 10) {
				sb.append("00").append(i);
			} else if (i < 100) {
				sb.append("0").append(i);
			} else {
				sb.append(i);
			}
		};

		int n = 49;
		int dur = 10;
		List<String> expect = new ArrayList<>(2 * n);
		List<String> received = new ArrayList<>(2 * n);

		for (int i = 1, d = dur; i <= n; i++, d += dur) {
			var s = String.valueOf(i);
			expect.add(s);
			sb.append(s).append('\n');
			sb.append("00:00:00,");
			itos.accept(d);
			d += dur;
			sb.append(" --> 00:00:00,");
			itos.accept(d);
			sb.append('\n').append(s).append("\n\n");
		}

		var sg = FileSubtitles.load(new ByteArrayInputStream(sb.toString().getBytes()));
		var clock = new TestClock();
		var exec = new TestExecutor(clock);
		BiConsumer<SubGrid.Position, Subtitles.Text> consumer = (p, t) -> {
			if (t == null) return;
			received.add(t.getText());
		};
		SubScheduler sched = new SubScheduler(exec, sg, consumer, clock);
		assertSchedulerRun(sched, exec, clock, received, expect, 0, 1);
		assertSchedulerRun(sched, exec, clock, received, expect, 10, 1);
		assertSchedulerRun(sched, exec, clock, received, expect, 10, 2);
		assertSchedulerRun(sched, exec, clock, received, expect, 10, 0.5f);
	}

	private static void assertSchedulerRun(SubScheduler scheduler, TestExecutor executor,
														 TestClock clock, List<String> received, List<String> expected,
														 int delay, float speed) {
		received.clear();
		clock.reset();
		scheduler.start(0, delay, speed);
		executor.runUntilIdle(2 * expected.size() + 10);
		assertArrayEquals(expected.toArray(), received.toArray());
		scheduler.stop(false);
		executor.clear();
	}

	private static final class TestClock implements LongSupplier {
		private long time;

		@Override
		public long getAsLong() {
			return time;
		}

		void reset() {
			time = 0;
		}
	}

	private static final class TestExecutor extends HandlerExecutor {
		private final TestClock clock;
		private final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>(
				Comparator.comparingLong((ScheduledTask t) -> t.time).thenComparingLong(t -> t.sequence));
		private long sequence;

		TestExecutor(TestClock clock) {
			this.clock = clock;
		}

		@Override
		public synchronized Cancellable schedule(Runnable task, long delay) {
			var scheduled = new ScheduledTask(task, clock.time + Math.max(0, delay), sequence++);
			tasks.add(scheduled);
			return scheduled;
		}

		void runUntilIdle(int maxTasks) {
			int count = 0;
			while (!tasks.isEmpty()) {
				if (++count > maxTasks) fail("Subtitle scheduler did not become idle");
				var task = tasks.remove();
				if (task.cancelled) continue;
				clock.time = task.time;
				task.run();
			}
		}

		void clear() {
			tasks.clear();
		}
	}

	private static final class ScheduledTask implements Runnable, Cancellable {
		private final Runnable task;
		private final long time;
		private final long sequence;
		private boolean cancelled;

		ScheduledTask(Runnable task, long time, long sequence) {
			this.task = task;
			this.time = time;
			this.sequence = sequence;
		}

		@Override
		public void run() {
			cancelled = true;
			task.run();
		}

		@Override
		public boolean cancel() {
			if (cancelled) return false;
			cancelled = true;
			return true;
		}
	}
}
