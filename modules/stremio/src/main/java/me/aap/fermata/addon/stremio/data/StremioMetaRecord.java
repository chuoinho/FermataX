package me.aap.fermata.addon.stremio.data;

import java.util.Objects;

import me.aap.fermata.addon.stremio.security.ArtworkUrlSanitizer;

public record StremioMetaRecord(
		String metaKey,
		String identityScope,
		String type,
		String providerMetaId,
		String canonicalIdentity,
		String name,
		String description,
		String posterUrl,
		String backgroundUrl,
		String logoUrl,
		String releaseInfo,
		long runtimeMs,
		String genresJson,
		long updatedMs) {

	public StremioMetaRecord {
		Objects.requireNonNull(metaKey, "metaKey");
		Objects.requireNonNull(identityScope, "identityScope");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(providerMetaId, "providerMetaId");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		posterUrl = ArtworkUrlSanitizer.sanitize(posterUrl);
		backgroundUrl = ArtworkUrlSanitizer.sanitize(backgroundUrl);
		logoUrl = ArtworkUrlSanitizer.sanitize(logoUrl);
		Objects.requireNonNull(genresJson, "genresJson");
	}
}
