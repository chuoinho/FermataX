package me.aap.fermata.media.service;

/**
 * Process-wide generation gate for Android Auto projection work.
 *
 * <p>The gate starts open so the Android Auto host can bind while {@code CarConnection} is still
 * publishing its first asynchronous value. Once a confirmed projected drive is shut down, the
 * gate stays quiescent until a later confirmed projection opens a new generation.</p>
 */
public final class AutomotiveRuntimeGate {
	private static State state = State.OPEN_UNCONFIRMED;
	private static long generation;

	private AutomotiveRuntimeGate() {
	}

	/** Returns the active generation, or {@code -1} while the previous one is still stopping. */
	public static synchronized long projectionConnected() {
		if (state == State.SHUTTING_DOWN) return -1L;
		if (state == State.ACTIVE) return generation;
		state = State.ACTIVE;
		return ++generation;
	}

	/** Atomically closes the active generation to all new runtime work. */
	public static synchronized long beginShutdown() {
		if (state != State.ACTIVE) return -1L;
		state = State.SHUTTING_DOWN;
		return generation;
	}

	/** Marks a fully torn-down generation quiescent without affecting a newer generation. */
	public static synchronized void completeShutdown(long expectedGeneration) {
		if ((state == State.SHUTTING_DOWN) && (generation == expectedGeneration)) {
			state = State.QUIESCENT;
		}
	}

	/** New service, binding, playback and projection work is rejected after shutdown starts. */
	public static synchronized boolean allowsNewWork() {
		return (state == State.OPEN_UNCONFIRMED) || (state == State.ACTIVE);
	}

	public static synchronized boolean isProjectionActive() {
		return state == State.ACTIVE;
	}

	public static synchronized boolean isActiveGeneration(long expectedGeneration) {
		return (state == State.ACTIVE) && (generation == expectedGeneration);
	}

	public static synchronized long currentGeneration() {
		return (state == State.ACTIVE) ? generation : 0L;
	}

	static synchronized State stateForTests() {
		return state;
	}

	static synchronized void resetForTests() {
		state = State.OPEN_UNCONFIRMED;
		generation = 0L;
	}

	enum State {
		OPEN_UNCONFIRMED,
		ACTIVE,
		SHUTTING_DOWN,
		QUIESCENT
	}
}
