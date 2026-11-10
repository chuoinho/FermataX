package me.app.fermatax.auto.carapps;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.utils.async.FutureSupplier;
import me.app.fermatax.auto.carapps.adb.AdbConnectPort;
import me.app.fermatax.auto.carapps.adb.AdbPairing;
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
		LocalAdb adb = new LocalAdb(ctx);
		String start = withGrant(ctx, command);
		return AdbConnectPort.find(ctx, PORT_TIMEOUT_MS)
				.then(port -> adb.runAndClose(port, start))
				.onCompletion((r, err) -> adb.close());
	}
}
