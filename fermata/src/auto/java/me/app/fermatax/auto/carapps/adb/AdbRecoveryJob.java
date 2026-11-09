package me.app.fermatax.auto.carapps.adb;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ObjIntConsumer;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.utils.log.Log;
import me.app.fermatax.auto.carapps.CarDisplayClient;
import me.app.fermatax.auto.carapps.ServerStarter;

import me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.Action;

import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.next;

/**
 * Starts the server again after a reboot or an app update, on a phone without root: once the phone is on an unmetered
 * network it asks CarDisplayClient to start it, which switches wireless debugging on for the command and hands it back.
 * The start itself stays in CarDisplayClient, so a car session and this job can only ever run one command between them.
 * No permission is requested; WRITE_SECURE_SETTINGS was granted by the pairing.
 */
public class AdbRecoveryJob extends JobService {
	public static final int JOB_ID = 0x4ad9;
	/** Holds the user's adb_wifi_enabled while we have changed it; written before the setting, removed after the restore. */
	public static final String PREF_RESTORE = "CAR_APPS_ADB_WIFI_RESTORE";
	public static final String PREF_TRIES = "CAR_APPS_ADB_RECOVERY_TRIES";
	private static final String ADB_WIFI = "adb_wifi_enabled";
	private static final int MAX_TRIES = 3;
	/** Four attempts of 12 s port wait plus the 15 s ADB watchdog, and the 7 s of backoff: under 2 minutes, a job may run 10. */
	private static final long RUN_TIMEOUT_MS = 120_000;

	public static volatile Predicate<Context> wifiCheck = AdbRecoveryJob::wifiConnected;
	static volatile ObjIntConsumer<Context> adbWifiWriter = AdbRecoveryJob::putAdbWifi;

	private Run run;

	@Override
	public boolean onStartJob(JobParameters params) {
		run = start(getApplicationContext(), retry -> jobFinished(params, retry)); // finish is always posted
		return run != null;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		if (run != null) run.cancel();
		return true; // the constraint was lost: run again when it is back
	}

