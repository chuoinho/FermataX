package me.app.fermatax.auto.carapps;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import me.app.fermatax.auto.carapps.adb.AdbConnectPort;
import me.app.fermatax.auto.carapps.adb.AdbPairing;
import me.app.fermatax.auto.carapps.adb.AdbRecoveryJob;
import me.app.fermatax.auto.carapps.adb.LocalAdb;

/**
 * Picks how the CarDisplayServer is started: through root, or through the app's own paired loopback ADB.
 */
public final class ServerStarter {

	public enum Channel {
		ROOT,
		ADB,
		NONE
	}

	/**
	 * Wireless debugging that was already on announces itself within a second or two, but one the app has just switched
	 * on may need several. The 15 s ADB watchdog starts only after the port is known, so one attempt is bounded by the
	 * sum of the two.
	 */
	static final long PORT_TIMEOUT_MS = 12_000;

	/** After one re-registration of wireless debugging that was already on: the gap to switch it on again plus its announcement. */
	static final long REREGISTER_TIMEOUT_MS = 10_000;

	/** Pause before the second look when the first answer was the port that was just refused. */
	static final long REREGISTER_GAP_AFTER_MS = 2_000;

	interface Runner {
		FutureSupplier<Void> run(Context ctx, int port, String command);
	}

	// replaced by the tests: there is no NSD and no loopback adbd on the host
	static volatile java.util.function.BiFunction<Context, Long, FutureSupplier<Integer>> portFinder = AdbConnectPort::find;
	static volatile Runner runner = (ctx, port, command) -> {
		LocalAdb adb = new LocalAdb(ctx);
		return adb.runAndClose(port, command).onCompletion((r, err) -> adb.close());
	};

	private ServerStarter() {
	}

	public static Channel choose(@Nullable RootState root, boolean adbPaired, int sdk) {
		if (CarAppsFeature.usable(root)) return Channel.ROOT;
		return CarAppsFeature.canRun(root, adbPaired, sdk) ? Channel.ADB : Channel.NONE;
	}

	/**
	 * Pairing runs "pm grant" once; if that failed the app could never switch debugging on. With the permission missing
	 * the grant goes in front of the start command in the same shell session: one connection, one retry per start, and
	 * ";" so that a failed grant (output discarded) cannot stop the start.
	 */
	static String withGrant(Context ctx, String command) {
		if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
			return command;
		return AdbPairing.grantCommand(ctx.getPackageName()) + " >/dev/null 2>&1; " + command;
	}

	/** One short ADB session: find the connect port, run the command, close. */
	static FutureSupplier<Void> startViaAdb(Context ctx, String command) {
		String start = withGrant(ctx, command);
		Promise<Void> result = new Promise<>();
		AtomicBoolean toggled = new AtomicBoolean(); // one re-registration per start, whichever failure asks for it
		findPort(ctx, toggled).onCompletion((port, err) -> {
			if (err != null) {
				result.complete(null, err);
				return;
			}
			runner.run(ctx, port, start).onCompletion((r, runErr) -> {
				// A refused loopback connection: the port came from a stale mdns cache while adbd listens elsewhere. The
				// command never ran, so one fresh port and one more run do not start anything twice.
				if ((runErr == null) || toggled.get() || !isRefused(runErr) || !AdbRecoveryJob.canReRegisterAdbWifi(ctx)) {
					result.complete(r, runErr);
					return;
				}
				toggled.set(true);
				reRegisterAndFind(ctx, port).onCompletion((port2, err2) -> {
					if (err2 != null) {
						result.complete(null, err2);
						return;
					}
					runner.run(ctx, port2, start).onCompletion((r2, runErr2) -> result.complete(r2, runErr2));
				});
			});
		});
		return result;
	}

	/** Connection refused (not a pairing, TLS or timeout failure) anywhere in the chain. */
	static boolean isRefused(Throwable err) {
		boolean refused = false;
		for (int depth = 0; (err != null) && (depth < 8); depth++, err = err.getCause()) {
			if (err instanceof AdbPairingRequiredException) return false;
			if ((err instanceof java.net.ConnectException)
					|| ((err.getMessage() != null) && err.getMessage().contains("ECONNREFUSED"))) refused = true;
		}
		return refused;
	}

	/**
	 * A stale mdns record (wireless debugging that was already on) shows as a port that never appears: then, once, wireless
	 * debugging is switched off and on again so adbd announces itself, and the port is awaited again.
	 */
	private static FutureSupplier<Integer> findPort(Context ctx, AtomicBoolean toggled) {
		Promise<Integer> result = new Promise<>();
		portFinder.apply(ctx, PORT_TIMEOUT_MS).onCompletion((port, err) -> {
			if (err == null) {
				result.complete(port);
			} else if (!AdbRecoveryJob.canReRegisterAdbWifi(ctx)) {
				result.completeExceptionally(err);
			} else {
				toggled.set(true);
				reRegisterAndFind(ctx, -1).onCompletion((port2, err2) -> result.complete(port2, err2));
			}
		});
		return result;
	}

	/**
	 * The discovery is running before the first write, so mdnsd is up when adbd announces. A port equal to "stale" (the
	 * one just refused) is the system's cache answering before adbd has announced again: one more look after a pause.
	 */
	private static FutureSupplier<Integer> reRegisterAndFind(Context ctx, int stale) {
		Promise<Integer> result = new Promise<>();
		FutureSupplier<Integer> again = portFinder.apply(ctx, REREGISTER_TIMEOUT_MS);
		AdbRecoveryJob.reRegisterAdbWifi(ctx);
		again.onCompletion((port, err) -> {
			if ((err == null) && (port == stale)) {
				new Handler(Looper.getMainLooper()).postDelayed(() -> portFinder.apply(ctx, REREGISTER_TIMEOUT_MS - 2_000)
						.onCompletion((port2, err2) -> finish(ctx, result, port2, err2, stale)), REREGISTER_GAP_AFTER_MS);
			} else {
				finish(ctx, result, port, err, stale);
			}
		});
		return result;
	}

	private static void finish(Context ctx, Promise<Integer> result, Integer port, Throwable err, int stale) {
		Throwable failure = (err != null) ? err : (port == stale) ? new java.util.concurrent.TimeoutException("stale port") : null;
		if (failure != null) AdbRecoveryJob.reRegisterFailed(ctx);
		result.complete(failure == null ? port : null, failure);
	}
}
