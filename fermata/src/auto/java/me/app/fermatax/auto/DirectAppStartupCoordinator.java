package me.app.fermatax.auto;

import java.util.function.Consumer;
import java.util.function.Predicate;

import me.aap.fermata.ui.activity.AsyncOperationController.DiagnosticsObserver;
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
		boolean reuseService = false;
		long generation;

		synchronized (this) {
			generation = ++activityGeneration;
			if ((service != null) && !isConnected.test(service)) {
				stale = service;
				service = null;
			}

			if (service != null) {
				state = State.SERVICE_READY;
				reuseService = true;
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

		DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.STARTED, generation, 0L,
				0, null);
		if (stale != null) disconnect.accept(stale);
		if (reuseService) {
			DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.SERVICE_READY, generation,
					0L, 0, null);
		} else if (connect) startAttempt(connector, result, 1);
		return new Startup<>(generation, result);
	}

	boolean beginUi(long generation, C connection) {
		synchronized (this) {
			if ((generation != activityGeneration) || (connection != service) ||
					!isConnected.test(connection)) return false;
			state = State.UI_ATTACHING;
			DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.UI_ATTACH_STARTED,
					generation, 0L, 0, null);
			return true;
		}
	}

	void uiReady(long generation) {
		boolean current;
		synchronized (this) {
			current = (generation == activityGeneration) && (state == State.UI_ATTACHING);
			if (current) {
				state = State.UI_READY;
			}
		}
		if (current) DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.UI_READY,
				generation, 0L, 0, null);
		else DiagnosticsObserver.startupDetail(DiagnosticsObserver.StartupEvent.UI_READY,
				generation, 0L, 0);
	}

	void uiFailed(long generation) {
		boolean current;
		synchronized (this) {
			current = generation == activityGeneration;
			if (current) {
				state = ((service != null) && isConnected.test(service)) ?
						State.SERVICE_READY : State.IDLE;
			}
		}
		if (current) DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.UI_FAILED,
				generation, 0L, 0, null);
		else DiagnosticsObserver.startupDetail(DiagnosticsObserver.StartupEvent.UI_FAILED,
				generation, 0L, 0);
	}

	boolean activityDestroyed(long generation) {
		boolean current;
		synchronized (this) {
			current = generation == activityGeneration;
			if (!current) return false;
			state = ((service != null) && isConnected.test(service)) ?
					State.SERVICE_READY : State.IDLE;
		}
		DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.ACTIVITY_DESTROYED,
				generation, 0L, 0, null);
		return true;
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
		long generation;
		synchronized (this) {
			generation = activityGeneration;
		}
		DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.SERVICE_ATTEMPT_STARTED,
				generation, 0L, attemptNumber, null);
		try {
			attempt = connector.connect();
		} catch (Throwable failure) {
			onAttemptComplete(connector, result, generation, attemptNumber, 0, null, failure);
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
				onAttemptComplete(connector, result, generation, attemptNumber, epoch, connection, failure));
	}

	private void onAttemptComplete(Connector<C> connector, Promise<C> result, long generation,
			int attemptNumber, long epoch, C connection, Throwable failure) {
		boolean retry = false;
		boolean complete = false;
		boolean dispose = false;
		boolean stale = false;
		Throwable terminalFailure = failure;

		synchronized (this) {
			if ((connectionResult != result) || ((epoch != 0) && (epoch != connectionEpoch))) {
				stale = true;
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

		if (stale) {
			DiagnosticsObserver.startupDetail(DiagnosticsObserver.StartupEvent.SERVICE_ATTEMPT_CALLBACK,
					generation, epoch, attemptNumber);
		}
		if (dispose) disconnect.accept(connection);
		if (retry) {
			DiagnosticsObserver.startupDetail(DiagnosticsObserver.StartupEvent.SERVICE_ATTEMPT_CALLBACK,
					generation, epoch, attemptNumber);
			if (connection != null) disconnect.accept(connection);
			startAttempt(connector, result, attemptNumber + 1);
		} else if (complete) {
			if (terminalFailure == null) {
				DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.SERVICE_READY,
						generation, epoch, attemptNumber, null);
			} else {
				DiagnosticsObserver.startup(DiagnosticsObserver.StartupEvent.SERVICE_FAILED,
						generation, epoch, attemptNumber, terminalFailure);
			}
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
