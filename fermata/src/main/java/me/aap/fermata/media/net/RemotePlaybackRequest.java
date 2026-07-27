package me.aap.fermata.media.net;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;

/** A short-lived, in-memory playback handoff. Its string form never renders its target. */
public final class RemotePlaybackRequest implements AutoCloseable {
	private final URI location;
	private final PlaybackRequestProfile profile;
	private final PlaybackHeaderResolver headerResolver;
	private final PlaybackEndpointValidator endpointValidator;
	private final Runnable release;
	private final AtomicBoolean released = new AtomicBoolean();

	public RemotePlaybackRequest(URI location, PlaybackRequestProfile profile,
			PlaybackHeaderResolver headerResolver) {
		this(location, profile, headerResolver, null);
	}

	public RemotePlaybackRequest(URI location, PlaybackRequestProfile profile,
			PlaybackHeaderResolver headerResolver, PlaybackEndpointValidator endpointValidator) {
		this(location, profile, headerResolver, endpointValidator, () -> {});
	}

	/** Releases transport-owned resources when this media handoff is no longer active. */
	public RemotePlaybackRequest(URI location, PlaybackRequestProfile profile,
			PlaybackHeaderResolver headerResolver, PlaybackEndpointValidator endpointValidator,
			Runnable release) {
		this.location = Objects.requireNonNull(location, "location");
		this.profile = Objects.requireNonNull(profile, "profile");
		this.headerResolver = headerResolver;
		this.endpointValidator = endpointValidator;
		this.release = Objects.requireNonNull(release, "release");
		if (!location.equals(profile.getTargetUri())) {
			throw new IllegalArgumentException("Playback location does not match request profile");
		}
		if (profile.getRequiredEngineCapabilities().contains(
				EngineCapability.ENDPOINT_VALIDATION) && (endpointValidator == null)) {
			throw new IllegalArgumentException("Playback endpoint validator is required");
		}
	}

	public URI getLocation() {
		return location;
	}

	public PlaybackRequestProfile getProfile() {
		return profile;
	}

	public boolean isSupportedBy(Set<EngineCapability> capabilities) {
		return capabilities.containsAll(profile.getRequiredEngineCapabilities());
	}

	public Map<String, String> resolveHeaders(URI requestLocation, long nowEpochMillis,
			Set<EngineCapability> capabilities) throws PlaybackRequestValidationException {
		PlaybackRequestProfile.ResolvedHeaders resolved = profile.resolveHeaders(
				requestLocation, nowEpochMillis, headerResolver);
		if (!resolved.isSupportedBy(capabilities)) {
			throw new UnsupportedPlaybackRequestException(
					"Playback engine does not support the required request profile");
		}
		return resolved.getHeaders();
	}

	public Map<String, String> resolveHeaders(long nowEpochMillis,
			Set<EngineCapability> capabilities) throws PlaybackRequestValidationException {
		return resolveHeaders(location, nowEpochMillis, capabilities);
	}

	public ResolvedRemotePlaybackRequest resolve(long nowEpochMillis,
			Set<EngineCapability> capabilities) throws PlaybackRequestValidationException {
		return new ResolvedRemotePlaybackRequest(location, profile,
				resolveHeaders(nowEpochMillis, capabilities), endpointValidator);
	}

	/** Releases the underlying transport exactly once. */
	@Override
	public void close() {
		if (released.compareAndSet(false, true)) release.run();
	}

	@Override
	public String toString() {
		return "RemotePlaybackRequest{" + profile.getDiagnosticIdentity() + '}';
	}
}
