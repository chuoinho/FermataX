package me.aap.fermata.addon.radio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.media.lib.MediaLib.Item;

/**
 * Pure merger for search results: keeps local results first, then appends remote results
 * deduplicating by station UUID.
 */
public final class RadioSearchResultMerger {

	private RadioSearchResultMerger() {
	}

	@NonNull
	public static List<Item> merge(@Nullable List<? extends Item> local, @Nullable List<? extends Item> remote) {
		List<Item> result = new ArrayList<>();
		Set<String> seenUuids = new LinkedHashSet<>();

		if (local != null) {
			for (Item item : local) {
				if (item == null) continue;
				String uuid = extractUuid(item);
				if (uuid != null) {
					seenUuids.add(uuid);
				}
				result.add(item);
			}
		}

		if (remote != null) {
			for (Item item : remote) {
				if (item == null) continue;
				String uuid = extractUuid(item);
				if (uuid != null && !seenUuids.add(uuid)) {
					// Duplicate station found, skip remote duplicate
					continue;
				}
				result.add(item);
			}
		}

		return result;
	}

	@Nullable
	private static String extractUuid(@NonNull Item item) {
		if (item instanceof RadioStationItem rsi) {
			RadioBrowserStation station = rsi.getStation();
			if (station != null && station.uuid != null && !station.uuid.isEmpty()) {
				return station.uuid;
			}
		}
		return null;
	}
}
