package me.aap.fermata.auto;

/** Controller-stamped lifetime values for a single Open on Car request. */
public record OpenOnCarToken(long connectionEpoch, long registrationGeneration,
		long modeRevision, long sourceGeneration, long requestId) {
}
