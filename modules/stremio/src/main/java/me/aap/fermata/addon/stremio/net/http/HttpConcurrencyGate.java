package me.aap.fermata.addon.stremio.net.http;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-blocking global/per-host admission control for logical HTTP calls. */
final class HttpConcurrencyGate {
	private final int maxGlobal;
	private final int maxPerHost;
	private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();
	private final Map<String, Integer> activeByHost = new HashMap<>();
	private int activeGlobal;

	HttpConcurrencyGate(int maxGlobal, int maxPerHost) {
		if (maxGlobal <= 0) throw new IllegalArgumentException("maxGlobal must be positive");
		if (maxPerHost <= 0) throw new IllegalArgumentException("maxPerHost must be positive");
		if (maxPerHost > maxGlobal) {
			throw new IllegalArgumentException("maxPerHost cannot exceed maxGlobal");
		}
		this.maxGlobal = maxGlobal;
		this.maxPerHost = maxPerHost;
	}

	CompletableFuture<Permit> acquire(String host) {
		Waiter waiter = new Waiter(normalizeHost(host), true, null);
		enqueue(waiter);
		return waiter.future;
	}

	private void enqueue(Waiter waiter) {
		ArrayList<Grant> grants;
		synchronized (this) {
			waiters.addLast(waiter);
			grants = drainLocked();
		}
		complete(grants);
	}

	private void cancel(Waiter waiter) {
		ArrayList<Grant> grants;
		synchronized (this) {
			if (!waiters.remove(waiter)) return;
			grants = drainLocked();
		}
		complete(grants);
	}

	private CompletableFuture<Void> move(Permit permit, String host) {
		String target = normalizeHost(host);
		Waiter waiter;
		ArrayList<Grant> grants;
		synchronized (this) {
			if (permit.closed.get()) return StremioFutures.failedFuture(
					new IllegalStateException("HTTP concurrency permit is closed"));
			if (target.equals(permit.host)) return CompletableFuture.completedFuture(null);
			if (permit.waiter != null) return StremioFutures.failedFuture(
					new IllegalStateException("HTTP concurrency permit is already moving"));

			releaseHostLocked(permit.host);
			permit.host = null;
			waiter = new Waiter(target, false, permit);
			permit.waiter = waiter;
			waiters.addLast(waiter);
			grants = drainLocked();
		}
		complete(grants);
		return waiter.moved;
	}

	private void release(Permit permit) {
		ArrayList<Grant> grants;
		synchronized (this) {
			Waiter waiter = permit.waiter;
			if (waiter != null) {
				waiters.remove(waiter);
				permit.waiter = null;
				waiter.moved.cancel(false);
			}
			releaseHostLocked(permit.host);
			permit.host = null;
			activeGlobal--;
			if (activeGlobal < 0) throw new IllegalStateException("Negative global permit count");
			grants = drainLocked();
		}
		complete(grants);
	}

	private ArrayList<Grant> drainLocked() {
		var grants = new ArrayList<Grant>();
		for (var iterator = waiters.iterator(); iterator.hasNext(); ) {
			Waiter waiter = iterator.next();
			if (waiter.future.isCancelled() || waiter.moved.isCancelled()) {
				iterator.remove();
				continue;
			}
			if (waiter.needsGlobal && (activeGlobal >= maxGlobal)) continue;
			if (activeByHost.getOrDefault(waiter.host, 0) >= maxPerHost) continue;

			iterator.remove();
			if (waiter.needsGlobal) activeGlobal++;
			activeByHost.merge(waiter.host, 1, Integer::sum);
			Permit permit = waiter.permit;
			if (permit == null) permit = new Permit(waiter.host);
			else {
				permit.waiter = null;
				permit.host = waiter.host;
			}
			grants.add(new Grant(waiter, permit));
		}
		return grants;
	}

	private void releaseHostLocked(String host) {
		if (host == null) return;
		int count = activeByHost.getOrDefault(host, 0);
		if (count <= 0) throw new IllegalStateException("Missing host permit");
		if (count == 1) activeByHost.remove(host);
		else activeByHost.put(host, count - 1);
	}

	private void complete(ArrayList<Grant> grants) {
		for (Grant grant : grants) {
			if (grant.waiter.needsGlobal) {
				if (!grant.waiter.future.complete(grant.permit)) grant.permit.close();
			} else if (!grant.waiter.moved.complete(null)) {
				grant.permit.close();
			}
		}
	}

	private static String normalizeHost(String host) {
		String normalized = Objects.requireNonNull(host, "host").trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) throw new IllegalArgumentException("host is empty");
		return normalized;
	}

	final class Permit implements AutoCloseable {
		private final AtomicBoolean closed = new AtomicBoolean();
		private volatile String host;
		private Waiter waiter;

		private Permit(String host) {
			this.host = host;
		}

		CompletableFuture<Void> moveTo(String host) {
			return move(this, host);
		}

		@Override
		public void close() {
			if (closed.compareAndSet(false, true)) release(this);
		}
	}

	private final class Waiter {
		private final String host;
		private final boolean needsGlobal;
		private final Permit permit;
		private final CompletableFuture<Permit> future = new CompletableFuture<>();
		private final CompletableFuture<Void> moved = new CompletableFuture<>();

		private Waiter(String host, boolean needsGlobal, Permit permit) {
			this.host = host;
			this.needsGlobal = needsGlobal;
			this.permit = permit;
			if (needsGlobal) future.whenComplete((ignored, error) -> {
				if (future.isCancelled()) cancel(this);
			});
			else moved.whenComplete((ignored, error) -> {
				if (moved.isCancelled()) cancel(this);
			});
		}
	}

	private record Grant(Waiter waiter, Permit permit) {
	}
}
