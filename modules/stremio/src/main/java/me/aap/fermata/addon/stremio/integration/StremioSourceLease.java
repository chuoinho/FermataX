package me.aap.fermata.addon.stremio.integration;

import java.util.Objects;
import java.util.function.Supplier;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;

/** Immutable provider grant checked against live source state before deferred network access. */
public final class StremioSourceLease {
	private final long revision;
	private final String sourceUuid;
	private final String addonId;
	private final String transportFingerprint;
	private final long updatedMs;
	private final NetworkConsent consent;
	private final Supplier<StremioSourceSnapshot> currentSnapshot;

	static StremioSourceLease bound(long revision, StremioSourceRecord source,
			Supplier<StremioSourceSnapshot> currentSnapshot) {
		return bound(revision, source.sourceUuid(), source.addonId(),
				source.transportFingerprint(), source.updatedMs(), source.networkConsent(),
				Objects.requireNonNull(currentSnapshot, "currentSnapshot"));
	}

	static StremioSourceLease bound(long revision, String sourceUuid, String addonId,
			String transportFingerprint, long updatedMs, NetworkConsent consent,
			Supplier<StremioSourceSnapshot> currentSnapshot) {
		return new StremioSourceLease(revision, sourceUuid, addonId, transportFingerprint,
				updatedMs, consent, currentSnapshot);
	}

	public static StremioSourceLease unbound(String sourceUuid, NetworkConsent consent) {
		return new StremioSourceLease(-1L, sourceUuid, "", "", -1L, consent, () -> null);
	}

	private StremioSourceLease(long revision, String sourceUuid, String addonId,
			String transportFingerprint, long updatedMs, NetworkConsent consent,
			Supplier<StremioSourceSnapshot> currentSnapshot) {
		this.revision = revision;
		this.sourceUuid = requireText(sourceUuid, "sourceUuid");
		this.addonId = Objects.requireNonNull(addonId, "addonId");
		this.transportFingerprint = Objects.requireNonNull(
				transportFingerprint, "transportFingerprint");
		this.updatedMs = updatedMs;
		this.consent = Objects.requireNonNull(consent, "consent");
		this.currentSnapshot = Objects.requireNonNull(currentSnapshot, "currentSnapshot");
	}

	public boolean isBound() {
		return revision >= 0L;
	}

	public boolean isCurrent() {
		return !isBound() || matches(currentSnapshot.get());
	}

	public boolean matches(StremioSourceSnapshot snapshot) {
		if (!isBound()) return true;
		if (snapshot == null) return false;
		StremioSourceRecord source = snapshot.source(sourceUuid);
		return (source != null) && source.enabled() && addonId.equals(source.addonId()) &&
				transportFingerprint.equals(source.transportFingerprint()) &&
				(updatedMs == source.updatedMs()) && consent.equals(source.networkConsent());
	}

	public String sourceUuid() {
		return sourceUuid;
	}

	public NetworkConsent consent() {
		return consent;
	}

	/** Included only in a hashed cache-key input; never expose this value in diagnostics. */
	public String cacheBinding() {
		return transportFingerprint + "\u0000" + updatedMs + "\u0000" +
				consent.allowCleartext() + "\u0000" + consent.allowLan();
	}

	@Override
	public String toString() {
		return "StremioSourceLease[bound=" + isBound() + "]";
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
