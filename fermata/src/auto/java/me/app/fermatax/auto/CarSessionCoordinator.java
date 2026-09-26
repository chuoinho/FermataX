package me.app.fermatax.auto;

import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_PROJECTION;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.car.app.connection.CarConnection;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.carfinder.CarLocatorManager;
import me.aap.fermata.diagnostics.DiagnosticEvent;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticScope;
import me.aap.fermata.media.service.AutomotiveRuntimeGate;
import me.aap.fermata.media.service.FermataMediaService;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.log.Log;

/**
 * Unified coordinator for automotive projection lifecycle, connection monitoring, and safe teardown.
 * <p>
 * Single source of truth managing:
 * <ul>
 *     <li>Session startup and runtime gate activation</li>
 *     <li>CarConnection state observation</li>
 *     <li>Safe teardown on genuine disconnection (audio pause, Car Finder GPS saving, UI cleanup)</li>
 *     <li>Protection against premature shutdown while car projection remains physically connected</li>
 * </ul>
 * </p>
 */
public final class CarSessionCoordinator {
	private static final long[] REFRESH_DELAYS_MS = {0L, 500L, 1200L};
	private static final AutoDisconnectController DISCONNECT = new AutoDisconnectController(20000L,
			new AutoDisconnectController.Scheduler() {
				@Override public void schedule(Runnable task, long delay) {
					FermataApplication.get().getHandler().postDelayed(task, delay);
				}
				@Override public void cancel(Runnable task) {
					FermataApplication.get().getHandler().removeCallbacks(task);
				}
			}, CarSessionCoordinator::shutdownCurrentSession);

	private static LiveData<Integer> connectionType;
	private static Observer<Integer> observer;
	private static Context appContext;
	private static int observationGeneration;
	private static int refreshGeneration;
	private static long startedGeneration;

	private CarSessionCoordinator() {
	}

	/** Initializes or binds monitoring to the application context. */
	public static void hostCreated(Context context) {
		start(context);
	}

	public static synchronized void start(Context context) {
		if (connectionType != null) return;
		appContext = context.getApplicationContext();
		if (!replaceObservation(appContext)) scheduleFreshQueries(appContext);
	}

	/** Authoritatively starts or resumes an Android Auto projection generation. */
	public static synchronized boolean sessionStarted() {
		cancelDisconnectRetry();
		long generation = AutomotiveRuntimeGate.forceActive();
		if (startedGeneration == generation) return true;
		startedGeneration = generation;
		Log.i("CarSessionCoordinator: Android Auto projection generation started: ", generation);

		try {
			CarLocatorManager.get().onAutomotiveConnected();
		} catch (Throwable failure) {
			Log.w(failure, "Failed to notify CarLocatorManager of automotive connection");
		}
		try {
			MirrorDisplay.projectionSessionStarted(generation);
		} catch (Throwable failure) {
			Log.e(failure, "Failed to open mirror projection generation");
		}
		try {
			List<String> failures = AddonManager.get().onAutomotiveSessionStarted();
			if (!failures.isEmpty()) Log.w("Automotive addon restart failures: ", failures);
		} catch (Throwable failure) {
			Log.e(failure, "Failed to restart automotive addon runtime");
		}
		return true;
	}

	/** Returns true if any automotive host service or car activity is currently active. */
	public static boolean isHostBound() {
		return CarService.isActive() || MirrorService.isActive() ||
				(MainCarActivity.getActiveInstance() != null);
	}

	/** Handles unbinding of automotive host services with safe projection-aware checks. */
	public static void onAutomotiveHostDisconnected(Context context) {
		if (isHostBound()) {
			Log.d("CarSessionCoordinator: Host service unbound, but other automotive components remain active.");
			return;
		}

		if (AutomotiveConnectionState.get().isProjectionConnected()) {
			Log.i("CarSessionCoordinator: Host service unbound, but projection is still connected. Skipping shutdown.");
			return;
		}

		Log.i("CarSessionCoordinator: Automotive host unbound and projection inactive; initiating clean teardown");
		Context ctx = (appContext != null) ? appContext : context.getApplicationContext();
		if (ctx != null) {
			try {
				CarLocatorManager.get().handleAutomotiveDisconnection(ctx, "host_unbind");
			} catch (Throwable t) {
				Log.w(t, "Failed to anchor parking location on host unbind");
			}
			shutdown(ctx);
		}
	}

