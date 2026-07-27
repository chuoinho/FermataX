package me.aap.fermata.addon.stremio.protocol.model;

import java.util.Objects;

public record ProviderCapability(StremioManifest manifest, boolean enabled) {
	public ProviderCapability {
		manifest = Objects.requireNonNull(manifest, "manifest");
	}
}
