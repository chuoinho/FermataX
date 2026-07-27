package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;

public record StremioAddonCatalogEntry(
		String transportName,
		String transportUrl,
		StremioManifest manifest,
		boolean official,
		boolean protectedAddon) {
	public StremioAddonCatalogEntry {
		if (Objects.requireNonNull(transportName, "transportName").isBlank()) {
			throw new IllegalArgumentException("transportName cannot be blank");
		}
		if (Objects.requireNonNull(transportUrl, "transportUrl").isBlank()) {
			throw new IllegalArgumentException("transportUrl cannot be blank");
		}
		Objects.requireNonNull(manifest, "manifest");
	}

	@Override
	public String toString() {
		return "StremioAddonCatalogEntry[transportName=" + transportName +
				", transportUrl=<redacted>, manifest=" + manifest.id() +
				", official=" + official + ", protected=" + protectedAddon + ']';
	}
}
