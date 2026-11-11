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

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ObjIntConsumer;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.utils.log.Log;
import me.app.fermatax.auto.carapps.CarDisplayClient;
import me.app.fermatax.auto.carapps.PhoneAppsJournal;

import me.app.fermatax.auto.carapps.ServerStarter;

import me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.Action;

import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.next;

/**
 * Starts the server again after a reboot or an app update, on a phone without root: once the phone is on Wi-Fi (metered
 * or not: the command only runs over loopback) it asks CarDisplayClient to start it, which switches wireless debugging
 * on for the command and hands it back.
 * The start itself stays in CarDisplayClient, so a car session and this job can only ever run one command between them.
 * No permission is requested; WRITE_SECURE_SETTINGS was granted by the pairing.
 */
public class AdbRecoveryJob extends JobService {
	public static final int JOB_ID = 0x4ad9;
	/** Holds the user's adb_wifi_enabled while we have changed it; written before the setting, removed after the restore. */
	public static final String PREF_RESTORE = "CAR_APPS_ADB_WIFI_RESTORE";
	public static final String PREF_TRIES = "CAR_APPS_ADB_RECOVERY_TRIES";
	/** Set by the one re-registration of a start, removed with the marker when the start gives the setting back. */
	static final String PREF_REREGISTERED = "CAR_APPS_ADB_WIFI_REREGISTERED";
	private static final String ADB_WIFI = "adb_wifi_enabled";
	private static final String ADB_ENABLED = "adb_enabled";
	private static final int MAX_TRIES = 3;
	/** Four attempts of 12 s port wait plus the 15 s ADB watchdog, and the 7 s of backoff: plus one 10 s re-registration: under 2.5 minutes, a job may run 10. */
	private static final long RUN_TIMEOUT_MS = 150_000;

	public static volatile Function<Context, Object> wifiNetwork = AdbRecoveryJob::currentWifi;
	public static volatile Predicate<Context> wifiCheck = c -> wifiNetwork.apply(c) != null;
	/**
	 * The Wi-Fi network on which Android switched wireless debugging off again right after the app turned it on: one it
	 * does not trust yet, where it shows its own dialog. Not written again there, so no start or retry shows it twice.
	 * shortcut: kept in memory per connection, so a reconnect or a new process may show the dialog once more.
	 */
	public static volatile Object refusedWifi;
	static volatile ObjIntConsumer<Context> adbWifiWriter = AdbRecoveryJob::putAdbWifi;
	public static volatile ObjIntConsumer<Context> usbDebuggingWriter = AdbRecoveryJob::putAdbEnabled;

	private Run run;

	@Override
	public boolean onStartJob(JobParameters params) {
		run = start(getApplicationContext(), retry -> jobFinished(params, retry)); // finish is always posted
		return run != null;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		PhoneAppsJournal.record("recovery_job", Map.of("state", "stopped", "reason", "constraint_lost"));
		if (run != null) run.cancel();
		return true; // the constraint was lost: run again when it is back
	}

