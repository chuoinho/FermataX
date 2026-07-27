package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/** Resolution result that keeps temporary addon availability separate from a missing item. */
public final class DeferredMediaItemResult {
	private static final DeferredMediaItemResult NOT_HANDLED =
			new DeferredMediaItemResult(DeferredMediaItemState.NOT_HANDLED, completedNull());
	private static final DeferredMediaItemResult DISABLED =
			new DeferredMediaItemResult(DeferredMediaItemState.DISABLED, completedNull());
	private static final DeferredMediaItemResult FAILED =
			new DeferredMediaItemResult(DeferredMediaItemState.FAILED, completedNull());
	private final DeferredMediaItemState state;
	private final FutureSupplier<? extends Item> item;

	private DeferredMediaItemResult(DeferredMediaItemState state,
			FutureSupplier<? extends Item> item) {
		this.state = state;
		this.item = item;
	}

	public static DeferredMediaItemResult notHandled() {
		return NOT_HANDLED;
	}

	public static DeferredMediaItemResult loading(FutureSupplier<? extends Item> item) {
		return new DeferredMediaItemResult(DeferredMediaItemState.LOADING, item);
	}

	public static DeferredMediaItemResult disabled() {
		return DISABLED;
	}

	public static DeferredMediaItemResult failed() {
		return FAILED;
	}

	@NonNull
	public DeferredMediaItemState getState() {
		return state;
	}

	@NonNull
	public FutureSupplier<? extends Item> getItem() {
		return item;
	}

	public boolean isHandled() {
		return state != DeferredMediaItemState.NOT_HANDLED;
	}
}
