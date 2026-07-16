package me.aap.fermata.addon.radio;

import java.util.function.LongSupplier;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.RefreshCoordinator;
import me.aap.fermata.media.lib.RefreshCoordinator.Operation;
import me.aap.fermata.media.lib.RefreshCoordinator.Result;
import me.aap.utils.async.FutureSupplier;

final class RadioRefreshCoordinator {
	static final long AUTO_RELOAD_INTERVAL = 10 * 60 * 1000L;
	private final RefreshCoordinator<String> refreshes;

	RadioRefreshCoordinator() {
		refreshes = new RefreshCoordinator<>(AUTO_RELOAD_INTERVAL);
	}

	RadioRefreshCoordinator(LongSupplier clock) {
		refreshes = new RefreshCoordinator<>(AUTO_RELOAD_INTERVAL, clock);
	}

	FutureSupplier<Result<String>> auto(BrowsableItem item, Operation operation) {
		return refreshes.auto(item.getId(), operation);
	}

	FutureSupplier<Result<String>> manual(BrowsableItem item, Operation operation) {
		return refreshes.manual(item.getId(), operation);
	}

	void start() {
		refreshes.start();
	}

	void stop() {
		refreshes.stop();
	}

	void reset() {
		refreshes.reset();
	}
}
