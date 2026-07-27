package me.aap.fermata.media.engine;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** A playback failure whose reason is safe to expose even for location-sensitive items. */
public final class PlaybackFailureException extends IOException {
	private final Reason reason;

	public PlaybackFailureException(Reason reason) {
		this(reason, null);
	}

	public PlaybackFailureException(Reason reason, Throwable cause) {
		super(reason.name(), cause);
		this.reason = java.util.Objects.requireNonNull(reason, "reason");
	}

	public Reason getReason() {
		return reason;
	}

	/** Transport failures cannot be repaired by preparing the same source in another decoder. */
	public boolean preventsEngineFallback() {
		return switch (reason) {
			case P2P_METADATA_UNAVAILABLE, P2P_NO_PEERS, P2P_DATA_TIMEOUT,
					P2P_ENGINE_UNAVAILABLE, P2P_FILE_UNAVAILABLE, P2P_LOW_STORAGE -> true;
		};
	}

	/** Finds a typed failure through CompletionException and player wrapper chains. */
	public static PlaybackFailureException find(Throwable error) {
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Throwable current = error;
				(current != null) && seen.add(current); current = current.getCause()) {
			if (current instanceof PlaybackFailureException failure) return failure;
		}
		return null;
	}

	public enum Reason {
		P2P_METADATA_UNAVAILABLE,
		P2P_NO_PEERS,
		P2P_DATA_TIMEOUT,
		P2P_ENGINE_UNAVAILABLE,
		P2P_FILE_UNAVAILABLE,
		P2P_LOW_STORAGE
	}
}
