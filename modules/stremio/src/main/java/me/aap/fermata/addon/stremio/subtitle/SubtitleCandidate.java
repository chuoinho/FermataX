package me.aap.fermata.addon.stremio.subtitle;

import java.time.Instant;
import java.util.Objects;

import me.aap.fermata.addon.stremio.integration.StremioSourceLease;
import me.aap.fermata.addon.stremio.net.NetworkConsent;

/** Raw but immutable subtitle input. Its string form intentionally excludes provider data. */
public record SubtitleCandidate(
		String subtitleId,
		String url,
		String languageLabel,
		String providerKey,
		String providerLabel,
		Source source,
		String formatHint,
		long declaredSizeBytes,
		OpaqueHeaderReference requestHeaders,
		StremioSourceLease sourceLease,
		Instant expiresAt) {
	public SubtitleCandidate(String subtitleId, String url, String languageLabel,
			String providerKey, String providerLabel, Source source, String formatHint,
			long declaredSizeBytes, OpaqueHeaderReference requestHeaders, Instant expiresAt) {
		this(subtitleId, url, languageLabel, providerKey, providerLabel, source, formatHint,
				declaredSizeBytes, requestHeaders,
				StremioSourceLease.unbound(providerKey, NetworkConsent.STRICT), expiresAt);
	}

	public SubtitleCandidate {
		Objects.requireNonNull(subtitleId, "subtitleId");
		Objects.requireNonNull(url, "url");
		Objects.requireNonNull(languageLabel, "languageLabel");
		Objects.requireNonNull(providerKey, "providerKey");
		Objects.requireNonNull(providerLabel, "providerLabel");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(sourceLease, "sourceLease");
		Objects.requireNonNull(expiresAt, "expiresAt");
		if (declaredSizeBytes < -1) throw new IllegalArgumentException("Invalid subtitle size");
	}

	@Override
	public String toString() {
		return "SubtitleCandidate[source=" + source + ", url=<redacted>, headers=<redacted>]";
	}

	public enum Source {
		STREAM_EMBEDDED,
		PROVIDER
	}
}
