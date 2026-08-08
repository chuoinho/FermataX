package me.app.fermatax.auto;

import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_PROJECTION;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.car.app.connection.CarConnection;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import me.aap.fermata.FermataApplication;
import me.aap.utils.log.Log;

/** Process-wide observer for the official Android Auto projection connection boundary. */
final class AutoConnectionMonitor {
	private static final long[] REFRESH_DELAYS_MS = {0L, 500L, 1200L};
	private static final AutoDisconnectController DISCONNECT = new AutoDisconnectController(1500L,
			new AutoDisconnectController.Scheduler() {
				@Override public void schedule(Runnable task, long delay) {
					FermataApplication.get().getHandler().postDelayed(task, delay);
				}
				@Override public void cancel(Runnable task) {
					FermataApplication.get().getHandler().removeCallbacks(task);
				}
			}, AutoConnectionMonitor::shutdownCurrentSession);
	private static LiveData<Integer> connectionType;
	private static Observer<Integer> observer;
	private static Context appContext;
	private static int observationGeneration;
	private static int refreshGeneration;

	private AutoConnectionMonitor() {
	}

	static void hostCreated(Context context) {
		start(context);
	}

	static void hostDestroyed(Context context) {
		start(context);
		// Service destruction is not a disconnect signal. It only asks CarConnection for fresh state.
		scheduleFreshQueries(context.getApplicationContext());
	}

	static synchronized void start(Context context) {
		if (connectionType != null) return;
		appContext = context.getApplicationContext();
		if (!replaceObservation(appContext)) scheduleFreshQueries(appContext);
	}

	private static void scheduleFreshQueries(Context context) {
		int refresh;
		synchronized (AutoConnectionMonitor.class) {
			appContext = context.getApplicationContext();
			refresh = ++refreshGeneration;
		}
		for (long delay : REFRESH_DELAYS_MS) {
			FermataApplication.get().getHandler().postDelayed(
					() -> refresh(refresh), delay);
		}
	}

	private static void refresh(int expectedRefresh) {
		Context context;
		synchronized (AutoConnectionMonitor.class) {
			if (refreshGeneration != expectedRefresh) return;
			context = appContext;
		}
		if (context != null) replaceObservation(context);
	}

	private static boolean replaceObservation(Context context) {
		LiveData<Integer> next;
		try {
			next = new CarConnection(context).getType();
		} catch (RuntimeException error) {
			Log.e(error, "Failed to create Android Auto connection monitor");
			return false;
		}
		LiveData<Integer> previous;
		Observer<Integer> previousObserver;
		final int expected;
		Observer<Integer> nextObserver;
		synchronized (AutoConnectionMonitor.class) {
			expected = ++observationGeneration;
			nextObserver = type -> connectionChanged(expected, type);
			previous = connectionType;
			previousObserver = observer;
			connectionType = next;
			observer = nextObserver;
		}
		if ((previous != null) && (previousObserver != null)) {
			try {
				previous.removeObserver(previousObserver);
			} catch (RuntimeException error) {
				Log.d(error, "Failed to replace stale Android Auto connection observer");
			}
		}
		try {
			next.observeForever(nextObserver);
			return true;
		} catch (RuntimeException error) {
			synchronized (AutoConnectionMonitor.class) {
				if (connectionType == next) {
					connectionType = null;
					observer = null;
					observationGeneration++;
				}
			}
			try {
				next.removeObserver(nextObserver);
			} catch (RuntimeException ignored) {
			}
			Log.e(error, "Failed to monitor Android Auto connection state");
			return false;
		}
	}

	private static void connectionChanged(int expectedObservation, @Nullable Integer type) {
		synchronized (AutoConnectionMonitor.class) {
			if ((expectedObservation != observationGeneration) || (type == null)) return;
		}

		boolean projectionAccepted = false;
		if (type == CONNECTION_TYPE_PROJECTION) {
			projectionAccepted = AutoSessionShutdown.sessionStarted();
		}
		DISCONNECT.onConnectionType(type, projectionAccepted);
	}

	private static void shutdownCurrentSession() {
		Context context;
		synchronized (AutoConnectionMonitor.class) {
			context = appContext;
		}
		if (context != null) AutoSessionShutdown.shutdown(context);
	}
}
