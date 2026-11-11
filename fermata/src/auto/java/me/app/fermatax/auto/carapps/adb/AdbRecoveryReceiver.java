package me.app.fermatax.auto.carapps.adb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Map;

import me.app.fermatax.auto.carapps.PhoneAppsJournal;

/** After a reboot or an app update the server is gone: queue the recovery job. It does nothing unless paired. */
public final class AdbRecoveryReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context ctx, Intent intent) {
		String action = (intent == null) ? null : intent.getAction();
		boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
		if (boot || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
			PhoneAppsJournal.record("receiver", Map.of("type", boot ? "boot" : "update")); // the line's time is the delivery time
			AdbRecoveryJob.schedule(ctx);
		}
	}
}
