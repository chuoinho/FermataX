package me.aap.fermata;

import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.media.engine.BitmapCache;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.utils.app.App;
import me.aap.utils.app.NetSplitCompatApp;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.activity.ActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public class FermataApplication extends NetSplitCompatApp {
	private static final long LEGACY_DIAGNOSTIC_WINDOW_MILLIS = 10_000L;
	private static final int MAX_LEGACY_DIAGNOSTICS_PER_WINDOW = 5;
	private FermataVfsManager vfsManager;
	private BitmapCache bitmapCache;
	private volatile SharedPreferenceStore preferenceStore;
	private volatile AddonManager addonManager;
	private int mirroringMode;
	private ServiceConnection eventService;
	private AndroidDiagnosticsRuntime diagnostics;
	private final AtomicLong legacyDiagnosticWindow = new AtomicLong();
	private final AtomicInteger legacyDiagnosticCount = new AtomicInteger();

	public static FermataApplication get() {
		return App.get();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		diagnostics = AndroidDiagnosticsRuntime.install(this);
		diagnostics.recordEssential("application", "application_initializing",
				DiagnosticPriority.STATE, null, null);
		vfsManager = new FermataVfsManager();
		bitmapCache = new BitmapCache();
		diagnostics.recordEssential("application", "application_initialized",
				DiagnosticPriority.STATE, null, null);
	}

	@Override
	public void onTerminate() {
		AndroidDiagnosticsRuntime runtime = AndroidDiagnosticsRuntime.get();
		if (runtime != null) runtime.shutdown();
		diagnostics = null;
		super.onTerminate();
	}

	@Override
	protected void attachBaseContext(Context ctx) {
		var ps = SharedPreferenceStore.create(ctx.getSharedPreferences("fermata", MODE_PRIVATE));
		preferenceStore = ps;
		var loc = MainActivityPrefs.Lang.get(ps.getIntPref(LOCALE)).locale;
		super.attachBaseContext(MainActivityDelegate.createLocaleContext(ctx, loc));
	}

	public boolean isConnectedToAuto() {
		return BuildConfig.AUTO && ActivityDelegate.getContextToDelegate() != null;
	}

	public FermataVfsManager getVfsManager() {
		return vfsManager;
	}

	public BitmapCache getBitmapCache() {
		return bitmapCache;
	}

	public PreferenceStore getPreferenceStore() {
		SharedPreferenceStore ps = preferenceStore;

		if (ps == null) {
			synchronized (this) {
				if ((ps = preferenceStore) == null) {
					preferenceStore =
							ps = SharedPreferenceStore.create(getSharedPreferences("fermata", MODE_PRIVATE));
				}
			}
		}

		return ps;
	}

	public SharedPreferences getDefaultSharedPreferences() {
		return ((SharedPreferenceStore) getPreferenceStore()).getSharedPreferences();
	}

	public AddonManager getAddonManager() {
		AddonManager mgr = addonManager;

		if (mgr == null) {
			synchronized (this) {
				if ((mgr = addonManager) == null) {
					addonManager = mgr = new AddonManager(getPreferenceStore());
				}
			}
		}

		return mgr;
	}

	public AndroidDiagnosticsRuntime getDiagnostics() {
		AndroidDiagnosticsRuntime runtime = AndroidDiagnosticsRuntime.get();
		diagnostics = runtime;
		return runtime;
	}

	@Override
	public void onLogFailure(Log.Level level, @Nullable Throwable error) {
		if ((level == Log.Level.WARN) && (error == null)) return;
		long now = SystemClock.elapsedRealtime();
		long window = legacyDiagnosticWindow.get();
		if ((now - window) >= LEGACY_DIAGNOSTIC_WINDOW_MILLIS &&
				legacyDiagnosticWindow.compareAndSet(window, now)) {
			legacyDiagnosticCount.set(0);
		}
		if (legacyDiagnosticCount.incrementAndGet() > MAX_LEGACY_DIAGNOSTICS_PER_WINDOW) return;
		AndroidDiagnosticsRuntime runtime = getDiagnostics();
		DiagnosticPriority priority = (level == Log.Level.ERROR) ?
				DiagnosticPriority.ERROR : DiagnosticPriority.WARN;
		if (error == null) {
			runtime.recordEssential("legacy_log", "logged_failure", priority, null, null);
		} else {
			runtime.record(me.aap.fermata.diagnostics.DiagnosticEvent
					.builder("legacy_log", "logged_exception")
					.priority(priority)
					.error(error)
					.build());
		}
	}

	@Override
	public void onDiagnosticEvent(String category, String event, Map<String, ?> attributes,
			@Nullable Throwable error) {
		onDiagnosticEvent(category, event, null, attributes, error);
	}

	@Override
	public void onDiagnosticEvent(String category, String event, @Nullable String operationId,
			Map<String, ?> attributes, @Nullable Throwable error) {
		AndroidDiagnosticsRuntime runtime = getDiagnostics();
		var builder = me.aap.fermata.diagnostics.DiagnosticEvent.builder(category, event)
				.operationId(operationId)
				.attributes((attributes == null) ? Collections.emptyMap() : attributes);
		if (error != null) builder.error(error);
		runtime.record(builder.build());
	}

	@Override
	protected int getMaxNumberOfThreads() {
		return 5;
	}

	@Nullable
	@Override
	public File getLogFile() {
		if (!BuildConfig.D) return null;
		File dir = new File(getNoBackupFilesDir(), "legacy");
		if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return null;
		return new File(dir, "Fermata.log");
	}

	@Nullable
	@Override
	public String getCrashReportEmail() {
		return null;
	}

	public boolean isMirroringMode() {
		return BuildConfig.AUTO && getMirroringMode() != 0;
	}

	public boolean isMirroringLandscape() {
		return BuildConfig.AUTO && getMirroringMode() == 1;
	}

	public int getMirroringMode() {
		return mirroringMode;
	}

	public void setMirroringMode(int mirroringMode) {
		if (!BuildConfig.AUTO) return;
		this.mirroringMode = mirroringMode;

		if (mirroringMode == 0) {
			if (eventService != null) {
				unbindService(eventService);
				eventService = null;
			}
		} else if (eventService == null) {
			eventService = new ServiceConnection() {
				@Override
				public void onServiceConnected(ComponentName name, IBinder service) {
					Log.d("Connected to XposedEventDispatcherService");
				}

				@Override
				public void onServiceDisconnected(ComponentName name) {
					Log.d("Disconnected from XposedEventDispatcherService");
				}
			};
			try {
				Log.i("Starting XposedEventDispatcherService");
				var i = new Intent();
				i.setComponent(
						new ComponentName(this, "me.app.fermatax.auto" + ".XposedEventDispatcherService"));
				bindService(i, eventService, Context.BIND_AUTO_CREATE);
			} catch (Exception err) {
				eventService = null;
				Log.e(err, "Failed to bind EventDispatcherService");
			}
		}
	}
}