	/** Only for a paired phone without usable root, with Phone Apps on; root phones start it through su instead. */
	public static void schedule(Context ctx) {
		if (!eligible()) return;
		try {
			JobScheduler js = ctx.getSystemService(JobScheduler.class);
			if ((js == null) || (js.getPendingJob(JOB_ID) != null)) {
				PhoneAppsJournal.record("recovery_job", Map.of("state", "pending"));
				return;
			}
			// the very network hasWifi() looks for, else a job could run on another network and be spent; a home Wi-Fi
			// flagged metered counts too, since nothing leaves the phone
			NetworkRequest wifi = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
			js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(ctx, AdbRecoveryJob.class))
					.setRequiredNetwork(wifi).build());
			PhoneAppsJournal.record("recovery_job", Map.of("state", "scheduled", "reason", "wifi_network"));
		} catch (Throwable t) {
			Log.e(t, "Cannot schedule the ADB recovery");
			PhoneAppsJournal.record("recovery_job", Map.of("state", "schedule_failed", "error", t.getClass().getSimpleName()));
		}
	}

	/** Phone Apps was turned off: a queued job would start the server again. */
	public static void cancel(Context ctx) {
		try {
			JobScheduler js = ctx.getSystemService(JobScheduler.class);
			if (js != null) js.cancel(JOB_ID);
		} catch (Throwable t) {
			Log.e(t, "Cannot cancel the ADB recovery");
		}
	}

	private static boolean eligible() {
		return CarAppsFeature.isEnabled() && ServerStarter.choose(CarAppsFeature.state(), CarAppsFeature.isAdbPaired(), Build.VERSION.SDK_INT)
				== ServerStarter.Channel.ADB;
	}

	/** Null when there is nothing for the job to do; else finish gets true (try again later) or false, exactly once. */
	@Nullable
	public static Run start(Context ctx, Consumer<Boolean> finish) {
		Context app = ctx.getApplicationContext();
		CarDisplayClient client = CarDisplayClient.get();
		if (!eligible()) {
			PhoneAppsJournal.record("recovery_job", Map.of("state", "skipped", "reason", "not_eligible"));
			return null;
		}
		Action action = action(app, client);
		if (action == Action.RESTORE_ADB_SETTING) {
			restoreAdbWifi(app);
			action = action(app, client);
		}
		boolean runs = (action == Action.ENABLE_ADB) || (action == Action.CONNECT);
		PhoneAppsJournal.record("recovery_job", runs
				? Map.of("state", "ran", "attempt", prefs(app).getInt(PREF_TRIES, 0) + 1, "result", action.name().toLowerCase(java.util.Locale.ROOT))
				: Map.of("state", "skipped", "reason", action.name().toLowerCase(java.util.Locale.ROOT)));
		return runs ? new Run(app, client, finish) : null;
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
		private final Runnable timeout = () -> fail("timeout");

		private Run(Context app, CarDisplayClient client, Consumer<Boolean> finish) {
			this.client = client;
			this.finish = finish;
			this.prefs = prefs(app);
			client.addStateListener(listener);
			handler.postDelayed(timeout, RUN_TIMEOUT_MS);
			client.ensureRunning(app, "recovery_job");
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
				PhoneAppsJournal.record("recovery_job", Map.of("state", "finished", "result", "ready"));
				finish(false);
			} else if (state == CarDisplayClient.State.FAILED) {
				fail("failed");
			}
		}

		/** A revoked authorization has already cleared the pairing (and the client is FAILED): do not try again. */
		private void fail(String why) {
			if (!done.compareAndSet(false, true)) return;
			detach();
			int tries = prefs.getInt(PREF_TRIES, 0) + 1;
			boolean retry = CarAppsFeature.isAdbPaired() && (tries < MAX_TRIES);
			prefs.edit().putInt(PREF_TRIES, retry ? tries : 0).apply();
			PhoneAppsJournal.record("recovery_job", Map.of("state", "finished", "result", why, "attempt", tries, "retry", retry));
			finish(retry);
		}
	}

	/**
	 * Called by the start, inside its single-flight guard. Turns wireless debugging on when the user left it off and the
	 * app may. The marker (the user's value) is on disk before the setting changes, so a kill in between is repaired by
	 * the next startup.
	 */
	public static boolean enableAdbWifi(Context ctx) {
		SharedPreferences prefs = prefs(ctx);
		boolean on = adbWifiOn(ctx);
		if (next(true, false, hasWifi(ctx), on, canWriteSecure(ctx), false) != Action.ENABLE_ADB) return false;
		// A marker already there belongs to this start (an earlier try): its value is the user's, so after a network
		// change or a toggle in between only the setting is switched on again.
		boolean owned = prefs.contains(PREF_RESTORE);
		if (!owned) prefs.edit().putInt(PREF_RESTORE, on ? 1 : 0).commit();
		try {
			adbWifiWriter.accept(ctx, 1);
			recordAdbWifi("enable", on ? 1 : 0, 1, "written");
			return true;
		} catch (RuntimeException e) {
			Log.e(e, "Cannot switch wireless debugging on");
			recordAdbWifi("enable", on ? 1 : 0, 1, "failed");
			if (!owned) prefs.edit().remove(PREF_RESTORE).remove(PREF_REREGISTERED).commit();
			return false;
		}
	}

	/** adb_wifi_enabled before and after each write of the app; the user's value is the marker until it is given back. */
	private static void recordAdbWifi(String state, int from, int to, String result) {
		PhoneAppsJournal.record("adb_wifi", Map.of("state", state, "from", from, "to", to, "result", result));
	}

	/** adbd needs a moment to stop before it is switched on again, else the settings observer sees one change at most. */
	static final long REREGISTER_GAP_MS = 1_500;

	/**
	 * Wireless debugging that is on, whose connect service nobody finds or whose port refuses: Android stops mdnsd while
	 * no one uses it and adbd may have announced itself to the old one, also right after the app switched it on. Once per
	 * start (all its retries), only where the app may write it and Wi-Fi is there. Null when allowed, else why not.
	 */
	@Nullable
	public static String reRegisterBlocker(Context ctx) {
		if (!adbWifiOn(ctx)) return "setting_off";
		if (prefs(ctx).contains(PREF_REREGISTERED)) return "once_per_start";
		if (!canWriteSecure(ctx)) return "no_permission";
		return hasWifi(ctx) ? null : "no_wifi";
	}

	public static boolean canReRegisterAdbWifi(Context ctx) {
		return reRegisterBlocker(ctx) == null;
	}

	/**
	 * Switches wireless debugging off and, after a short gap, on again so adbd announces itself anew. The marker holds the
	 * user's value before the first write (their 1, or the one this start already saved, left as it is), so the usual
	 * restore (or the repair after a kill) ends on their value. Call only after canReRegisterAdbWifi.
	 */
	public static void reRegisterAdbWifi(Context ctx) {
		SharedPreferences prefs = prefs(ctx);
		boolean owned = prefs.contains(PREF_RESTORE);
		SharedPreferences.Editor once = prefs.edit().putBoolean(PREF_REREGISTERED, true);
		if (!owned) once.putInt(PREF_RESTORE, 1);
		once.commit();
		try {
			adbWifiWriter.accept(ctx, 0);
			recordAdbWifi("reregister_off", 1, 0, "written");
		} catch (RuntimeException e) {
			Log.e(e, "Cannot restart wireless debugging");
			recordAdbWifi("reregister_off", 1, 0, "failed");
			if (!owned) prefs.edit().remove(PREF_RESTORE).remove(PREF_REREGISTERED).commit();
			return;
		}
		new Handler(Looper.getMainLooper()).postDelayed(() -> {
			// the start ended inside the gap and gave the user's value back: a 1 now would overwrite their 0
			if (!prefs.contains(PREF_RESTORE)) return;
			try {
				adbWifiWriter.accept(ctx, 1);
				recordAdbWifi("reregister_on", 0, 1, "written");
			} catch (RuntimeException e) {
				Log.e(e, "Cannot switch wireless debugging on again"); // the marker still gives the user's 1 back
				recordAdbWifi("reregister_on", 0, 1, "failed");
			}
		}, REREGISTER_GAP_MS);
	}

	/**
	 * The port was still not found after re-registering. If Android turned wireless debugging off on this Wi-Fi it refused
	 * it: remember that (no retry shows its dialog again) and drop the marker, else the restore would ask for it once more.
	 */
	public static void reRegisterFailed(Context ctx) {
		boolean refused = refusedOnThisWifi(ctx);
		PhoneAppsJournal.record("adb_reregister", Map.of("result", "no_port", "reason", refused ? "refused_wifi" : "unknown"));
		if (refused) prefs(ctx).edit().remove(PREF_RESTORE).remove(PREF_REREGISTERED).commit();
	}

	/**
	 * For a start that switched wireless debugging on and then failed: still on Wi-Fi with the setting off again means
	 * Android refused it on this network (and showed its own dialog). Remembers that network and returns true.
	 */
	public static boolean refusedOnThisWifi(Context ctx) {
		Object net = wifiNetwork.apply(ctx);
		if ((net == null) || adbWifiOn(ctx)) return false;
		refusedWifi = net;
		return true;
	}

	/**
	 * Turns USB debugging on when it is off (or unreadable) and the app may. It is never turned off again, by design: with
	 * it on, adbd outlives wireless debugging, so the server started over ADB is not killed when that is switched off or the
	 * phone leaves Wi-Fi. Never throws. shortcut: on Android 17 adb_enabled may read as off while adbd runs, so this can
	 * write 1 again; harmless, and reading init.svc.adbd (adbRunning) would need a hidden-API call the app does not make.
	 */
	public static void ensureUsbDebugging(Context ctx) {
		if (!canWriteSecure(ctx)) {
			PhoneAppsJournal.record("usb_debugging", Map.of("result", "no_permission"));
			return;
		}
		try {
			if (Settings.Global.getInt(ctx.getContentResolver(), ADB_ENABLED, -1) == 1) return; // on: nothing to note
			usbDebuggingWriter.accept(ctx, 1);
			PhoneAppsJournal.record("usb_debugging", Map.of("result", "written"));
		} catch (RuntimeException e) {
			Log.e(e, "Cannot switch USB debugging on");
			PhoneAppsJournal.record("usb_debugging", Map.of("result", "failed", "error", e.getClass().getSimpleName()));
		}
	}

	/** Gives the user's value back and only then removes the marker; with the write refused the marker stays. */
	public static void restoreAdbWifi(Context ctx) {
		SharedPreferences prefs = prefs(ctx);
		if (!prefs.contains(PREF_RESTORE) || !canWriteSecure(ctx)) return;
		int from = adbWifiOn(ctx) ? 1 : 0;
		int to = prefs.getInt(PREF_RESTORE, 0);
		try {
			adbWifiWriter.accept(ctx, to);
		} catch (RuntimeException e) {
			Log.e(e, "Cannot restore wireless debugging");
			recordAdbWifi("restore", from, to, "failed");
			return;
		}
		recordAdbWifi("restore", from, to, "written");
		prefs.edit().remove(PREF_RESTORE).remove(PREF_REREGISTERED).commit();
	}

	/** A network where Android refused wireless debugging counts as none, until the user allows it there. */
	public static boolean hasWifi(Context ctx) {
		if (!wifiCheck.test(ctx)) return false;
		Object refused = refusedWifi;
		return (refused == null) || adbWifiOn(ctx) || !refused.equals(wifiNetwork.apply(ctx));
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

	/** The only writer of adb_enabled, and only ever called with 1. */
	static void putAdbEnabled(Context ctx, int value) {
		Settings.Global.putInt(ctx.getContentResolver(), ADB_ENABLED, value);
	}

	/** Wireless debugging needs a Wi-Fi connection, so only Wi-Fi counts; a VPN on top must not hide it. */
	@SuppressWarnings("deprecation")
	@Nullable
	private static Network currentWifi(Context ctx) {
		ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
		if (cm == null) return null;
		for (Network n : cm.getAllNetworks()) {
			NetworkCapabilities caps = cm.getNetworkCapabilities(n);
			if ((caps != null) && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return n;
		}
		return null;
	}
}
