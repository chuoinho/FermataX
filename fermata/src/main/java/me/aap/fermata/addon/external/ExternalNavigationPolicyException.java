package me.aap.fermata.addon.external;

/** A browser handoff URL no longer satisfies its originating addon's network policy. */
public final class ExternalNavigationPolicyException extends Exception {
	public ExternalNavigationPolicyException(String message) {
		super(message);
	}

	public ExternalNavigationPolicyException(String message, Throwable cause) {
		super(message, cause);
	}
}
