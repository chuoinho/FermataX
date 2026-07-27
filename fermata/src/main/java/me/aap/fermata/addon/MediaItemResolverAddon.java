package me.aap.fermata.addon;

import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

import static me.aap.utils.async.Completed.completed;

/** Resolves persisted item IDs without contributing a root to the media browser tree. */
public interface MediaItemResolverAddon extends FermataAddon {

	@Nullable
	FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme, String id);

	/** True when a missing item is temporarily unavailable and its persisted ID must be retained. */
	default FutureSupplier<Boolean> shouldRetainMissingItem(DefaultMediaLib lib,
			@Nullable String scheme, String id) {
		return completed(false);
	}

	/** Keeps resolver-owned persistence in sync with global Favorites. */
	default void onFavoriteChanged(PlayableItem item, boolean favorite) {
	}
}
