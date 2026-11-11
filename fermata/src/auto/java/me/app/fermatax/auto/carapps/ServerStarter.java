package me.app.fermatax.auto.carapps;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
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
		return findPort(ctx).then(port -> runner.run(ctx, port, start));
	}

	/**
	 * A stale mdns record (wireless debugging that was already on) shows as a port that never appears: then, once, wireless
	 * debugging is switched off and on again so adbd announces itself, and the port is awaited again. The discovery is
	 * running before the first write, so mdnsd is up when adbd announces. Never more than once per start.
	 */
	private static FutureSupplier<Integer> findPort(Context ctx) {
		Promise<Integer> result = new Promise<>();
		portFinder.apply(ctx, PORT_TIMEOUT_MS).onCompletion((port, err) -> {
			if (err == null) {
				result.complete(port);
			} else if (!AdbRecoveryJob.canReRegisterAdbWifi(ctx)) {
				result.completeExceptionally(err);
			} else {
				FutureSupplier<Integer> again = portFinder.apply(ctx, REREGISTER_TIMEOUT_MS);
				AdbRecoveryJob.reRegisterAdbWifi(ctx);
				again.onCompletion((port2, err2) -> {
					if (err2 == null) {
						result.complete(port2);
					} else {
						AdbRecoveryJob.reRegisterFailed(ctx);
						result.completeExceptionally(err2);
					}
				});
			}
		});
		return result;
	}
}
