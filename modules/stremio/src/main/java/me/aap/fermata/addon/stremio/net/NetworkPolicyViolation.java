package me.aap.fermata.addon.stremio.net;

import java.io.IOException;

public final class NetworkPolicyViolation extends IOException {
	private final Reason reason;

	public NetworkPolicyViolation(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public enum Reason {
		INVALID_URL,
		UNSUPPORTED_SCHEME,
		CLEARTEXT_NOT_ALLOWED,
		LAN_NOT_ALLOWED,
		FORBIDDEN_HOST,
		FORBIDDEN_ADDRESS,
		DNS_NO_RESULTS,
		TOO_MANY_REDIRECTS
	}
}
