package me.aap.fermata.addon.stremio.session;

import java.util.Objects;

public record StremioItemResolution(
		StremioItemAvailability availability,
		StremioSessionItem item) {

	public StremioItemResolution {
		Objects.requireNonNull(availability, "availability");
		if ((availability == StremioItemAvailability.ITEM_MISSING) != (item == null)) {
			throw new IllegalArgumentException("Only missing resolutions omit the item");
		}
	}

	public static StremioItemResolution missing() {
		return new StremioItemResolution(StremioItemAvailability.ITEM_MISSING, null);
	}

	public boolean isAvailable() {
		return availability == StremioItemAvailability.AVAILABLE;
	}
}
