package me.app.fermatax.auto.carapps.adb;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.android.AdbMdns;
import me.aap.fermata.R;
import me.aap.fermata.ui.carapps.RootProbe;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.app.App;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.app.fermatax.auto.carapps.CarDisplayClient;

import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

/**
 * One-time pairing of the app with the phone's own wireless debugging. The 6-digit code is typed into a
 * notification reply field, so the system "Pair with code" dialog can stay open (no split screen needed).
 * No permission is requested: with notifications off the user is only told how to turn them on.
 * The code and the key are never logged or stored.
 */
public final class AdbPairing {
	public enum State {IDLE, NOTIFICATIONS_OFF, WAITING, WORKING, WRONG_CODE, FAILED, DONE}

	private static final String CHANNEL = "adb_pairing";
	private static final int NOTIFICATION_ID = 0x4ad8;
	private static final String KEY_CODE = "code";
	private static final long PAIRING_PORT_TIMEOUT_MS = 8000;
	private static final long CONNECT_PORT_TIMEOUT_MS = 5000;
	/** A receiver may hold the broadcast for about 10 s; the pairing itself continues after it is released. */
	private static final long RECEIVER_HOLD_MS = 9000;

	// Test seams; the defaults are the real implementations.
	static Function<Context, LocalAdb> adbFactory = LocalAdb::new;
	static Function<Context, FutureSupplier<Integer>> pairingPort =
			c -> AdbConnectPort.find(c, AdbMdns.SERVICE_TYPE_TLS_PAIRING, PAIRING_PORT_TIMEOUT_MS);
	static Function<Context, FutureSupplier<Integer>> connectPort = c -> AdbConnectPort.find(c, CONNECT_PORT_TIMEOUT_MS);
	static Consumer<Context> serverStarter = c -> CarDisplayClient.get().ensureRunning(c);
	/** Task 5 sets this to schedule the recovery job; nothing to do until then. */
	public static volatile Runnable recoveryScheduler = () -> {
	};

	private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private static volatile State state = State.IDLE;

	private AdbPairing() {
	}

	public static boolean validCode(@Nullable String code) {
		return (code != null) && code.matches("[0-9]{6}");
	}

	public static State state() {
		return state;
	}

	public static void addListener(Runnable listener) {
		listeners.add(listener);
	}

