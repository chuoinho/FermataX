package me.aap.fermata.addon;

import androidx.annotation.NonNull;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Optional addon contract used by voice routing without class-name or fragment-id coupling. */
public interface VoiceSearchAddon extends FermataAddon {
	@NonNull
	String getVoiceTarget();

	/** Returns true when the addon accepted and asynchronously owns this search command. */
	default boolean handleVoiceSearch(MainActivityDelegate activity, String query, boolean play) {
		return false;
	}

	default boolean resolveVoiceSelection(MainActivityDelegate activity, String stableId) {
		return false;
	}
}
