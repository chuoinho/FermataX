package me.aap.fermata.addon.stremio.subtitle;

import java.util.Objects;

public record SubtitleProviderFailure(String providerKey, Code code) {
	public SubtitleProviderFailure {
		Objects.requireNonNull(providerKey, "providerKey");
		Objects.requireNonNull(code, "code");
	}

	@Override
	public String toString() {
		return "SubtitleProviderFailure[provider=<redacted>, code=" + code + "]";
	}

	public enum Code {
		PROVIDER_FAILED,
		PROVIDER_REMOVED,
		INVALID_SUBTITLE,
		LIMIT_REACHED
	}
}
