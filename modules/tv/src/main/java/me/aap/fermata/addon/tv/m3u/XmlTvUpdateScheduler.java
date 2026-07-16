package me.aap.fermata.addon.tv.m3u;

import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;

import java.io.Closeable;
import java.util.Objects;

import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.CheckedSupplier;

final class XmlTvUpdateScheduler implements Closeable {
	private final Scheduler scheduler;
	private FutureSupplier<?> scheduled = completedVoid();
	private long generation;
	private boolean closed;

	XmlTvUpdateScheduler() {
		this(new Scheduler() {
			@Override
			public <T> FutureSupplier<T> schedule(
					CheckedSupplier<FutureSupplier<T>, Throwable> task, long delay) {
				return Async.schedule(task, delay);
			}
		});
	}

	XmlTvUpdateScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}

	synchronized <T> FutureSupplier<T> schedule(
			CheckedSupplier<FutureSupplier<T>, Throwable> task, long delay) {
		if (closed) return completedNull();

		scheduled.cancel();
		long generation = ++this.generation;
		FutureSupplier<T> next = scheduler.schedule(() -> {
			synchronized (this) {
				if (closed || (generation != this.generation)) return completedNull();
				scheduled = completedVoid();
			}
			return task.get();
		}, Math.max(0L, delay));
		scheduled = Objects.requireNonNull(next);
		return next;
	}

	synchronized boolean isClosed() {
		return closed;
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;
		generation++;
		scheduled.cancel();
		scheduled = completedVoid();
	}

	interface Scheduler {
		<T> FutureSupplier<T> schedule(CheckedSupplier<FutureSupplier<T>, Throwable> task,
				long delay);
	}
}
