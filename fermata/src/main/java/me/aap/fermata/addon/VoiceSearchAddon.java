package me.aap.fermata.addon;

import androidx.annotation.NonNull;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Optional addon contract used by voice routing without class-name or fragment-id coupling. */
public interface VoiceSearchAddon extends FermataAddon {
	@NonNull
	String getVoiceTarget();

	default boolean resolveVoiceSelection(MainActivityDelegate activity, String stableId) {
		return false;
	}
}
