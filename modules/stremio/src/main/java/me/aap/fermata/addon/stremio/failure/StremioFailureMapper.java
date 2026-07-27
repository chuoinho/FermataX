package me.aap.fermata.addon.stremio.failure;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;

import me.aap.fermata.addon.stremio.failure.StremioFailure.Code;
import me.aap.fermata.addon.stremio.failure.StremioFailure.Phase;
import me.aap.fermata.addon.stremio.integration.StremioIntegrationException;
import me.aap.fermata.addon.stremio.net.http.HttpFailure;
import me.aap.fermata.addon.stremio.source.StremioSourceException;
import me.aap.fermata.media.engine.PlaybackFailureException;

/** Converts implementation exceptions to a stable, secret-free failure contract. */
public final class StremioFailureMapper {
	private StremioFailureMapper() {
	}

	public static StremioFailure map(Throwable error, Phase phase, String providerKey) {
		Throwable cause = unwrap(error);
		Code code = code(cause);
		boolean retryable = retryable(code);
		return new StremioFailure(code, phase, providerKey, retryable,
				recovery(code, retryable), cause);
	}

	private static Code code(Throwable cause) {
		if (cause instanceof CancellationException) return Code.CANCELLED;
		if (cause instanceof HttpFailure http) {
			return switch (http.code()) {
				case CANCELLED -> Code.CANCELLED;
				case CONNECT_TIMEOUT -> Code.CONNECT_TIMEOUT;
				case HEADER_TIMEOUT -> Code.HEADER_TIMEOUT;
				case BODY_TIMEOUT, CALL_TIMEOUT -> Code.BODY_TIMEOUT;
				case INVALID_REDIRECT -> Code.REDIRECT_REJECTED;
				case BODY_TOO_LARGE -> Code.MALFORMED_RESOURCE;
				case HTTP_STATUS -> Code.HTTP_SERVER;
				case TRANSPORT -> Code.DNS;
			};
		}
		if (cause instanceof StremioIntegrationException integration) {
			return switch (integration.code()) {
				case CANCELLED -> Code.CANCELLED;
				case SOURCE_DISABLED -> Code.PROVIDER_DISABLED;
				case SOURCE_CHANGED -> Code.PROVIDER_CHANGED;
				case UNSUPPORTED_CAPABILITY -> Code.UNSUPPORTED_RESOURCE;
				case NETWORK_TIMEOUT -> Code.BODY_TIMEOUT;
				case INVALID_SOURCE, INVALID_RESPONSE, RESPONSE_TOO_LARGE ->
						Code.MALFORMED_RESOURCE;
				case SOURCE_NOT_FOUND, SECRET_UNAVAILABLE -> Code.PROVIDER_CHANGED;
				case NETWORK -> Code.DNS;
				case HTTP_STATUS -> Code.HTTP_SERVER;
			};
		}
		if (cause instanceof StremioSourceException source) {
			return switch (source.code()) {
				case CANCELLED, CLOSED -> Code.CANCELLED;
				case INVALID_MANIFEST -> Code.MALFORMED_MANIFEST;
				case INVALID_TRANSPORT, TRANSPORT -> Code.ENDPOINT_REJECTED;
				case NOT_FOUND -> Code.PROVIDER_CHANGED;
				case PERSISTENCE, ROLLBACK -> Code.DATABASE;
				case SECURE_STORAGE -> Code.INTERNAL;
				case CONCURRENT_MODIFICATION -> Code.STALE_RESULT;
				case DUPLICATE_TRANSPORT, INVALID_ORDER, SECRET_TAINT ->
						Code.MALFORMED_RESOURCE;
			};
		}
		PlaybackFailureException playback = PlaybackFailureException.find(cause);
		if (playback != null) {
			return switch (playback.getReason()) {
				case P2P_METADATA_UNAVAILABLE -> Code.P2P_METADATA_TIMEOUT;
				case P2P_NO_PEERS -> Code.P2P_NO_PEERS;
				case P2P_DATA_TIMEOUT -> Code.P2P_DATA_TIMEOUT;
				case P2P_FILE_UNAVAILABLE, P2P_ENGINE_UNAVAILABLE -> Code.P2P_FILE_ERROR;
				case P2P_LOW_STORAGE -> Code.P2P_LOW_STORAGE;
			};
		}
		if (cause instanceof IllegalArgumentException) return Code.MALFORMED_RESOURCE;
		return Code.INTERNAL;
	}

	private static boolean retryable(Code code) {
		return switch (code) {
			case DNS, CONNECT_TIMEOUT, HEADER_TIMEOUT, BODY_TIMEOUT, HTTP_RATE_LIMIT,
					HTTP_SERVER, P2P_METADATA_TIMEOUT, P2P_NO_PEERS, P2P_DATA_TIMEOUT,
					PLAYER_CREATE, PLAYER_PREPARE, PLAYER_FIRST_FRAME_TIMEOUT,
					PLAYER_DECODER, SUBTITLE_DOWNLOAD, SUBTITLE_ATTACH, CACHE_IO -> true;
			default -> false;
		};
	}

	private static StremioRecovery recovery(Code code, boolean retryable) {
		return switch (code) {
			case CANCELLED, STALE_RESULT -> StremioRecovery.NONE;
			case PROVIDER_DISABLED, PROVIDER_CHANGED, MALFORMED_MANIFEST,
					UNSUPPORTED_RESOURCE -> StremioRecovery.MANAGE_ADDON;
			case NO_RESULTS, NO_PLAYABLE_STREAM, STREAM_EXPIRED, P2P_NO_PEERS ->
					StremioRecovery.SELECT_SOURCE;
			case P2P_LOW_STORAGE -> StremioRecovery.FREE_STORAGE;
			default -> retryable ? StremioRecovery.RETRY : StremioRecovery.CANCEL;
		};
	}

	private static Throwable unwrap(Throwable error) {
		if (error == null) return new IllegalStateException("Missing Stremio failure cause");
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Throwable current = error;
		while (current.getCause() != null && seen.add(current) &&
				(current instanceof java.util.concurrent.CompletionException ||
						current instanceof java.util.concurrent.ExecutionException)) {
			current = current.getCause();
		}
		return current;
	}
}
