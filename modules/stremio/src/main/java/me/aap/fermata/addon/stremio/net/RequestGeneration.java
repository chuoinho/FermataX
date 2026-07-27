package me.aap.fermata.addon.stremio.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

import me.aap.utils.log.Log;

public final class RequestGeneration implements AutoCloseable {
	private static final long CLOSED = Long.MIN_VALUE;
	private final AtomicLong generation = new AtomicLong();
	private final Object observerLock = new Object();
	private final Map<Long, List<Registration>> observers = new HashMap<>();

	public Token begin() {
		List<Registration> invalidated;
		long next;
		synchronized (observerLock) {
			long current = generation.get();
			if (current == CLOSED) throw new IllegalStateException("Request generation is closed");
			next = current + 1;
			generation.set(next);
			invalidated = observers.remove(current);
		}
		notifyInvalidated(invalidated);
		return new Token(this, next);
	}

	public void cancelAll() {
		List<Registration> invalidated;
		synchronized (observerLock) {
			long current = generation.get();
			if (current == CLOSED) return;
			generation.set(current + 1);
			invalidated = observers.remove(current);
		}
		notifyInvalidated(invalidated);
	}

	public boolean isCurrent(Token token) {
		return (token != null) && (token.owner == this) &&
				(token.generation == generation.get());
	}

	@Override
	public void close() {
		List<Registration> invalidated = new ArrayList<>();
		synchronized (observerLock) {
			if (generation.get() == CLOSED) return;
			generation.set(CLOSED);
			for (List<Registration> registrations : observers.values()) {
				invalidated.addAll(registrations);
			}
			observers.clear();
		}
		notifyInvalidated(invalidated);
	}

	private AutoCloseable observe(Token token, Runnable observer) {
		Objects.requireNonNull(observer, "observer");
		Registration registration;
		synchronized (observerLock) {
			if (isCurrent(token)) {
				registration = new Registration(token.generation, observer);
				observers.computeIfAbsent(token.generation, ignored -> new ArrayList<>())
						.add(registration);
				return registration;
			}
		}
		notifyInvalidated(List.of(new Registration(token.generation, observer)));
		return () -> {};
	}

	private void notifyInvalidated(List<Registration> registrations) {
		if (registrations == null) return;
		for (Registration registration : registrations) registration.invalidate();
	}

	private final class Registration implements AutoCloseable {
		private final long generation;
		private final Runnable observer;
		private boolean closed;

		private Registration(long generation, Runnable observer) {
			this.generation = generation;
			this.observer = observer;
		}

		private void invalidate() {
			synchronized (observerLock) {
				if (closed) return;
				closed = true;
		}
		try {
			observer.run();
		} catch (Throwable error) {
			Log.e("Stremio generation observer failed: ", error.getClass().getName());
		}
	}

		@Override
		public void close() {
			synchronized (observerLock) {
				if (closed) return;
				closed = true;
				List<Registration> registrations = observers.get(generation);
				if (registrations == null) return;
				registrations.remove(this);
				if (registrations.isEmpty()) observers.remove(generation);
			}
		}
	}

	public static final class Token {
		private final RequestGeneration owner;
		private final long generation;

		private Token(RequestGeneration owner, long generation) {
			this.owner = owner;
			this.generation = generation;
		}

		public long value() {
			return generation;
		}

		public boolean isCurrent() {
			return owner.isCurrent(this);
		}

		public void throwIfStale() {
			if (!isCurrent()) throw new CancellationException("Stale Stremio request generation");
		}

		/** Invokes the observer once when this token is superseded, cancelled or closed. */
		public AutoCloseable onInvalidated(Runnable observer) {
			return owner.observe(this, observer);
		}
	}
}
