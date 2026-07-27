package me.aap.fermata.media.net;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** In-memory request material resolved once at engine handoff and discarded with that engine. */
public final class ResolvedRemotePlaybackRequest {
	private final URI location;
	private final PlaybackRequestProfile profile;
	private final Map<String, String> targetHeaders;
	private final PlaybackEndpointValidator endpointValidator;

	ResolvedRemotePlaybackRequest(URI location, PlaybackRequestProfile profile,
			Map<String, String> targetHeaders, PlaybackEndpointValidator endpointValidator) {
		this.location = Objects.requireNonNull(location, "location");
		this.profile = Objects.requireNonNull(profile, "profile");
		this.targetHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(targetHeaders));
		this.endpointValidator = endpointValidator;
	}

	public ValidatedPlaybackEndpoint validateEndpoint(URI requestUri)
			throws PlaybackRequestValidationException {
		if (endpointValidator == null) return null;
		ValidatedPlaybackEndpoint endpoint = endpointValidator.validate(requestUri);
		if ((endpoint == null) || !requestUri.equals(endpoint.uri())) {
			throw new PlaybackRequestValidationException(
					"Playback endpoint validator returned an invalid result");
		}
		return endpoint;
	}

	public URI getLocation() {
		return location;
	}

	public PlaybackRequestProfile getProfile() {
		return profile;
	}

	public Map<String, String> headersFor(URI requestUri)
			throws PlaybackRequestValidationException {
		PlaybackRequestProfile.Origin destination;
		try {
			destination = PlaybackRequestProfile.Origin.from(requestUri);
		} catch (IllegalArgumentException ex) {
			throw new PlaybackRequestValidationException("Invalid playback request origin", ex);
		}
		if (!profile.isRequestOriginAllowed(requestUri)) {
			throw new PlaybackRequestValidationException(
					"Request origin is not allowed: " + profile.getDiagnosticIdentity());
		}
		if (PlaybackRequestProfile.Origin.from(location).equals(destination)) return targetHeaders;

		LinkedHashMap<String, String> restricted = new LinkedHashMap<>(targetHeaders);
		restricted.remove("Authorization");
		restricted.remove("Cookie");
		return Collections.unmodifiableMap(restricted);
	}

	@Override
	public String toString() {
		return "ResolvedRemotePlaybackRequest{" + profile.getDiagnosticIdentity() + '}';
	}
}
