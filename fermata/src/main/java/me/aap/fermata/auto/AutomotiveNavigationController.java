package me.aap.fermata.auto;

import static me.aap.utils.async.Completed.completed;

import java.util.function.BooleanSupplier;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

/** Process-local bridge from the phone controller to the active projected car UI. */
public final class AutomotiveNavigationController {
	private static final AutomotiveNavigationController INSTANCE =
			new AutomotiveNavigationController();
	private Navigator navigator;
	private long registrationGeneration;
	private long requestGeneration;

	AutomotiveNavigationController() {
	}

	public static AutomotiveNavigationController get() {
		return INSTANCE;
	}

	public synchronized void register(Navigator navigator) {
		this.navigator = navigator;
		registrationGeneration++;
		requestGeneration++;
	}

	public synchronized void unregister(Navigator navigator) {
		if (this.navigator != navigator) return;
		this.navigator = null;
		registrationGeneration++;
		requestGeneration++;
	}

	public FutureSupplier<OpenResult> open(int destinationId) {
		Navigator captured;
		long registration;
		long request;
		long connectionEpoch;
		synchronized (this) {
			captured = navigator;
			AutomotiveConnectionState connection = AutomotiveConnectionState.get();
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
		AutomotiveConnectionState connection = AutomotiveConnectionState.get();
		return (navigator == captured) && (registrationGeneration == registration) &&
				(requestGeneration == request) && connection.isProjectionConnected() &&
				(connection.connectionEpoch() == connectionEpoch);
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
}
