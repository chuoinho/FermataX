package me.app.fermatax.auto;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.diagnostics.DiagnosticEvent;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticScope;
import me.aap.fermata.media.service.AutomotiveRuntimeGate;
import me.aap.fermata.media.service.FermataMediaService;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.log.Log;

/** Performs a complete, generation-scoped teardown when projection disconnects. */
final class AutoSessionShutdown {
	private static long startedGeneration;

	private AutoSessionShutdown() {
	}

	/** Returns false while an older generation is still being torn down. */
	static synchronized boolean sessionStarted() {
		long generation = AutomotiveRuntimeGate.projectionConnected();
		if (generation < 0L) return false;
		if (startedGeneration == generation) return true;
		startedGeneration = generation;
		Log.i("Android Auto projection generation started: ", generation);
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

	static void shutdown(Context context) {
		long generation = AutomotiveRuntimeGate.beginShutdown();
		if (generation < 0L) return;
		Log.i("Android Auto disconnected; shutting down FermataX generation ", generation);
		recordDiagnostic("shutdown_started", generation, List.of());

		Context app = context.getApplicationContext();
		List<String> addonFailures = new ArrayList<>();
		List<String> failures;
		try {
			failures = AutoShutdownSequence.run(
					new AutoShutdownSequence.Step("media", FermataMediaService::shutdownActiveInstance),
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
					new AutoShutdownSequence.Step("phone_ui", MainActivity::finishForAutoDisconnect),
					new AutoShutdownSequence.Step("car_ui", MainCarActivity::finishForAutoDisconnect),
					new AutoShutdownSequence.Step("media_service", () ->
							app.stopService(new Intent(app, AutoFermataMediaService.class))),
					new AutoShutdownSequence.Step("xposed_service", () ->
							app.stopService(new Intent(app, XposedEventDispatcherService.class))),
					new AutoShutdownSequence.Step("ui_tasks", () -> finishTasks(app)));
		} finally {
			AutomotiveRuntimeGate.completeShutdown(generation);
		}

		if (!addonFailures.isEmpty()) failures = merge(failures, addonFailures);
		recordDiagnostic("shutdown_completed", generation, failures);
		if (failures.isEmpty()) Log.i("Automotive shutdown completed: ", generation);
		else Log.w("Automotive shutdown completed with failures: ", failures);
	}

	private static void finishTasks(Context app) {
		ActivityManager manager = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
		if (manager == null) return;
		RuntimeException first = null;
		for (ActivityManager.AppTask task : manager.getAppTasks()) {
			try {
				task.finishAndRemoveTask();
			} catch (RuntimeException failure) {
				if (first == null) first = failure;
				Log.d(failure, "Failed to remove automotive UI task");
			}
		}
		if (first != null) throw first;
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
