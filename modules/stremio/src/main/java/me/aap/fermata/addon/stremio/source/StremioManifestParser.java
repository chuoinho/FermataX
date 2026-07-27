package me.aap.fermata.addon.stremio.source;

import me.aap.fermata.addon.stremio.protocol.ManifestValidator;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;

/** Replaceable validator boundary for source-management tests and integration. */
@FunctionalInterface
public interface StremioManifestParser {
	StremioManifest parse(String manifestJson);

	static StremioManifestParser strict() {
		return ManifestValidator::parse;
	}
}
