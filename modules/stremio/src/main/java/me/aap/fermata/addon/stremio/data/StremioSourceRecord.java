package me.aap.fermata.addon.stremio.data;

import java.util.Objects;

import me.aap.fermata.addon.stremio.net.NetworkConsent;

public record StremioSourceRecord(
		String sourceUuid,
		String transportFingerprint,
		String addonId,
		String name,
		String version,
		String redactedTransportUrl,
		String secretRef,
		boolean enabled,
		int position,
		String manifestJson,
		String manifestEtag,
		String manifestLastModified,
		long lastCheckedMs,
		long lastSuccessMs,
		String lastErrorCode,
		long installedMs,
		long updatedMs,
		boolean allowCleartext,
		boolean allowLan) {

	public StremioSourceRecord(String sourceUuid, String transportFingerprint,
			String addonId, String name, String version, String redactedTransportUrl,
			String secretRef, boolean enabled, int position, String manifestJson,
			String manifestEtag, String manifestLastModified, long lastCheckedMs,
			long lastSuccessMs, String lastErrorCode, long installedMs, long updatedMs) {
		this(sourceUuid, transportFingerprint, addonId, name, version, redactedTransportUrl,
				secretRef, enabled, position, manifestJson, manifestEtag, manifestLastModified,
				lastCheckedMs, lastSuccessMs, lastErrorCode, installedMs, updatedMs, false, false);
	}

	public StremioSourceRecord {
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(transportFingerprint, "transportFingerprint");
		Objects.requireNonNull(addonId, "addonId");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(redactedTransportUrl, "redactedTransportUrl");
		Objects.requireNonNull(manifestJson, "manifestJson");
		if (sourceUuid.isBlank()) throw new IllegalArgumentException("sourceUuid is blank");
		if (transportFingerprint.isBlank()) {
			throw new IllegalArgumentException("transportFingerprint is blank");
		}
	}

	public StremioSourceRecord withTransport(String fingerprint, String redactedUrl,
			String newSecretRef, long nowMs) {
		return new StremioSourceRecord(sourceUuid, fingerprint, addonId, name, version,
				redactedUrl, newSecretRef, enabled, position, manifestJson, manifestEtag,
				manifestLastModified, lastCheckedMs, lastSuccessMs, lastErrorCode,
				installedMs, nowMs, allowCleartext, allowLan);
	}

	public NetworkConsent networkConsent() {
		return new NetworkConsent(allowCleartext, allowLan);
	}
}
