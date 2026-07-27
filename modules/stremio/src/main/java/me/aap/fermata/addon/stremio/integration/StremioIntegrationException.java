package me.aap.fermata.addon.stremio.integration;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/** Stable, redacted failure exposed by the runtime-to-domain boundary. */
public final class StremioIntegrationException extends RuntimeException {
	private final Code code;
	private final boolean retryable;

	StremioIntegrationException(Code code, boolean retryable) {
		super("Stremio request failed: " + code.name());
		this.code = code;
		this.retryable = retryable;
	}

	public Code code() {
		return code;
	}

	public boolean retryable() {
		return retryable;
	}

	static StremioIntegrationException redactResponseFailure(Throwable error) {
		while ((error instanceof CompletionException ||
				error instanceof java.util.concurrent.ExecutionException) &&
				error.getCause() != null) error = error.getCause();
		if (error instanceof StremioIntegrationException integration) return integration;
		if (error instanceof CancellationException) {
			return new StremioIntegrationException(Code.CANCELLED, false);
		}
		return new StremioIntegrationException(Code.INVALID_RESPONSE, false);
	}

	@Override
	public String toString() {
		return "StremioIntegrationException[code=" + code + ", retryable=" + retryable + ']';
	}

	public enum Code {
		CANCELLED,
		SOURCE_NOT_FOUND,
		SOURCE_DISABLED,
		SOURCE_CHANGED,
		SECRET_UNAVAILABLE,
		INVALID_SOURCE,
		UNSUPPORTED_CAPABILITY,
		NETWORK_TIMEOUT,
		NETWORK,
		HTTP_STATUS,
		RESPONSE_TOO_LARGE,
		INVALID_RESPONSE
	}
}
