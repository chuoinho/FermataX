package me.app.fermatax.auto.carapps;

import android.content.Context;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.utils.async.FutureSupplier;
import me.app.fermatax.auto.carapps.adb.AdbConnectPort;
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

	/** One short ADB session: find the connect port, run the command, close. */
	static FutureSupplier<Void> startViaAdb(Context ctx, String command) {
		LocalAdb adb = new LocalAdb(ctx);
		return AdbConnectPort.find(ctx, PORT_TIMEOUT_MS)
				.then(port -> adb.runAndClose(port, command))
				.onCompletion((r, err) -> adb.close());
	}
}
