package me.app.fermatax.auto.carapps;

import static me.aap.utils.async.Completed.completed;

import android.content.Context;

import java.util.function.Supplier;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootProbe;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.app.fermatax.auto.CarService;
import me.app.fermatax.auto.Su;

/**
 * Handles root grant requests for the Car Apps feature.
 */
public final class RootGrant {

	private RootGrant() {
	}

	public static FutureSupplier<Boolean> request(Context ctx) {
		return request(ctx, Su::get);
	}

	static FutureSupplier<Boolean> request(Context ctx, Supplier<FutureSupplier<Su>> suSupplier) {
		if (CarService.isActive()) {
			return completed(false);
		}

		Promise<Boolean> promise = new Promise<>();
		try {
			FutureSupplier<Su> suFuture = suSupplier.get();
			suFuture.onSuccess(su -> {
				RootProbe.recordGranted(ctx, true);
				promise.complete(true);
			}).onFailure(err -> {
				if (CarAppsFeature.state() != RootState.ABSENT) {
					CarAppsFeature.setState(RootState.AVAILABLE);
				}
				promise.complete(false);
			});
		} catch (Throwable t) {
			if (CarAppsFeature.state() != RootState.ABSENT) {
				CarAppsFeature.setState(RootState.AVAILABLE);
			}
			promise.complete(false);
		}

		return promise;
	}
}
