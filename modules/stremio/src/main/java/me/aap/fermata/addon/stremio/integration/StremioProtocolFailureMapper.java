package me.aap.fermata.addon.stremio.integration;

import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.CANCELLED;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.HTTP_STATUS;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.INVALID_RESPONSE;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.NETWORK;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.NETWORK_TIMEOUT;
import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.RESPONSE_TOO_LARGE;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import me.aap.fermata.addon.stremio.net.http.HttpFailure;

/** Redacts and maps lower-level failures at the protocol integration boundary. */
final class StremioProtocolFailureMapper {
	private StremioProtocolFailureMapper() {
	}

	static StremioIntegrationException map(Throwable error) {
		Throwable cause = unwrap(error);
		if (cause instanceof StremioIntegrationException integration) return integration;
		if (cause instanceof CancellationException) return failure(CANCELLED);
		if (cause instanceof HttpFailure http) {
			return switch (http.code()) {
				case CANCELLED -> failure(CANCELLED);
				case CONNECT_TIMEOUT, HEADER_TIMEOUT, BODY_TIMEOUT, CALL_TIMEOUT ->
						failure(NETWORK_TIMEOUT);
				case BODY_TOO_LARGE -> failure(RESPONSE_TOO_LARGE);
				case HTTP_STATUS -> failure(HTTP_STATUS);
				case INVALID_REDIRECT, TRANSPORT -> failure(NETWORK);
			};
		}
		if (cause instanceof IllegalArgumentException) return failure(INVALID_RESPONSE);
		return failure(NETWORK);
	}

	static StremioIntegrationException failure(StremioIntegrationException.Code code) {
		boolean retryable = switch (code) {
			case NETWORK_TIMEOUT, NETWORK, HTTP_STATUS -> true;
			default -> false;
		};
		return new StremioIntegrationException(code, retryable);
	}

	private static Throwable unwrap(Throwable error) {
		while ((error instanceof CompletionException || error instanceof ExecutionException) &&
				error.getCause() != null) error = error.getCause();
		return error;
	}
}
