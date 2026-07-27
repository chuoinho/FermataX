package me.aap.fermata.addon.stremio.failure;

import java.util.Objects;

/** Redacted failure value shared by Stremio domain, playback and presentation boundaries. */
public final class StremioFailure {
	private final Code code;
	private final Phase phase;
	private final String providerKey;
	private final boolean retryable;
	private final StremioRecovery recovery;
	private final Throwable cause;

	public StremioFailure(Code code, Phase phase, String providerKey, boolean retryable,
			StremioRecovery recovery, Throwable cause) {
		this.code = Objects.requireNonNull(code, "code");
		this.phase = Objects.requireNonNull(phase, "phase");
		this.providerKey = providerKey;
		this.retryable = retryable;
		this.recovery = Objects.requireNonNull(recovery, "recovery");
		this.cause = cause;
	}

	public Code code() {
		return code;
	}

	public Phase phase() {
		return phase;
	}

	public String providerKey() {
		return providerKey;
	}

	public boolean retryable() {
		return retryable;
	}

	public StremioRecovery recovery() {
		return recovery;
	}

	public Throwable cause() {
		return cause;
	}

	public boolean isCancellation() {
		return code == Code.CANCELLED || code == Code.STALE_RESULT;
	}

	@Override
	public String toString() {
		return "StremioFailure[code=" + code + ", phase=" + phase +
				", provider=" + ((providerKey == null) ? "none" : "<redacted>") +
				", retryable=" + retryable + ", recovery=" + recovery + ']';
	}

	public enum Phase {
		SOURCE,
		MANIFEST,
		CATALOG,
		META,
		STREAM,
		PLAYBACK_PREPARE,
		PLAYER,
		P2P,
		SUBTITLE,
		PERSISTENCE,
		CONFIGURATION,
		LIFECYCLE
	}

	public enum Code {
		CANCELLED,
		STALE_RESULT,
		DNS,
		CONNECT_TIMEOUT,
		HEADER_TIMEOUT,
		BODY_TIMEOUT,
		HTTP_AUTH,
		HTTP_RATE_LIMIT,
		HTTP_SERVER,
		REDIRECT_REJECTED,
		MALFORMED_MANIFEST,
		MALFORMED_RESOURCE,
		UNSUPPORTED_RESOURCE,
		PROVIDER_DISABLED,
		PROVIDER_CHANGED,
		NO_RESULTS,
		NO_PLAYABLE_STREAM,
		STREAM_EXPIRED,
		ENDPOINT_REJECTED,
		P2P_METADATA_TIMEOUT,
		P2P_NO_PEERS,
		P2P_DATA_TIMEOUT,
		P2P_FILE_ERROR,
		P2P_LOW_STORAGE,
		PLAYER_CREATE,
		PLAYER_PREPARE,
		PLAYER_FIRST_FRAME_TIMEOUT,
		PLAYER_DECODER,
		SUBTITLE_NOT_FOUND,
		SUBTITLE_DOWNLOAD,
		SUBTITLE_FORMAT,
		SUBTITLE_ATTACH,
		CACHE_IO,
		DATABASE,
		INTERNAL
	}
}