	/** Only for a paired phone without usable root; root phones start the server through su and never need this. */
	public static void schedule(Context ctx) {
		if (!eligible()) return;
		try {
			JobScheduler js = ctx.getSystemService(JobScheduler.class);
			if ((js == null) || (js.getPendingJob(JOB_ID) != null)) return;
			// the very network hasWifi() looks for, else a job could run on another unmetered network and be spent
			NetworkRequest wifi = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
					.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).build();
			js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(ctx, AdbRecoveryJob.class))
					.setRequiredNetwork(wifi).build());
		} catch (Throwable t) {
			Log.e(t, "Cannot schedule the ADB recovery");
		}
	}

	private static boolean eligible() {
		return ServerStarter.choose(CarAppsFeature.state(), CarAppsFeature.isAdbPaired(), Build.VERSION.SDK_INT)
				== ServerStarter.Channel.ADB;
	}

	/** Null when there is nothing for the job to do; else finish gets true (try again later) or false, exactly once. */
	@Nullable
	public static Run start(Context ctx, Consumer<Boolean> finish) {
		Context app = ctx.getApplicationContext();
		CarDisplayClient client = CarDisplayClient.get();
		if (!eligible()) return null;
		Action action = action(app, client);
		if (action == Action.RESTORE_ADB_SETTING) {
			restoreAdbWifi(app);
			action = action(app, client);
		}
		return (action == Action.ENABLE_ADB || action == Action.CONNECT) ? new Run(app, client, finish) : null;
	}

	private static Action action(Context app, CarDisplayClient client) {
		CarDisplayClient.State state = client.state();
		// from its first command to its end (also between two retries) a start owns the marker and gives the setting back
		boolean starting = (state == CarDisplayClient.State.STARTING) || (state == CarDisplayClient.State.RETRYING);
		boolean pending = prefs(app).contains(PREF_RESTORE) && !starting;
		return next(true, state == CarDisplayClient.State.READY, hasWifi(app), adbWifiOn(app), canWriteSecure(app), pending);
	}

	/** Waits for the outcome of the one start in flight; the retries stay the client's. */
	public static final class Run {
		private final CarDisplayClient client;
		private final Consumer<Boolean> finish;
		private final SharedPreferences prefs;
		private final Handler handler = new Handler(Looper.getMainLooper());
		private final AtomicBoolean done = new AtomicBoolean();
		private final Consumer<CarDisplayClient.State> listener = this::onState;
		private final Runnable timeout = this::fail;

		private Run(Context app, CarDisplayClient client, Consumer<Boolean> finish) {
			this.client = client;
			this.finish = finish;
			this.prefs = prefs(app);
			client.addStateListener(listener);
			handler.postDelayed(timeout, RUN_TIMEOUT_MS);
			client.ensureRunning(app);
		}

		public void cancel() {
			if (done.compareAndSet(false, true)) detach();
		}

		private void detach() {
			client.removeStateListener(listener);
			handler.removeCallbacks(timeout);
		}

		/** Posted: the outcome can be known inside the constructor, before onStartJob has returned true. */
		private void finish(boolean retry) {
			handler.post(() -> finish.accept(retry));
		}

		private void onState(CarDisplayClient.State state) {
			if (state == CarDisplayClient.State.READY) {
				if (!done.compareAndSet(false, true)) return;
				detach();
				prefs.edit().putInt(PREF_TRIES, 0).apply();
				finish(false);
			} else if (state == CarDisplayClient.State.FAILED) {
				fail();
			}
		}

		/** A revoked authorization has already cleared the pairing (and the client is FAILED): do not try again. */
		private void fail() {
			if (!done.compareAndSet(false, true)) return;
			detach();
			int tries = prefs.getInt(PREF_TRIES, 0) + 1;
			boolean retry = CarAppsFeature.isAdbPaired() && (tries < MAX_TRIES);
			prefs.edit().putInt(PREF_TRIES, retry ? tries : 0).apply();
			finish(retry);
		}
	}

	/**
	 * Called by the start, inside its single-flight guard. Turns wireless debugging on when the user left it off and the
	 * app may. The marker (the user's value) is on disk before the setting changes, so a kill in between is repaired by
	 * the next startup.
	 */
	public static void enableAdbWifi(Context ctx) {
		SharedPreferences prefs = prefs(ctx);
		boolean on = adbWifiOn(ctx);
		if (next(true, false, hasWifi(ctx), on, canWriteSecure(ctx), false) != Action.ENABLE_ADB) return;
		// A marker already there belongs to this start (an earlier try): its value is the user's, so after a network
		// change or a toggle in between only the setting is switched on again.
		boolean owned = prefs.contains(PREF_RESTORE);
		if (!owned) prefs.edit().putInt(PREF_RESTORE, on ? 1 : 0).commit();
		try {
			adbWifiWriter.accept(ctx, 1);
		} catch (RuntimeException e) {
			Log.e(e, "Cannot switch wireless debugging on");
			if (!owned) prefs.edit().remove(PREF_RESTORE).commit();
		}
	}

	/** Gives the user's value back and only then removes the marker; with the write refused the marker stays. */
	public static void restoreAdbWifi(Context ctx) {
		SharedPreferences prefs = prefs(ctx);
		if (!prefs.contains(PREF_RESTORE) || !canWriteSecure(ctx)) return;
		try {
			adbWifiWriter.accept(ctx, prefs.getInt(PREF_RESTORE, 0));
		} catch (RuntimeException e) {
			Log.e(e, "Cannot restore wireless debugging");
			return;
		}
		prefs.edit().remove(PREF_RESTORE).commit();
	}

	public static boolean hasWifi(Context ctx) {
		return wifiCheck.test(ctx);
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
	}

	private static boolean canWriteSecure(Context ctx) {
		return ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
	}

	private static boolean adbWifiOn(Context ctx) {
		return Settings.Global.getInt(ctx.getContentResolver(), ADB_WIFI, 0) == 1;
	}

	static void putAdbWifi(Context ctx, int value) {
		Settings.Global.putInt(ctx.getContentResolver(), ADB_WIFI, value);
	}

	/** Wireless debugging needs a Wi-Fi connection, so only Wi-Fi counts; a VPN on top must not hide it. */
	@SuppressWarnings("deprecation")
	private static boolean wifiConnected(Context ctx) {
		ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
		if (cm == null) return false;
		for (Network n : cm.getAllNetworks()) {
			NetworkCapabilities caps = cm.getNetworkCapabilities(n);
			if ((caps != null) && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
		}
		return false;
	}
}
