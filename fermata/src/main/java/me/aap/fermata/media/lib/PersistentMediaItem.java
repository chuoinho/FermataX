package me.aap.fermata.media.lib;

import java.util.Objects;

import me.aap.fermata.media.lib.MediaLib.Item;

/** Optional stable identity used only for durable preferences and process restoration. */
public interface PersistentMediaItem {
	String getPersistentId();

	static String idOf(Item item) {
		Objects.requireNonNull(item, "item");
		String id = (item instanceof PersistentMediaItem persistent) ?
				persistent.getPersistentId() : item.getId();
		if ((id == null) || id.isBlank()) {
			throw new IllegalStateException("Persistent media item ID must not be empty");
		}
		return id;
	}
}
