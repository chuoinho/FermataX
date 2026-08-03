package me.aap.fermata.diagnostics.android;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.diagnostics.DiagnosticEvent;
import me.aap.fermata.diagnostics.DiagnosticConfig;
import me.aap.fermata.diagnostics.DiagnosticOperation;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticRecorder;
import me.aap.fermata.diagnostics.DiagnosticScope;
import me.aap.fermata.pref.DiagnosticsPreferences;

/**
 * Android integration for the local diagnostics core. Installation is process-wide and fail-safe:
 * callers can keep using the returned facade even when initialization was not possible.
 */
public final class AndroidDiagnosticsRuntime {
	private static final String RUNTIME_PREF_IMPORTED_EXIT_FINGERPRINTS =
			"RUNTIME_IMPORTED_EXIT_FINGERPRINTS";
	private static final String RUNTIME_PREF_LAST_EXIT_TIMESTAMP = "RUNTIME_LAST_EXIT_TIMESTAMP";
	private static final int MAX_IMPORTED_EXIT_RECORDS = 32;
	private static final int MAX_EXIT_FINGERPRINTS = 64;
	private static final long JOURNAL_MAX_BYTES = 7L * 1024L * 1024L;
	private static final long CRASH_MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(7);
	private static final long WATCHDOG_INTERVAL_MILLIS = 2500L;
	private static final long WATCHDOG_REPORT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10);
	private static final int WATCHDOG_THREAD_DUMP_CHARS = 16 * 1024;
	private static final Object INSTALL_LOCK = new Object();
	private static final AndroidDiagnosticsRuntime NO_OP = new AndroidDiagnosticsRuntime();
	private static volatile AndroidDiagnosticsRuntime instance = NO_OP;
	private static boolean installationAttempted;
	private static int installationFailures;

	private Application application;
	private DiagnosticsPreferences preferences;
	private DiagnosticRecorder recorder;
	private ScheduledThreadPoolExecutor executor;
	private File crashDirectory;
	private File sessionMarker;
	private String processName;
	private int processId;
	private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
	private LifecycleCallbacks lifecycleCallbacks;
	private DiagnosticsCrashHandler crashHandler;
	private final AtomicBoolean stopped = new AtomicBoolean(true);
	private final AtomicBoolean preferenceSyncQueued = new AtomicBoolean();
	private final AtomicInteger resumedActivities = new AtomicInteger();
	private Handler mainHandler;
	private PowerManager powerManager;
	private MainThreadWatchdogState watchdogState;
	private boolean preferenceListenerRegistered;
	private boolean lifecycleCallbacksRegistered;
	private volatile ScheduledFuture<?> expiryTask;
	private volatile ScheduledFuture<?> watchdogTask;
	private volatile boolean foreground;
	private volatile boolean lastRequestedDetailedEnabled;
	private volatile boolean sessionMarkerOwned;

	private AndroidDiagnosticsRuntime() {
		processName = "unknown";
	}

	private AndroidDiagnosticsRuntime(Application application) {
		this();
		initialize(application);
	}

	private void initialize(Application application) {
		this.application = application;
		preferences = new DiagnosticsPreferences(application);
		lastRequestedDetailedEnabled = preferences.getStore().getSharedPreferences()
				.getBoolean(DiagnosticsPreferences.DETAILED_ENABLED.getName(), false);
		processName = resolveProcessName(application);
		processId = Process.myPid();
		File diagnosticsDirectory = new File(application.getNoBackupFilesDir(), "diagnostics");
		File journalDirectory = new File(diagnosticsDirectory, "journal");
		crashDirectory = new File(diagnosticsDirectory, "crash");
		sessionMarker = new File(new File(diagnosticsDirectory, "state"), "active-session.json");
		recorder = DiagnosticRecorder.builder(journalDirectory)
				.config(DiagnosticConfig.builder().maxTotalBytes(JOURNAL_MAX_BYTES).build())
				.detailedState(preferences::isDetailedEnabled)
				.process(processName, processId)
				.build();
		executor = new ScheduledThreadPoolExecutor(1, task -> {
			Thread thread = new Thread(task, "FermataX-Diagnostics-Android");
			thread.setDaemon(true);
			return thread;
		});
		executor.setRemoveOnCancelPolicy(true);
		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
		stopped.set(false);
		mainHandler = new Handler(Looper.getMainLooper());
		powerManager = (PowerManager) application.getSystemService(Context.POWER_SERVICE);
		watchdogState = new MainThreadWatchdogState(2, WATCHDOG_REPORT_INTERVAL_MILLIS);
		preferenceListener = (store, key) -> {
			if (DiagnosticsPreferences.DETAILED_ENABLED.getName().equals(key) ||
					DiagnosticsPreferences.DETAILED_ENABLED_AT.getName().equals(key) ||
					DiagnosticsPreferences.DETAILED_EXPIRES_AT.getName().equals(key) ||
					DiagnosticsPreferences.DETAILED_ENABLED_ELAPSED.getName().equals(key)) {
				scheduleDetailedPreferenceSync();
			}
		};
		lifecycleCallbacks = new LifecycleCallbacks();
		try {
			preferences.getStore().getSharedPreferences()
					.registerOnSharedPreferenceChangeListener(preferenceListener);
			preferenceListenerRegistered = true;
			application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
			lifecycleCallbacksRegistered = true;
			Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
			crashHandler = new DiagnosticsCrashHandler(crashDirectory, processName, processId,
					recorder::snapshotBreadcrumbs, previous);
			Thread.setDefaultUncaughtExceptionHandler(crashHandler);

			recordEssential("process", "process_started", DiagnosticPriority.STATE, null,
					Collections.singletonMap("sdk", Build.VERSION.SDK_INT));
			executeSafely(() -> {
				syncDetailedState();
				updateSessionMarker();
				collectPreviousProcessExits();
				pruneEmergencyCrashes();
			});
		} catch (Throwable failure) {
			cleanupPartialRuntime();
			if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
			if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
			if (failure instanceof RuntimeException) throw (RuntimeException) failure;
			if (failure instanceof Error) throw (Error) failure;
			throw new IllegalStateException("Diagnostics initialization failed", failure);
		}
	}

	/** Installs one runtime for the current process. Initialization failures become a safe no-op. */
	public static AndroidDiagnosticsRuntime install(Application application) {
		if (application == null) throw new NullPointerException("application");
		synchronized (INSTALL_LOCK) {
			if (installationAttempted) return instance;
			try {
				instance = new AndroidDiagnosticsRuntime(application);
				installationAttempted = true;
				installationFailures = 0;
			} catch (ThreadDeath | VirtualMachineError fatal) {
				throw fatal;
			} catch (Throwable ignored) {
				instance = NO_OP;
				installationAttempted = false;
				int failure = ++installationFailures;
				if (failure <= 3) {
					new Handler(application.getMainLooper()).postDelayed(
							() -> install(application), 1000L << (failure - 1));
				}
			}
			return instance;
		}
	}

	/** Returns the installed facade, or a safe no-op facade before/after failed installation. */
	public static AndroidDiagnosticsRuntime get() {
		return instance;
	}

	@Nullable
	public DiagnosticRecorder getRecorder() {
		return recorder;
	}

	public boolean record(DiagnosticEvent event) {
		return (recorder != null) && !stopped.get() && recorder.record(event);
	}

	public boolean recordEssential(String category, String name, DiagnosticPriority priority,
			@Nullable String operationId, @Nullable Map<String, ?> attributes) {
		if ((recorder == null) || stopped.get()) return false;
		return recorder.recordEssential(category, name, priority, operationId,
				(attributes == null) ? Collections.emptyMap() : attributes);
	}

	@Nullable
	public DiagnosticOperation begin(String category, String operationName,
			@Nullable Map<String, ?> attributes) {
		if ((recorder == null) || stopped.get()) return null;
		return recorder.beginOperation(category, operationName,
				(attributes == null) ? Collections.emptyMap() : attributes);
	}

	public boolean isDetailedEnabled() {
		return (preferences != null) && preferences.isDetailedEnabled(System.currentTimeMillis());
	}

	public void setDetailedEnabled(boolean enabled) {
		if ((preferences == null) || stopped.get()) return;
		lastRequestedDetailedEnabled = enabled;
		preferences.setDetailedEnabled(enabled, System.currentTimeMillis());
		executeSafely(this::syncDetailedState);
	}

	public void clear() {
		if ((recorder == null) || stopped.get()) return;
		recorder.clear();
		executeSafely(this::clearEmergencyCrashes);
	}

	public boolean clear(long timeoutMillis) {
		if ((recorder == null) || stopped.get()) return false;
		boolean result = recorder.clear(timeoutMillis);
		clearEmergencyCrashes();
		return result;
	}

	public boolean flush(long timeoutMillis) {
		return (recorder != null) && !stopped.get() && recorder.flush(timeoutMillis);
	}

	public void shutdown() {
		if (!stopped.compareAndSet(false, true)) return;
		deleteCurrentSessionMarker();
		cleanupPartialRuntime();
	}

	private void cleanupPartialRuntime() {
		ScheduledFuture<?> expiry = expiryTask;
		if (expiry != null) expiry.cancel(false);
		ScheduledFuture<?> watchdog = watchdogTask;
		if (watchdog != null) watchdog.cancel(false);
		if (preferenceListenerRegistered && (preferences != null) &&
				(preferenceListener != null)) {
			try {
				preferences.getStore().getSharedPreferences()
						.unregisterOnSharedPreferenceChangeListener(preferenceListener);
			} catch (RuntimeException ignored) {
				// Best-effort rollback after partial initialization.
			}
			preferenceListenerRegistered = false;
		}
		if (lifecycleCallbacksRegistered && (application != null) &&
				(lifecycleCallbacks != null)) {
			try {
				application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
			} catch (RuntimeException ignored) {
				// Best-effort rollback after partial initialization.
			}
			lifecycleCallbacksRegistered = false;
		}
		if ((crashHandler != null) &&
				(Thread.getDefaultUncaughtExceptionHandler() == crashHandler)) {
			Thread.setDefaultUncaughtExceptionHandler(crashHandler.getPrevious());
		}
		if (executor != null) executor.shutdownNow();
		if (recorder != null) recorder.close();
	}

	public boolean isForeground() {
		return foreground;
	}

	private void syncDetailedState() {
		if (stopped.get()) return;
		long now = System.currentTimeMillis();
		preferences.disableIfExpired(now);
		recorder.setDetailedState(preferences::isDetailedEnabled);
		ScheduledFuture<?> previous = expiryTask;
		if (previous != null) previous.cancel(false);
		long remaining = preferences.getRemainingMillis(now);
		long delay = (remaining <= 0L) ? DetailedDiagnosticsPolicy.NO_SCHEDULE : remaining;
		if (delay == DetailedDiagnosticsPolicy.NO_SCHEDULE) {
			expiryTask = null;
		} else {
			expiryTask = executor.schedule(() -> {
				preferences.disableIfExpired(System.currentTimeMillis());
				syncDetailedState();
			}, delay, TimeUnit.MILLISECONDS);
		}
		syncWatchdogState();
	}

	private void syncRequestedDetailedState() {
		long now = System.currentTimeMillis();
		SharedPreferences raw = preferences.getStore().getSharedPreferences();
		boolean requested = raw.getBoolean(
				DiagnosticsPreferences.DETAILED_ENABLED.getName(), false);
		if (requested != lastRequestedDetailedEnabled) {
			lastRequestedDetailedEnabled = requested;
			preferences.setDetailedEnabled(requested, now);
		} else if (requested && !preferences.isDetailedEnabled(now)) {
			preferences.setDetailedEnabled(true, now);
		}
		syncDetailedState();
	}

	private void scheduleDetailedPreferenceSync() {
		if (!preferenceSyncQueued.compareAndSet(false, true)) return;
		executeSafely(() -> {
			try {
				syncRequestedDetailedState();
			} finally {
				preferenceSyncQueued.set(false);
			}
		});
	}

	private void syncWatchdogState() {
		if (stopped.get()) return;
		if (!isWatchdogEligible()) {
			ScheduledFuture<?> task = watchdogTask;
			if (task != null) task.cancel(false);
			watchdogTask = null;
			watchdogState.deactivate();
			return;
		}
		ScheduledFuture<?> task = watchdogTask;
		if ((task != null) && !task.isCancelled() && !task.isDone()) return;
		watchdogTask = executor.scheduleWithFixedDelay(this::watchdogTick,
				0L, WATCHDOG_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
	}

	private void watchdogTick() {
		boolean eligible = isWatchdogEligible();
		MainThreadWatchdogState.Tick tick =
				watchdogState.onTick(SystemClock.uptimeMillis(), eligible);
		if (!eligible) {
			syncWatchdogState();
			return;
		}
		long probeId = tick.getProbeId();
		mainHandler.post(() -> watchdogState.acknowledge(probeId));
		if (!tick.shouldReportStall()) return;
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("missed_probes", tick.getMissedProbes());
		attributes.put("stalled_for_ms", tick.getStalledForMillis());
		attributes.put("thread_dump", BoundedThreadDump.format(
				Looper.getMainLooper().getThread(), WATCHDOG_THREAD_DUMP_CHARS));
		record(DiagnosticEvent.builder("performance", "suspected_main_thread_stall")
				.scope(DiagnosticScope.DETAILED)
				.priority(DiagnosticPriority.WARN)
				.attributes(attributes)
				.build());
	}

	private boolean isWatchdogEligible() {
		return !stopped.get() && isDetailedEnabled() && foreground &&
				(resumedActivities.get() > 0) && !Debug.isDebuggerConnected() &&
				((powerManager == null) || powerManager.isInteractive());
	}

	private void collectPreviousProcessExits() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
		collectPreviousProcessExitsApi30();
	}

	@RequiresApi(Build.VERSION_CODES.R)
	private void collectPreviousProcessExitsApi30() {
		if (stopped.get()) return;
		try {
			ActivityManager manager =
					(ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
			if (manager == null) return;
			SharedPreferences raw = preferences.getStore().getSharedPreferences();
			Set<String> imported = new HashSet<>(raw.getStringSet(
					RUNTIME_PREF_IMPORTED_EXIT_FINGERPRINTS, Collections.emptySet()));
			List<ApplicationExitInfo> history = new ArrayList<>(
					manager.getHistoricalProcessExitReasons(null, 0, MAX_IMPORTED_EXIT_RECORDS));
			history.sort(Comparator.comparingLong(ApplicationExitInfo::getTimestamp));
			boolean changed = false;
			for (ApplicationExitInfo info : history) {
				String fingerprint = exitFingerprint(info);
				if (imported.contains(fingerprint)) continue;
				ApplicationExitRecord exit = new ApplicationExitRecord(info.getReason(),
						info.getStatus(), info.getImportance(), info.getProcessName(), info.getPss(),
						info.getRss(), info.getTimestamp());
				DiagnosticEvent event = DiagnosticEvent.builder("process", "previous_process_exit")
						.scope(DiagnosticScope.ESSENTIAL)
						.priority(exit.getPriority())
						.attributes(exit.toAttributes())
						.build();
				if (recorder.recordAndSync(event, 3000L)) {
					imported.add(fingerprint);
					changed = true;
				}
			}
			if (changed) {
				List<String> ordered = new ArrayList<>(imported);
				ordered.sort(Comparator.comparingLong(AndroidDiagnosticsRuntime::exitTimestamp));
				if (ordered.size() > MAX_EXIT_FINGERPRINTS) {
					ordered = ordered.subList(ordered.size() - MAX_EXIT_FINGERPRINTS, ordered.size());
				}
				raw.edit().putStringSet(RUNTIME_PREF_IMPORTED_EXIT_FINGERPRINTS,
						new HashSet<>(ordered)).remove(RUNTIME_PREF_LAST_EXIT_TIMESTAMP).commit();
			}
		} catch (RuntimeException error) {
			recorder.recordError("process", "exit_history_collection_failed", null, error,
					Collections.emptyMap());
		}
	}

	@RequiresApi(Build.VERSION_CODES.R)
	private static String exitFingerprint(ApplicationExitInfo info) {
		String process = info.getProcessName();
		return info.getTimestamp() + ":" + info.getReason() + ':' + info.getStatus() + ':' +
				info.getImportance() + ':' + processFingerprint(process);
	}

	private static String processFingerprint(String process) {
		if (process == null) return "none";
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(
					process.getBytes(StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder(12);
			for (int i = 0; i < 6; i++) out.append(String.format(java.util.Locale.US, "%02x", hash[i]));
			return out.toString();
		} catch (NoSuchAlgorithmException impossible) {
			return "unavailable";
		}
	}

	private static long exitTimestamp(String fingerprint) {
		try {
			int delimiter = fingerprint.indexOf(':');
			return Long.parseLong((delimiter < 0) ? fingerprint : fingerprint.substring(0, delimiter));
		} catch (RuntimeException ignored) {
			return 0L;
		}
	}

	private void executeSafely(Runnable task) {
		if ((executor == null) || stopped.get()) return;
		try {
			executor.execute(task);
		} catch (RejectedExecutionException ignored) {
			// Shutdown raced this request.
		}
	}

	private void pruneEmergencyCrashes() {
		File[] files = crashDirectory.listFiles((dir, name) ->
				name.startsWith("crash-") && name.endsWith(".json"));
		if (files == null) return;
		long cutoff = System.currentTimeMillis() - CRASH_MAX_AGE_MILLIS;
		for (File file : files) {
			if (file.lastModified() < cutoff) file.delete();
		}
		files = crashDirectory.listFiles((dir, name) ->
				name.startsWith("crash-") && name.endsWith(".json"));
		if ((files == null) || (files.length <= 8)) return;
		java.util.Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
		for (int i = 8; i < files.length; i++) files[i].delete();
	}

	private void clearEmergencyCrashes() {
		File[] files = crashDirectory.listFiles((dir, name) ->
				name.startsWith("crash-") && name.endsWith(".json"));
		if (files == null) return;
		for (File file : files) file.delete();
	}

	private void updateSessionMarker() {
		if ((sessionMarker == null) || stopped.get()) return;
		if (sessionMarker.isFile()) {
			recordEssential("process", "previous_session_without_shutdown_marker",
					DiagnosticPriority.WARN, null, Collections.emptyMap());
		}
		File directory = sessionMarker.getParentFile();
		if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) return;
		File pending = new File(directory, "active-session.tmp");
		String marker = "{\"schema_version\":1,\"started_at_ms\":" +
				System.currentTimeMillis() + ",\"pid\":" + processId + '}';
		try (FileOutputStream output = new FileOutputStream(pending, false)) {
			output.write(marker.getBytes(StandardCharsets.UTF_8));
			output.flush();
			output.getFD().sync();
			if (sessionMarker.exists() && !sessionMarker.delete()) return;
			if (pending.renameTo(sessionMarker)) sessionMarkerOwned = true;
		} catch (IOException | RuntimeException error) {
			recordErrorSafely("session_marker_write_failed", error);
		} finally {
			if (pending.exists()) pending.delete();
		}
	}

	private void deleteCurrentSessionMarker() {
		if (sessionMarkerOwned && (sessionMarker != null)) sessionMarker.delete();
		sessionMarkerOwned = false;
	}

	private void recordErrorSafely(String name, Throwable error) {
		DiagnosticRecorder current = recorder;
		if (current == null) return;
		try {
			current.recordError("diagnostics", name, null, error, Collections.emptyMap());
		} catch (RuntimeException ignored) {
			// Diagnostics failures remain observational.
		}
	}

	private static String resolveProcessName(Application application) {
		String name = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			name = Application.getProcessName();
		} else {
			try {
				ActivityManager manager =
						(ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
				if ((manager != null) && (manager.getRunningAppProcesses() != null)) {
					for (ActivityManager.RunningAppProcessInfo process :
							manager.getRunningAppProcesses()) {
						if (process.pid == Process.myPid()) {
							name = process.processName;
							break;
						}
					}
				}
			} catch (RuntimeException ignored) {
				// Package name remains a stable fallback on older Android releases.
			}
		}
		return ((name == null) || name.isEmpty()) ? application.getPackageName() : name;
	}

	private final class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
		private final AtomicInteger started = new AtomicInteger();

		@Override
		public void onActivityCreated(Activity activity, Bundle state) {
		}

		@Override
		public void onActivityStarted(Activity activity) {
			if (started.incrementAndGet() == 1) {
				foreground = true;
				recordEssential("lifecycle", "process_foreground", DiagnosticPriority.STATE,
						null, Collections.emptyMap());
			}
		}

		@Override
		public void onActivityResumed(Activity activity) {
			resumedActivities.incrementAndGet();
			executeSafely(AndroidDiagnosticsRuntime.this::syncWatchdogState);
		}

		@Override
		public void onActivityPaused(Activity activity) {
			resumedActivities.updateAndGet(value -> Math.max(0, value - 1));
			executeSafely(AndroidDiagnosticsRuntime.this::syncWatchdogState);
		}

		@Override
		public void onActivityStopped(Activity activity) {
			int count = started.updateAndGet(value -> Math.max(0, value - 1));
			if (count == 0) {
				foreground = false;
				recordEssential("lifecycle", "process_background", DiagnosticPriority.STATE,
						null, Collections.emptyMap());
			}
		}

		@Override
		public void onActivitySaveInstanceState(Activity activity, Bundle state) {
		}

		@Override
		public void onActivityDestroyed(Activity activity) {
		}
	}
}
