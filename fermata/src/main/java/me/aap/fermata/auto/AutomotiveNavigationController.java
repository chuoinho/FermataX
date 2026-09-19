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
		long request;
		long connectionEpoch;
		synchronized (this) {
			captured = navigator;
			if ((captured == null) || !connection.isProjectionConnected()) {
				return completed(OpenResult.NOT_READY);
			}
			registration = registrationGeneration;
			request = ++requestGeneration;
			connectionEpoch = connection.connectionEpoch();
		}
		BooleanSupplier stillCurrent = () -> isCurrent(captured, registration, request,
				connectionEpoch);
		try {
			FutureSupplier<OpenResult> result = captured.open(destinationId, stillCurrent);
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
		NOT_READY,
		DISABLED,
		FAILED,
		CANCELLED
	}

	public interface Navigator {
		FutureSupplier<OpenResult> open(int destinationId, BooleanSupplier stillCurrent);
	}

	public interface ReadinessListener {
		void onReadinessChanged(boolean available, long epoch);
	}
}
