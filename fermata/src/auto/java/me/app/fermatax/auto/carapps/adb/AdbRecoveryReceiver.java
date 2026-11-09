package me.app.fermatax.auto.carapps.adb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot or an app update the server is gone: queue the recovery job. It does nothing unless paired. */
public final class AdbRecoveryReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context ctx, Intent intent) {
		String action = (intent == null) ? null : intent.getAction();
		if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
			AdbRecoveryJob.schedule(ctx);
		}
	}
}
