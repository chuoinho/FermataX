package me.aap.utils.ui.activity;

import java.util.ArrayList;
import java.util.List;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

final class PendingActivityBroker<T> {
	private final List<Promise<T>> waiters = new ArrayList<>();
	private long generation;
	private long activeGeneration;

	synchronized Request<T> acquire() {
		waiters.removeIf(FutureSupplier::isDone);
		Promise<T> waiter = new Promise<>();
		boolean first = waiters.isEmpty() && (activeGeneration == 0);
		waiters.add(waiter);
		return new Request<>(waiter, first);
	}

	synchronized long beginActivity() {
		return activeGeneration = ++generation;
	}

	void complete(long activityGeneration, T value, Throwable error) {
		List<Promise<T>> pending;
		synchronized (this) {
			if ((activityGeneration == 0) || (activityGeneration != activeGeneration)) return;
			activeGeneration = 0;
			pending = new ArrayList<>(waiters);
			waiters.clear();
		}
		for (Promise<T> waiter : pending) {
			if (error == null) waiter.complete(value);
			else waiter.completeExceptionally(error);
		}
	}

	void cancel(long activityGeneration, Throwable error) {
		complete(activityGeneration, null, error);
	}

	record Request<T>(FutureSupplier<T> future, boolean first) {
	}
}
