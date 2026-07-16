package me.aap.fermata.media.lib;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/** Resolves collection and presentation wrappers to the media item that owns playback. */
public final class PlayableItemResolver {
	private PlayableItemResolver() {
	}

	@NonNull
	public static PlayableItem unwrap(@NonNull PlayableItem item) {
		for (int depth = 0; depth < 16; depth++) {
			PlayableItem next;
			if (item instanceof ExportedItem exported) next = exported.getOrig();
			else if (item instanceof PlayableItemWrapper wrapper) next = wrapper.getItem();
			else return item;

			if (next == item) return item;
			item = next;
		}
		return item;
	}
}