	public static void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	/** Posts the notification the user types the code into, or reports that notifications are off. Never asks for a permission. */
	public static void start(Context ctx) {
		ctx = ctx.getApplicationContext();
		if (state == State.WORKING) return;
		if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
			setState(State.NOTIFICATIONS_OFF);
			return;
		}
		setState(State.WAITING);
		post(ctx, R.string.car_apps_setup_notif_prompt, true);
	}

	/** Pairs with what the user typed and finishes the setup; the result is in state() and the notification. */
	static FutureSupplier<Void> submit(Context ctx, @Nullable String input) {
		ctx = ctx.getApplicationContext();
		if (!validCode(input)) {
			setState(State.WRONG_CODE);
			post(ctx, R.string.car_apps_setup_wrong_code, true);
			return completedVoid();
		}
		synchronized (AdbPairing.class) {
			if (state == State.WORKING) return completedVoid();
			state = State.WORKING;
		}
		notifyListeners();
		post(ctx, R.string.car_apps_setup_working, false);
		Promise<Void> done = new Promise<>();
		LocalAdb adb = adbFactory.apply(ctx);
		Context app = ctx;
		port(pairingPort, ctx).then(port -> adb.pair(port, input)).onCompletion((r, err) -> {
			if (err != null) {
				adb.close();
				boolean wrongCode = isPairingRequired(err);
				setState(wrongCode ? State.WRONG_CODE : State.FAILED);
				post(app, wrongCode ? R.string.car_apps_setup_wrong_code : R.string.car_apps_setup_failed, true);
				done.complete(null);
				return;
			}
			grantAndFinish(app, adb, done);
		});
		return done;
	}

	/** The grant lets the app switch wireless debugging on by itself later; failing it does not undo the pairing. */
	private static void grantAndFinish(Context ctx, LocalAdb adb, Promise<Void> done) {
		port(connectPort, ctx)
				.then(port -> adb.runAndClose(port,
						"pm grant " + ctx.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS"))
				.onCompletion((r, err) -> {
					adb.close();
					RootProbe.recordAdbPaired(ctx, true);
					recoveryScheduler.run();
					try {
						serverStarter.accept(ctx);
					} catch (Throwable t) {
						Log.e(t, "Cannot start the car display server after pairing");
					}
					setState(State.DONE);
					post(ctx, R.string.car_apps_setup_done, false);
					done.complete(null);
				});
	}

	private static FutureSupplier<Integer> port(Function<Context, FutureSupplier<Integer>> finder, Context ctx) {
		try {
			return finder.apply(ctx);
		} catch (Throwable t) {
			return failed(t);
		}
	}

	private static boolean isPairingRequired(Throwable err) {
		for (int depth = 0; (err != null) && (depth < 8); depth++, err = err.getCause()) {
			if (err instanceof AdbPairingRequiredException) return true;
		}
		return false;
	}

	private static void setState(State s) {
		state = s;
		notifyListeners();
	}

	private static void notifyListeners() {
		for (Runnable l : listeners) l.run();
	}

	static void resetForTesting() {
		adbFactory = LocalAdb::new;
		pairingPort = c -> AdbConnectPort.find(c, AdbMdns.SERVICE_TYPE_TLS_PAIRING, PAIRING_PORT_TIMEOUT_MS);
		connectPort = c -> AdbConnectPort.find(c, CONNECT_PORT_TIMEOUT_MS);
		serverStarter = c -> CarDisplayClient.get().ensureRunning(c);
		recoveryScheduler = () -> {
		};
		listeners.clear();
		state = State.IDLE;
	}

	/** Replaces the one pairing notification: with a reply field while the user can still type, else a plain result. */
	@SuppressLint("MissingPermission") // notifications are checked in start(); the permission is never requested here
	private static void post(Context ctx, int textRes, boolean replyField) {
		NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
		if (!nm.areNotificationsEnabled()) return;
		nm.createNotificationChannel(new NotificationChannel(CHANNEL, ctx.getString(R.string.car_apps_title),
				NotificationManager.IMPORTANCE_LOW));
		NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
				.setSmallIcon(R.drawable.notification)
				.setContentTitle(ctx.getString(R.string.car_apps_setup_notif_title))
				.setContentText(ctx.getString(textRes))
				.setOnlyAlertOnce(true)
				.setAutoCancel(!replyField);
		if (replyField) {
			PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, Receiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE); // the system fills in the reply
			b.setOngoing(true).addAction(new NotificationCompat.Action.Builder(R.drawable.notification,
					ctx.getString(R.string.car_apps_setup_notif_hint), pi)
					.addRemoteInput(new RemoteInput.Builder(KEY_CODE).setLabel(ctx.getString(R.string.car_apps_setup_notif_hint)).build())
					.build());
		} else {
			b.setTimeoutAfter(60000);
		}
		nm.notify(NOTIFICATION_ID, b.build());
	}

	/** Receives the reply typed into the notification. Not exported: only our own PendingIntent reaches it. */
	public static final class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context ctx, Intent intent) {
			Bundle reply = RemoteInput.getResultsFromIntent(intent);
			CharSequence text = (reply == null) ? null : reply.getCharSequence(KEY_CODE);
			PendingResult pending = goAsync();
			AtomicBoolean released = new AtomicBoolean();
			Runnable release = () -> {
				if (released.compareAndSet(false, true)) pending.finish();
			};
			App.get().getScheduler().schedule(release, RECEIVER_HOLD_MS, TimeUnit.MILLISECONDS);
			submit(ctx, (text == null) ? null : text.toString().trim()).onCompletion((r, err) -> release.run());
		}
	}
}
