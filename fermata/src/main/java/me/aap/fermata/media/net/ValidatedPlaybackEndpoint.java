package me.aap.fermata.media.net;

import java.net.InetAddress;
import java.net.URI;
import java.util.Objects;

/** A policy-approved playback endpoint. The address is short-lived and never persisted. */
public record ValidatedPlaybackEndpoint(URI uri, InetAddress pinnedAddress) {
	public ValidatedPlaybackEndpoint {
		Objects.requireNonNull(uri, "uri");
		Objects.requireNonNull(pinnedAddress, "pinnedAddress");
	}

	@Override
	public String toString() {
		return "ValidatedPlaybackEndpoint{redacted}";
	}
}
