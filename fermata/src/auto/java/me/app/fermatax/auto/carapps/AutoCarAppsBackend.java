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
		});
		AdbPairing.recoveryScheduler = () -> AdbRecoveryJob.schedule(ctx);
		AdbRecoveryJob.restoreAdbWifi(ctx); // a run killed with wireless debugging on left it on
	}
}
