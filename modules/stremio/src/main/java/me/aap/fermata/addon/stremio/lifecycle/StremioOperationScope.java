package me.aap.fermata.addon.stremio.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import me.aap.fermata.addon.stremio.net.RequestGeneration;

/** Parent lifecycle owner for independently cancellable Stremio operations. */
public final class StremioOperationScope implements AutoCloseable {
	private final ScheduledExecutorService scheduler;
	private final AtomicLong ids = new AtomicLong();
	private final AtomicBoolean closed = new AtomicBoolean();
	private final Set<StremioOperation> active = new LinkedHashSet<>();

	public StremioOperationScope(ScheduledExecutorService scheduler) {
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
	}

	public StremioOperation open(String logicalKey, RequestGeneration.Token generation) {
		synchronized (active) {
			if (closed.get()) throw new IllegalStateException("Stremio operation scope is closed");
			StremioOperation[] holder = new StremioOperation[1];
			StremioOperation operation = new StremioOperation(ids.incrementAndGet(), logicalKey,
					generation, scheduler, () -> retire(holder[0]));
			holder[0] = operation;
			if (!operation.isCurrent()) {
				operation.close();
				throw new CancellationException("Stremio operation generation is stale");
			}
			active.add(operation);
			return operation;
		}
	}

	public int activeCount() {
		synchronized (active) {
			return active.size();
		}
	}

	private void retire(StremioOperation operation) {
		synchronized (active) {
			active.remove(operation);
		}
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		for (StremioOperation operation : snapshot()) operation.close();
	}

	private ArrayList<StremioOperation> snapshot() {
		synchronized (active) {
			ArrayList<StremioOperation> snapshot = new ArrayList<>(active);
			active.clear();
			return snapshot;
		}
	}
}
