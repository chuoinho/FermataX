package me.aap.fermata.addon.external;

import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

/** Addon boundary for targets that must be played or displayed by an addon-owned surface. */
public interface ExternalPlaybackHandler extends FermataAddon {
	@NonNull
	ExternalPlaybackTargetKind getExternalPlaybackTargetKind();

	/** Lower values are preferred. Equal priorities are ordered by addon class name. */
	default int getExternalPlaybackPriority() {
		return 1000;
	}

	/**
	 * Returns an immutable playable item, or {@link #unavailable()} when this handler cannot
	 * create one for the request. Implementations must not enable or install another addon.
	 */
	@NonNull
	FutureSupplier<PlayableItem> createExternalPlaybackItem(DefaultMediaLib lib,
			ExternalPlaybackRequest request);

	static FutureSupplier<PlayableItem> unavailable() {
		return completedNull();
	}
}
