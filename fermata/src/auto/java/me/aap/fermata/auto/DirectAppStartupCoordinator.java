package me.app.fermatax.auto;

import java.util.function.Consumer;
import java.util.function.Predicate;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/**
 * Serializes the service connection and rejects activity callbacks from stale AA generations.
 */
final class DirectAppStartupCoordinator<C> {
	private static final int MAX_CONNECT_ATTEMPTS = 2;
	private final Predicate<C> isConnected;
	private final Consumer<C> disconnect;
	private State state = State.IDLE;
	private C service;
	private Promise<C> connectionResult;
	private FutureSupplier<C> connectionAttempt;
	private long connectionEpoch;
	private long activityGeneration;

	DirectAppStartupCoordinator(Predicate<C> isConnected, Consumer<C> disconnect) {
		this.isConnected = isConnected;
		this.disconnect = disconnect;
	}

	Startup<C> begin(Connector<C> connector) {
		C stale = null;
		Promise<C> result;
		boolean connect = false;
		long generation;

		synchronized (this) {
			generation = ++activityGeneration;
			if ((service != null) && !isConnected.test(service)) {
				stale = service;
				service = null;
			}

			if (service != null) {
				state = State.SERVICE_READY;
				result = new Promise<>();
				result.complete(service);
			} else {
				result = connectionResult;
				if (result == null) {
					connectionResult = result = new Promise<>();
					state = State.SERVICE_CONNECTING;
					connect = true;
				}
			}
		}

		if (stale != null) disconnect.accept(stale);
		if (connect) startAttempt(connector, result, 1);
		return new Startup<>(generation, result);
	}

	boolean beginUi(long generation, C connection) {
		synchronized (this) {
			if ((generation != activityGeneration) || (connection != service) ||
					!isConnected.test(connection)) return false;
			state = State.UI_ATTACHING;
			return true;
		}
	}

	void uiReady(long generation) {
		synchronized (this) {
			if ((generation == activityGeneration) && (state == State.UI_ATTACHING)) {
				state = State.UI_READY;
			}
		}
	}

	void uiFailed(long generation) {
		synchronized (this) {
			if (generation == activityGeneration) {
				state = ((service != null) && isConnected.test(service)) ?
						State.SERVICE_READY : State.IDLE;
			}
		}
	}

	boolean activityDestroyed(long generation) {
		synchronized (this) {
			if (generation != activityGeneration) return false;
			state = ((service != null) && isConnected.test(service)) ?
					State.SERVICE_READY : State.IDLE;
			return true;
		}
	}

	boolean isCurrent(long generation) {
		synchronized (this) {
			return generation == activityGeneration;
		}
	}

	C shutdown() {
		FutureSupplier<C> attempt;
		Promise<C> result;
		C current;
		synchronized (this) {
			activityGeneration++;
			connectionEpoch++;
			attempt = connectionAttempt;
			connectionAttempt = null;
			result = connectionResult;
			connectionResult = null;
			current = service;
			service = null;
			state = State.IDLE;
		}
		if (attempt != null) attempt.cancel();
		if (result != null) result.cancel();
		return current;
	}

	State getState() {
		synchronized (this) {
			return state;
		}
	}

	private void startAttempt(Connector<C> connector, Promise<C> result, int attemptNumber) {
		FutureSupplier<C> attempt;
		try {
			attempt = connector.connect();
		} catch (Throwable failure) {
			onAttemptComplete(connector, result, attemptNumber, 0, null, failure);
			return;
		}

		long epoch;
		synchronized (this) {
			if (connectionResult != result) {
				attempt.cancel();
				return;
			}
			connectionAttempt = attempt;
			epoch = ++connectionEpoch;
		}
		attempt.onCompletion((connection, failure) ->
				onAttemptComplete(connector, result, attemptNumber, epoch, connection, failure));
	}

	private void onAttemptComplete(Connector<C> connector, Promise<C> result, int attemptNumber,
			long epoch, C connection, Throwable failure) {
		boolean retry = false;
		boolean complete = false;
		boolean dispose = false;
		Throwable terminalFailure = failure;

		synchronized (this) {
			if ((connectionResult != result) || ((epoch != 0) && (epoch != connectionEpoch))) {
				dispose = connection != null;
			} else if ((failure == null) && (connection != null) && isConnected.test(connection)) {
				service = connection;
				connectionAttempt = null;
				connectionResult = null;
				state = State.SERVICE_READY;
				complete = true;
			} else if (attemptNumber < MAX_CONNECT_ATTEMPTS) {
				connectionAttempt = null;
				retry = true;
			} else {
				connectionAttempt = null;
				connectionResult = null;
				state = State.IDLE;
				if (terminalFailure == null) {
					terminalFailure = new IllegalStateException("Media service connection is not alive");
				}
				complete = true;
				dispose = connection != null;
			}
		}

		if (dispose) disconnect.accept(connection);
		if (retry) {
			if (connection != null) disconnect.accept(connection);
			startAttempt(connector, result, attemptNumber + 1);
		} else if (complete) {
			if (terminalFailure == null) result.complete(connection);
			else result.completeExceptionally(terminalFailure);
		}
	}

	enum State {
		IDLE,
		SERVICE_CONNECTING,
		SERVICE_READY,
		UI_ATTACHING,
		UI_READY
	}

	@FunctionalInterface
	interface Connector<C> {
		FutureSupplier<C> connect();
	}

	static final class Startup<C> {
		private final long generation;
		private final FutureSupplier<C> connection;

		Startup(long generation, FutureSupplier<C> connection) {
			this.generation = generation;
			this.connection = connection;
		}

		long getGeneration() {
			return generation;
		}

		FutureSupplier<C> getConnection() {
			return connection;
		}
	}
}
