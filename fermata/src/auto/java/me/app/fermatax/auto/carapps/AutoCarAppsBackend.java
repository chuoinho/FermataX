package me.app.fermatax.auto.carapps;

import android.content.Context;

import me.aap.fermata.ui.carapps.CarAppsFeature;
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
		});
	}
}
