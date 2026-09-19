package me.aap.fermata.auto;

import static me.aap.utils.async.Completed.completed;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

/** Process-local bridge from the phone controller to the active projected car UI. */
public final class AutomotiveNavigationController {
	private static final AutomotiveNavigationController INSTANCE =
			new AutomotiveNavigationController();
	private final AutomotiveConnectionState connection;
	private final OpenOnCarMode openOnCarMode = new OpenOnCarMode();
	private final Set<ReadinessListener> readinessListeners = new LinkedHashSet<>();
	private Navigator navigator;
	private long registrationGeneration;
	private long requestGeneration;
	private boolean readinessAvailable;
	private long readinessEpoch;

	AutomotiveNavigationController() {
		this(AutomotiveConnectionState.get());
	}

	AutomotiveNavigationController(AutomotiveConnectionState connection) {
		this.connection = connection;
		readinessEpoch = connection.connectionEpoch();
		openOnCarMode.setAvailable(false, readinessEpoch);
		connection.addRawConnectionListener((connected, epoch) -> updateReadiness());
	}

	public static AutomotiveNavigationController get() {
		return INSTANCE;
	}

	public void register(Navigator navigator) {
		synchronized (this) {
			this.navigator = navigator;
			registrationGeneration++;
			requestGeneration++;
		}
		updateReadiness();
	}

	public void unregister(Navigator navigator) {
		synchronized (this) {
			if (this.navigator != navigator) return;
			this.navigator = null;
			registrationGeneration++;
			requestGeneration++;
		}
		updateReadiness();
	}

	public synchronized OpenOnCarMode getOpenOnCarMode() {
		return openOnCarMode;
	}

	public void addReadinessListener(ReadinessListener listener) {
		boolean available;
		long epoch;
		synchronized (this) {
			readinessListeners.add(listener);
			available = readinessAvailable;
			epoch = readinessEpoch;
		}
		listener.onReadinessChanged(available, epoch);
	}

	public synchronized void removeReadinessListener(ReadinessListener listener) {
		readinessListeners.remove(listener);
	}

	public FutureSupplier<OpenResult> open(int destinationId) {
		Navigator captured;
		long registration;
		long requestId;
		long connectionEpoch;
		synchronized (this) {
			captured = navigator;
			if ((captured == null) || !connection.isProjectionConnected()) {
				return completed(OpenResult.NOT_READY);
			}
			registration = registrationGeneration;
			requestId = ++requestGeneration;
			connectionEpoch = connection.connectionEpoch();
		}
		BooleanSupplier stillCurrent = () -> isCurrent(captured, registration, requestId,
				connectionEpoch);
		return dispatch(captured, stillCurrent, () -> captured.open(destinationId, stillCurrent));
	}

	/**
	 * Dispatches a typed request to the currently registered projected host. Caller-provided
	 * token values are never trusted for host/request identity; only mode/source values survive.
	 */
	public FutureSupplier<OpenResult> open(OpenOnCarRequest request) {
		if (request == null) return completed(OpenResult.FAILED);
		Navigator captured;
		long registration;
		long requestId;
		long connectionEpoch;
		long modeRevision;
		synchronized (this) {
			captured = navigator;
			if ((captured == null) || !connection.isProjectionConnected()) {
				return completed(OpenResult.NOT_READY);
			}
			modeRevision = openOnCarMode.revision();
			if (request.token().modeRevision() != modeRevision) return completed(OpenResult.CANCELLED);
			registration = registrationGeneration;
			requestId = ++requestGeneration;
			connectionEpoch = connection.connectionEpoch();
		}
		OpenOnCarRequest stamped = request.stamp(new OpenOnCarToken(connectionEpoch, registration,
				modeRevision, request.token().sourceGeneration(), requestId));
		BooleanSupplier stillCurrent = () -> isCurrent(captured, registration, requestId,
				connectionEpoch) && (openOnCarMode.revision() == modeRevision);
		return dispatch(captured, stillCurrent, () -> captured.open(stamped, stillCurrent));
	}

	private FutureSupplier<OpenResult> dispatch(Navigator captured, BooleanSupplier stillCurrent,
			me.aap.utils.function.Supplier<FutureSupplier<OpenResult>> open) {
		try {
			FutureSupplier<OpenResult> result = open.get();
			if (result == null) return completed(OpenResult.FAILED);
			return result.map(value -> stillCurrent.getAsBoolean() ?
					(value == null ? OpenResult.FAILED : value) : OpenResult.CANCELLED)
					.ifFail(error -> {
						if (!stillCurrent.getAsBoolean()) return OpenResult.CANCELLED;
						Log.e(error, "Phone automotive navigation failed");
						return OpenResult.FAILED;
					});
		} catch (RuntimeException error) {
			Log.e(error, "Phone automotive navigation failed");
			return completed(stillCurrent.getAsBoolean() ? OpenResult.FAILED : OpenResult.CANCELLED);
		}
	}

	private synchronized boolean isCurrent(Navigator captured, long registration, long request,
			long connectionEpoch) {
		return (navigator == captured) && (registrationGeneration == registration) &&
				(requestGeneration == request) && connection.isProjectionConnected() &&
				(connection.connectionEpoch() == connectionEpoch);
	}

	private void updateReadiness() {
		ReadinessListener[] notify;
		boolean available;
		long epoch;
		synchronized (this) {
			available = (navigator != null) && connection.isProjectionConnected();
			epoch = connection.connectionEpoch();
			if ((available == readinessAvailable) && (epoch == readinessEpoch)) return;
			readinessAvailable = available;
			readinessEpoch = epoch;
			openOnCarMode.setAvailable(available, epoch);
			notify = readinessListeners.toArray(new ReadinessListener[0]);
		}
		for (ReadinessListener listener : notify) listener.onReadinessChanged(available, epoch);
	}

	public enum OpenResult {
		OPENED,
		LOAD_DISPATCHED,
		NOT_READY,
		DISABLED,
		FAILED,
		CANCELLED
	}

	public interface Navigator {
		FutureSupplier<OpenResult> open(int destinationId, BooleanSupplier stillCurrent);

		default FutureSupplier<OpenResult> open(OpenOnCarRequest request,
				BooleanSupplier stillCurrent) {
			if (request.kind() != OpenOnCarKind.OPEN_ADDON) return completed(OpenResult.NOT_READY);
			return open(request.addonId(), stillCurrent);
		}
	}

	public interface ReadinessListener {
		void onReadinessChanged(boolean available, long epoch);
	}
}
