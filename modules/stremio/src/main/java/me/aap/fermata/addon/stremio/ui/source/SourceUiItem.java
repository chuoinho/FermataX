package me.aap.fermata.addon.stremio.ui.source;

import java.util.Objects;
import java.util.Set;

/** Secret-free source row rendered by the source-management UI. */
public record SourceUiItem(
		String sourceUuid,
		String name,
		String version,
		String redactedEndpoint,
		boolean enabled,
		int position,
		String lastErrorCode,
		boolean configurable,
		SourceUiConsent consent,
		Set<SourceUiCapability> capabilities) {
	public SourceUiItem {
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(redactedEndpoint, "redactedEndpoint");
		Objects.requireNonNull(consent, "consent");
		capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
		if (sourceUuid.isBlank()) throw new IllegalArgumentException("sourceUuid is blank");
		if (position < 0) throw new IllegalArgumentException("position is negative");
	}

	public SourceUiItem(String sourceUuid, String name, String version,
			String redactedEndpoint, boolean enabled, int position, String lastErrorCode,
			boolean configurable, SourceUiConsent consent) {
		this(sourceUuid, name, version, redactedEndpoint, enabled, position, lastErrorCode,
				configurable, consent, Set.of());
	}

	@Override
	public String toString() {
		return "SourceUiItem[id=" + sourceUuid +
				", enabled=" + enabled + ", configurable=" + configurable +
				", position=" + position + ']';
	}
}