	/** Performs a complete, graceful teardown when Android Auto disconnects. */
	public static void shutdown(Context context) {
		cancelDisconnectRetry();
		long generation = AutomotiveRuntimeGate.beginShutdown();
		if (generation < 0L) return;
		Log.i("CarSessionCoordinator: Shutting down FermataX generation ", generation);
		recordDiagnostic("shutdown_started", generation, List.of());

		Context app = context.getApplicationContext();
		List<String> addonFailures = new ArrayList<>();
		List<String> failures;
		try {
			failures = AutoShutdownSequence.run(generation,
					new AutoShutdownSequence.Step("media",
							FermataMediaService::suspendActiveInstanceForAutomotiveDisconnect),
					new AutoShutdownSequence.Step("mirror_surface",
							MirrorServiceFS::shutdownForAutoDisconnect),
					new AutoShutdownSequence.Step("mirror_display", MirrorDisplay::close),
					new AutoShutdownSequence.Step("projection_service", ProjectionService::stop),
					new AutoShutdownSequence.Step("addons",
							() -> addonFailures.addAll(AddonManager.get().onAutomotiveShutdown())),
					new AutoShutdownSequence.Step("car_binding", () -> {
						FermataMediaServiceConnection connection = MainCarActivity.takeServiceForShutdown();
						if (connection != null) connection.disconnect();
					}),
					new AutoShutdownSequence.Step("save_car_location", () ->
							CarLocatorManager.get().onAutomotiveDisconnect(app)),
					new AutoShutdownSequence.Step("phone_ui", MainActivity::finishForAutoDisconnect),
					new AutoShutdownSequence.Step("car_ui", MainCarActivity::finishForAutoDisconnect),
					new AutoShutdownSequence.Step("mirror_ui", MirrorActivity::finishForAutoDisconnect),
					new AutoShutdownSequence.Step("media_service", () ->
							app.stopService(new Intent(app, AutoFermataMediaService.class))),
					new AutoShutdownSequence.Step("xposed_service", () ->
							app.stopService(new Intent(app, XposedEventDispatcherService.class))));
		} finally {
			AutomotiveRuntimeGate.completeShutdown(generation);
		}

		if (!addonFailures.isEmpty()) failures = merge(failures, addonFailures);
		recordDiagnostic("shutdown_completed", generation, failures);
		if (failures.isEmpty()) Log.i("CarSessionCoordinator: Automotive shutdown completed: ", generation);
		else Log.w("CarSessionCoordinator: Automotive shutdown completed with failures: ", failures);
	}

	static void connectionChanged(int expectedObservation, @Nullable Integer type) {
		synchronized (CarSessionCoordinator.class) {
			if ((expectedObservation != observationGeneration) || (type == null)) return;
		}

		boolean isProjection = (type == CONNECTION_TYPE_PROJECTION);
		AutomotiveConnectionState.get().connectionChanged(isProjection);
		boolean projectionAccepted = false;
		if (isProjection) {
			cancelDisconnectRetry();
			projectionAccepted = sessionStarted();
		} else {
			if (isHostBound()) {
				Log.i("CarSessionCoordinator: CarConnection reported disconnected, but CarService/Activity is actively bound. Skipping premature parking anchor.");
			} else {
				Context ctx = appContext;
				if (ctx != null) {
					try {
						CarLocatorManager.get().handleAutomotiveDisconnection(ctx, "car_connection");
					} catch (Throwable t) {
						Log.w(t, "Failed to anchor parking location on CarConnection disconnect");
					}
				}
			}
		}
		DISCONNECT.onConnectionType(type, projectionAccepted);
	}

	private static final int MAX_DISCONNECT_RETRIES = 3;
	private static int disconnectRetryCount;
	private static Runnable pendingRetryTask;

	static synchronized void cancelDisconnectRetry() {
		if (pendingRetryTask != null) {
			FermataApplication.get().getHandler().removeCallbacks(pendingRetryTask);
			pendingRetryTask = null;
		}
		disconnectRetryCount = 0;
	}

	private static synchronized void scheduleDisconnectRetry() {
		if (pendingRetryTask != null) {
			FermataApplication.get().getHandler().removeCallbacks(pendingRetryTask);
		}
		pendingRetryTask = CarSessionCoordinator::shutdownCurrentSession;
		FermataApplication.get().getHandler().postDelayed(pendingRetryTask, 5000L);
	}

	private static void shutdownCurrentSession() {
		synchronized (CarSessionCoordinator.class) {
			pendingRetryTask = null;
		}
		if (isHostBound()) {
			if (disconnectRetryCount < MAX_DISCONNECT_RETRIES) {
				disconnectRetryCount++;
				Log.i("CarSessionCoordinator: Automotive host is still bound during disconnect check (attempt ",
						disconnectRetryCount, "/", MAX_DISCONNECT_RETRIES, "); rescheduling retry");
				scheduleDisconnectRetry();
				return;
			}
			Log.w("CarSessionCoordinator: Max disconnect retries reached while host bound; stopping retry loop");
			disconnectRetryCount = 0;
			return;
		}
		disconnectRetryCount = 0;
		if (AutomotiveConnectionState.get().isProjectionConnected()) {
			Log.i("CarSessionCoordinator: Projection is still connected during disconnect check; skipping shutdown");
			return;
		}
		Context context;
		synchronized (CarSessionCoordinator.class) {
			context = appContext;
		}
		if (context != null) shutdown(context);
	}

	private static void scheduleFreshQueries(Context context) {
		int refresh;
		synchronized (CarSessionCoordinator.class) {
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
		synchronized (CarSessionCoordinator.class) {
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
		synchronized (CarSessionCoordinator.class) {
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
			synchronized (CarSessionCoordinator.class) {
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

	private static List<String> merge(List<String> first, List<String> second) {
		List<String> merged = new ArrayList<>(first.size() + second.size());
		merged.addAll(first);
		merged.addAll(second);
		return List.copyOf(merged);
	}

	private static void recordDiagnostic(String name, long generation, List<String> failures) {
		try {
			DiagnosticPriority priority = failures.isEmpty() ?
					DiagnosticPriority.STATE : DiagnosticPriority.ERROR;
			FermataApplication.get().getDiagnostics().record(
					DiagnosticEvent.builder("automotive_shutdown", name)
							.scope(DiagnosticScope.ESSENTIAL).priority(priority)
							.put("generation", generation)
							.put("failure_count", failures.size())
							.put("failures", String.join(",", failures)).build());
		} catch (Throwable ignored) {
			// Diagnostics must never affect teardown.
		}
	}
}
