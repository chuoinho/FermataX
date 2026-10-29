package me.app.fermatax.auto.carapps;

import android.content.Context;
import android.content.SharedPreferences;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.CarAppsReadinessModel;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

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
				SharedPreferences prefs = ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
				return prefs.getInt("CAR_APPS_CAPABILITIES",
						CarAppsReadinessModel.CAP_INJECT | CarAppsReadinessModel.CAP_PANEL_POWER);
			}
		});
	}
}
