package me.aap.fermata.addon.stremio.protocol.response;

import java.util.List;
import java.util.Objects;

public record AddonCatalogResponse(List<StremioAddonCatalogEntry> addons) {
	public AddonCatalogResponse {
		addons = List.copyOf(Objects.requireNonNull(addons, "addons"));
	}
}
