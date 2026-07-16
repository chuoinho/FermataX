package me.aap.fermata.addon.tv.m3u;

import static me.aap.utils.async.Completed.completedVoid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.CheckedSupplier;

public class XmlTvUpdateSchedulerTest {
	@Test
	public void replacingScheduleCancelsOldTask() throws Throwable {
		FakeScheduler fake = new FakeScheduler();
		XmlTvUpdateScheduler updates = new XmlTvUpdateScheduler(fake);
		AtomicInteger calls = new AtomicInteger();

		updates.schedule(() -> {
			calls.incrementAndGet();
			return completedVoid();
		}, 1L);
		updates.schedule(() -> {
			calls.incrementAndGet();
			return completedVoid();
		}, 2L);

		assertTrue(fake.tasks.get(0).future.isCancelled());
		fake.run(0);
		fake.run(1);
		assertEquals(1, calls.get());
	}

	@Test
	public void closeCancelsPendingTaskAndRejectsNewSchedules() throws Throwable {
		FakeScheduler fake = new FakeScheduler();
		XmlTvUpdateScheduler updates = new XmlTvUpdateScheduler(fake);
		AtomicInteger calls = new AtomicInteger();

		updates.schedule(() -> {
			calls.incrementAndGet();
			return completedVoid();
		}, 1L);
		updates.close();
		updates.close();
		FutureSupplier<?> rejected = updates.schedule(() -> {
			calls.incrementAndGet();
			return completedVoid();
		}, 2L);

		assertTrue(updates.isClosed());
		assertTrue(fake.tasks.get(0).future.isCancelled());
		assertEquals(1, fake.tasks.size());
		assertTrue(rejected.isDone());
		fake.run(0);
		assertEquals(0, calls.get());
	}

	@Test
	public void executedTaskCanInstallSuccessorWithoutSelfCancellation() throws Throwable {
		FakeScheduler fake = new FakeScheduler();
		XmlTvUpdateScheduler updates = new XmlTvUpdateScheduler(fake);

		updates.schedule(() -> {
			updates.schedule(() -> completedVoid(), 2L);
			return completedVoid();
		}, 1L);
		fake.run(0);

		assertFalse(fake.tasks.get(0).future.isCancelled());
		assertEquals(2, fake.tasks.size());
	}

	private static final class FakeScheduler implements XmlTvUpdateScheduler.Scheduler {
		final List<Task<?>> tasks = new ArrayList<>();

		@Override
		public <T> FutureSupplier<T> schedule(
				CheckedSupplier<FutureSupplier<T>, Throwable> task, long delay) {
			Task<T> t = new Task<>(task, delay);
			tasks.add(t);
			return t.future;
		}

		void run(int index) throws Throwable {
			Task<?> task = tasks.get(index);
			if (!task.future.isCancelled()) task.supplier.get();
		}
	}

	private static final class Task<T> {
		final CheckedSupplier<FutureSupplier<T>, Throwable> supplier;
		final long delay;
		final Promise<T> future = new Promise<>();

		Task(CheckedSupplier<FutureSupplier<T>, Throwable> supplier, long delay) {
			this.supplier = supplier;
			this.delay = delay;
		}
	}
}
