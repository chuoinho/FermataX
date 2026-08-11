package me.aap.fermata.addon;

import java.util.Objects;

/** Candidate paired with the exact lease required to resolve it after a user action. */
public record SmartTopProviderResult(
		SmartTopProviderLease lease,
		SmartTopCandidate candidate) {
	public SmartTopProviderResult {
		Objects.requireNonNull(lease, "lease");
		Objects.requireNonNull(candidate, "candidate");
	}
}
