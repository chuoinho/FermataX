package me.aap.utils.ui.activity;

import java.util.ArrayList;
import java.util.List;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

final class PendingActivityBroker<T> {
	private final List<Promise<T>> waiters = new ArrayList<>();

	synchronized Request<T> acquire() {
		waiters.removeIf(FutureSupplier::isDone);
		Promise<T> waiter = new Promise<>();
		boolean first = waiters.isEmpty();
		waiters.add(waiter);
		return new Request<>(waiter, first);
	}

	void complete(T value, Throwable error) {
		List<Promise<T>> pending;
		synchronized (this) {
			pending = new ArrayList<>(waiters);
			waiters.clear();
		}
		for (Promise<T> waiter : pending) {
			if (error == null) waiter.complete(value);
			else waiter.completeExceptionally(error);
		}
	}

	record Request<T>(FutureSupplier<T> future, boolean first) {
	}
}
