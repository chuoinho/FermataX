package me.aap.fermata.addon.stremio.playback;

import java.util.Objects;

import me.aap.fermata.media.net.RemotePlaybackRequest;

/** Mutable state holder guarded by {@link PlaybackAttemptSupervisor}. */
public final class PlaybackAttempt {
	private final long operationId;
	private final long requestRevision;
	private final PlaybackDescriptor descriptor;
	private PlaybackAttemptState state = PlaybackAttemptState.CREATED;
	private RemotePlaybackRequest request;
	private Throwable failure;
	private boolean startedSignal;
	private boolean firstFrameSignal;
	private int decoderFallbacks;

	PlaybackAttempt(long operationId, long requestRevision, PlaybackDescriptor descriptor) {
		this.operationId = operationId;
		this.requestRevision = requestRevision;
		this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
	}

	public long operationId() {
		return operationId;
	}

	public long requestRevision() {
		return requestRevision;
	}

	public PlaybackDescriptor descriptor() {
		return descriptor;
	}

	public PlaybackAttemptState state() {
		return state;
	}

	public Throwable failure() {
		return failure;
	}

	public int decoderFallbacks() {
		return decoderFallbacks;
	}

	boolean transition(PlaybackAttemptState next) {
		if (state == next) return false;
		if (state.isTerminal() || !isForwardTransition(state, next)) return false;
		state = next;
		return true;
	}

	void request(RemotePlaybackRequest request) {
		if (this.request == request) return;
		closeRequest();
		this.request = request;
	}

	void closeRequest() {
		RemotePlaybackRequest r = request;
		request = null;
		if (r != null) r.close();
	}

	void failure(Throwable failure) {
		this.failure = failure;
	}

	void startedSignal() {
		startedSignal = true;
	}

	boolean hasStartedSignal() {
		return startedSignal;
	}

	void firstFrameSignal() {
		firstFrameSignal = true;
	}

	boolean hasFirstFrameSignal() {
		return firstFrameSignal;
	}

	boolean claimDecoderFallback() {
		if (decoderFallbacks != 0) return false;
		decoderFallbacks = 1;
		return true;
	}

	void inheritDecoderFallback() {
		decoderFallbacks = 1;
	}

	private static boolean isForwardTransition(PlaybackAttemptState current,
			PlaybackAttemptState next) {
		if ((next == PlaybackAttemptState.FAILED) ||
				(next == PlaybackAttemptState.CANCELLED)) return true;
		return switch (current) {
			case CREATED -> next == PlaybackAttemptState.RESOLVING;
			case RESOLVING -> next == PlaybackAttemptState.PREPARING;
			case PREPARING -> next == PlaybackAttemptState.DATA_READY;
			case DATA_READY -> next == PlaybackAttemptState.PLAYER_READY;
			case PLAYER_READY -> next == PlaybackAttemptState.FIRST_FRAME;
			case FIRST_FRAME -> next == PlaybackAttemptState.PLAYING;
			case PLAYING -> next == PlaybackAttemptState.ENDED;
			default -> false;
		};
	}
}
