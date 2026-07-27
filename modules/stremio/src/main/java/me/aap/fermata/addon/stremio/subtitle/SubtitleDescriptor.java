package me.aap.fermata.addon.stremio.subtitle;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import me.aap.fermata.addon.stremio.integration.StremioSourceLease;
import me.aap.fermata.addon.stremio.net.NetworkConsent;

/** Normalized subtitle value. Raw location and headers never appear in diagnostic text. */
public record SubtitleDescriptor(
		String identity,
		String subtitleId,
		URI location,
		String languageLabel,
		SubtitleLanguage language,
		String providerKey,
		String providerLabel,
		SubtitleCandidate.Source source,
		SubtitleFormat format,
		Status status,
		long declaredSizeBytes,
		OpaqueHeaderReference requestHeaders,
		StremioSourceLease sourceLease,
		Instant expiresAt) {
	public SubtitleDescriptor(String identity, String subtitleId, URI location,
			String languageLabel, SubtitleLanguage language, String providerKey,
			String providerLabel, SubtitleCandidate.Source source, SubtitleFormat format,
			Status status, long declaredSizeBytes, OpaqueHeaderReference requestHeaders,
			Instant expiresAt) {
		this(identity, subtitleId, location, languageLabel, language, providerKey,
				providerLabel, source, format, status, declaredSizeBytes, requestHeaders,
				StremioSourceLease.unbound(providerKey, NetworkConsent.STRICT), expiresAt);
	}

	public SubtitleDescriptor {
		Objects.requireNonNull(identity, "identity");
		Objects.requireNonNull(subtitleId, "subtitleId");
		Objects.requireNonNull(location, "location");
		Objects.requireNonNull(languageLabel, "languageLabel");
		Objects.requireNonNull(language, "language");
		Objects.requireNonNull(providerKey, "providerKey");
		Objects.requireNonNull(providerLabel, "providerLabel");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(format, "format");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(sourceLease, "sourceLease");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}

	public boolean isPlayable(Instant now) {
		return (status == Status.READY) && now.isBefore(expiresAt);
	}

	@Override
	public String toString() {
		return "SubtitleDescriptor[identity=" + identity + ", language=" + language.tag() +
				", format=" + format + ", status=" + status +
				", location=<redacted>, headers=<redacted>]";
	}

	public enum Status {
		READY,
		UNSUPPORTED_FORMAT,
		FILE_TOO_LARGE
	}
}
