package me.aap.fermata.addon.stremio.playback;

import java.util.Objects;

import me.aap.fermata.addon.stremio.integration.StremioSourceLease;
import me.aap.fermata.addon.stremio.net.NetworkConsent;

/** Stable provider identity and user-defined ordering. */
public record StreamProvider(
		String sourceUuid, String addonId, String displayName, int position, boolean enabled,
		NetworkConsent networkConsent, long sourceRevision, long sourceUpdatedMs,
		String transportFingerprint, StremioSourceLease sourceLease, String requestId) {
	public StreamProvider(String sourceUuid, String addonId, String displayName,
			int position, boolean enabled) {
		this(sourceUuid, addonId, displayName, position, enabled, NetworkConsent.STRICT,
				-1L, -1L, "", StremioSourceLease.unbound(sourceUuid, NetworkConsent.STRICT), null);
	}

	public StreamProvider(String sourceUuid, String addonId, String displayName,
			int position, boolean enabled, NetworkConsent networkConsent) {
		this(sourceUuid, addonId, displayName, position, enabled, networkConsent,
				-1L, -1L, "", StremioSourceLease.unbound(sourceUuid, networkConsent), null);
	}

	public StreamProvider(String sourceUuid, String addonId, String displayName,
			int position, boolean enabled, NetworkConsent networkConsent, long sourceRevision,
			long sourceUpdatedMs, String transportFingerprint) {
		this(sourceUuid, addonId, displayName, position, enabled, networkConsent,
				sourceRevision, sourceUpdatedMs, transportFingerprint,
				StremioSourceLease.unbound(sourceUuid, networkConsent), null);
	}

	public StreamProvider(String sourceUuid, String addonId, String displayName,
			int position, boolean enabled, NetworkConsent networkConsent, long sourceRevision,
			long sourceUpdatedMs, String transportFingerprint, StremioSourceLease sourceLease) {
		this(sourceUuid, addonId, displayName, position, enabled, networkConsent,
				sourceRevision, sourceUpdatedMs, transportFingerprint, sourceLease, null);
	}

	public StreamProvider {
		StremioPlaybackIdentity.requireText(sourceUuid, "sourceUuid");
		StremioPlaybackIdentity.requireText(addonId, "addonId");
		Objects.requireNonNull(displayName, "displayName");
		if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
		if (position < 0) throw new IllegalArgumentException("position must not be negative");
		Objects.requireNonNull(networkConsent, "networkConsent");
		Objects.requireNonNull(transportFingerprint, "transportFingerprint");
		Objects.requireNonNull(sourceLease, "sourceLease");
		if ((requestId != null) && (requestId.isBlank() || requestId.contains("://") ||
				requestId.indexOf('\u0000') >= 0)) {
			throw new IllegalArgumentException("Invalid provider request ID");
		}
		if ((sourceRevision < -1L) || (sourceUpdatedMs < -1L)) {
			throw new IllegalArgumentException("Invalid provider source revision");
		}
		if ((sourceRevision >= 0L) != (sourceUpdatedMs >= 0L) ||
				(sourceRevision >= 0L) != !transportFingerprint.isBlank()) {
			throw new IllegalArgumentException("Incomplete provider source binding");
		}
	}

	public boolean hasSourceBinding() {
		return sourceRevision >= 0L;
	}

	public String requestIdOr(String fallback) {
		return (requestId == null) ? StremioPlaybackIdentity.requireText(fallback, "fallback") :
				requestId;
	}

	@Override
	public String toString() {
		return "StreamProvider{position=" + position + ", enabled=" + enabled + '}';
	}
}
