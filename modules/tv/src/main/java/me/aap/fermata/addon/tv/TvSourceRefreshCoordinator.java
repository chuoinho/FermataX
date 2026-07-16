package me.aap.fermata.addon.tv;

import java.util.function.LongSupplier;

import me.aap.fermata.media.lib.RefreshCoordinator;
import me.aap.fermata.media.lib.RefreshCoordinator.Result;
import me.aap.utils.async.FutureSupplier;

final class TvSourceRefreshCoordinator {
	static final long AUTO_RELOAD_INTERVAL = 10 * 60 * 1000L;
	private final RefreshCoordinator<String> refreshes;

	TvSourceRefreshCoordinator() {
		refreshes = new RefreshCoordinator<>(AUTO_RELOAD_INTERVAL);
	}

	TvSourceRefreshCoordinator(LongSupplier clock) {
		refreshes = new RefreshCoordinator<>(AUTO_RELOAD_INTERVAL, clock);
	}

	FutureSupplier<Result<String>> auto(TvSourceItem source) {
		return refreshes.auto(source.getId(), source::refresh);
	}

	FutureSupplier<Result<String>> manual(TvSourceItem source) {
		return refreshes.manual(source.getId(), source::refresh);
	}

	FutureSupplier<Result<String>> edited(TvSourceItem source) {
		return refreshes.replace(source.getId(), source::refresh);
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
