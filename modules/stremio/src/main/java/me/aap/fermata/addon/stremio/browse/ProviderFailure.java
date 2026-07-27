package me.aap.fermata.addon.stremio.browse;

import java.util.Objects;

public record ProviderFailure(String sourceUuid, String operation, boolean retryable) {
	public ProviderFailure {
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(operation, "operation");
	}
}
