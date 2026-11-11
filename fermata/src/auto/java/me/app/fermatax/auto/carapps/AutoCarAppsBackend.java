package me.app.fermatax.auto.carapps;

import android.content.Context;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.CarAppsReadinessModel;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.app.fermatax.auto.carapps.adb.AdbPairing;
import me.app.fermatax.auto.carapps.adb.AdbRecoveryJob;

/**
 * Android Auto flavor backend for the Car Apps feature.
 */
public final class AutoCarAppsBackend {

	private AutoCarAppsBackend() {
	}

	public static void install(Context ctx) {
		PhoneAppsJournal.install(new java.io.File(ctx.getFilesDir(), "carapps_journal.txt"));
		CarAppsFeature.setBackend(new CarAppsFeature.Backend() {
			@Override
			public FutureSupplier<Boolean> requestRoot() {
				return RootGrant.request(ctx);
			}

			@Override
			public ActivityFragment createFragment() {
				return new CarAppsFragment();
			}

			@Override
			public int capabilities() {
				CarDisplayClient client = CarDisplayClient.get();
				if (client != null && client.capabilities() != 0) {
					return client.capabilities();
				}
				return CarDisplayClient.savedCapabilities(ctx, CarAppsReadinessModel.CAP_INJECT);
			}

			@Override
			public boolean serverFailed() {
				// offers a new pairing: not for a missing Wi-Fi, and not while driving
				CarDisplayClient client = CarDisplayClient.get();
				return (client.state() == CarDisplayClient.State.FAILED) && !client.failedForWifi()
						&& !me.app.fermatax.auto.CarService.isActive();
			}

			/** Off: the job and the server stop; the pairing and USB debugging stay. On: the usual triggers start it. */
			@Override
			public void onEnabledChanged(boolean enabled) {
				PhoneAppsJournal.record("addon_switch", java.util.Map.of("enabled", enabled));
				if (enabled) return;
				AdbRecoveryJob.cancel(ctx);
				CarDisplayClient.get().stop(ctx);
			}

			@Override
			public String report(Context c) {
				return PhoneAppsReport.build(c);
			}
		});
		AdbPairing.recoveryScheduler = () -> AdbRecoveryJob.schedule(ctx);
		AdbRecoveryJob.restoreAdbWifi(ctx); // a run killed with wireless debugging on left it on
	}
}
