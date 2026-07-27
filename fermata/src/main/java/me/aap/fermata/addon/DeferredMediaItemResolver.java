package me.aap.fermata.addon;

import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;

/** Coalesces dynamic-feature delivery while preserving the item-specific resolve operation. */
final class DeferredMediaItemResolver {
	private final Map<String, FutureSupplier<?>> deliveries = new HashMap<>();

	DeferredMediaItemResult resolve(String addonKey, AddonState state,
			Supplier<FutureSupplier<?>> requestDelivery,
			Supplier<FutureSupplier<? extends Item>> resolveLoaded) {
		return switch (state) {
			case DISABLED -> DeferredMediaItemResult.disabled();
			case FAILED -> DeferredMediaItemResult.failed();
			case ENABLED_PENDING, LOADING -> DeferredMediaItemResult.loading(
					getOrRequestDelivery(addonKey, requestDelivery).then(v -> resolveLoaded.get()));
			case LOADED -> DeferredMediaItemResult.notHandled();
		};
	}

	private synchronized FutureSupplier<?> getOrRequestDelivery(String addonKey,
			Supplier<FutureSupplier<?>> requestDelivery) {
		FutureSupplier<?> delivery = deliveries.get(addonKey);
		if (delivery != null) return delivery;

		delivery = requestDelivery.get();
		if (delivery.isDone()) return delivery;

		deliveries.put(addonKey, delivery);
		FutureSupplier<?> pending = delivery;
		delivery.onCompletion((result, error) -> remove(addonKey, pending));
		return delivery;
	}

	private synchronized void remove(String addonKey, FutureSupplier<?> delivery) {
		deliveries.remove(addonKey, delivery);
	}
}
