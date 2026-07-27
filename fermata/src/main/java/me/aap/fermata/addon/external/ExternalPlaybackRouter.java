package me.aap.fermata.addon.external;

import java.util.List;
import java.util.function.Predicate;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/** Isolates handler failures while preserving deterministic routing order. */
public final class ExternalPlaybackRouter {
	private ExternalPlaybackRouter() {
	}

	public static FutureSupplier<PlayableItem> route(List<ExternalPlaybackHandler> handlers,
			DefaultMediaLib lib, ExternalPlaybackRequest request) {
		return route(handlers, handler -> true, lib, request);
	}

	public static FutureSupplier<PlayableItem> route(List<ExternalPlaybackHandler> handlers,
			Predicate<ExternalPlaybackHandler> available, DefaultMediaLib lib,
			ExternalPlaybackRequest request) {
		Dispatch dispatch = new Dispatch(handlers, available, lib, request);
		dispatch.next();
		return dispatch;
	}

	private static final class Dispatch extends Promise<PlayableItem> {
		private final List<ExternalPlaybackHandler> handlers;
		private final Predicate<ExternalPlaybackHandler> available;
		private final DefaultMediaLib lib;
		private final ExternalPlaybackRequest request;
		private int index;
		private FutureSupplier<PlayableItem> active;

		private Dispatch(List<ExternalPlaybackHandler> handlers,
				Predicate<ExternalPlaybackHandler> available, DefaultMediaLib lib,
				ExternalPlaybackRequest request) {
			this.handlers = handlers;
			this.available = available;
			this.lib = lib;
			this.request = request;
		}

		private void next() {
			if (isDone()) return;
			if (index >= handlers.size()) {
				complete(null);
				return;
			}

			ExternalPlaybackHandler handler = handlers.get(index++);
			if (!available.test(handler)) {
				next();
				return;
			}
			FutureSupplier<PlayableItem> candidate;
			try {
				candidate = handler.createExternalPlaybackItem(lib, request);
			} catch (Throwable failure) {
				Log.e(failure, "External playback handler failed: ",
						handler.getClass().getName());
				next();
				return;
			}
			if (candidate == null) {
				Log.e("External playback handler returned null future: ",
						handler.getClass().getName());
				next();
				return;
			}

			active = candidate;
			candidate.onCompletion((item, failure) -> {
				active = null;
				if (isDone()) return;
				if (item != null) complete(item);
				else {
					if (failure != null) Log.e(failure, "External playback handler failed: ",
							handler.getClass().getName());
					next();
				}
			});
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (!super.cancel(mayInterruptIfRunning)) return false;
			FutureSupplier<PlayableItem> pending = active;
			if (pending != null) pending.cancel(mayInterruptIfRunning);
			return true;
		}
	}
}
