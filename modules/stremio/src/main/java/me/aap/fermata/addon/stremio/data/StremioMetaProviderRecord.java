package me.aap.fermata.addon.stremio.data;

import java.util.Objects;

public record StremioMetaProviderRecord(
		String metaKey,
		String sourceUuid,
		String providerMetaId,
		int priority,
		long updatedMs) {

	public StremioMetaProviderRecord {
		Objects.requireNonNull(metaKey, "metaKey");
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(providerMetaId, "providerMetaId");
	}
}
