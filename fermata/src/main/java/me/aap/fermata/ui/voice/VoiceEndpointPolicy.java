package me.aap.fermata.ui.voice;

/** Timing and stability thresholds for one online speech-recognition session. */
public final class VoiceEndpointPolicy {
	public static final VoiceEndpointPolicy DEFAULT =
			new VoiceEndpointPolicy(12_000L, 5_000L, 30_000L, 1_200L, 2);

	private final long noSpeechTimeoutMs;
	private final long finalResultTimeoutMs;
	private final long hardSessionTimeoutMs;
	private final long stablePartialDelayMs;
	private final int stablePartialRepetitions;

	public VoiceEndpointPolicy(long noSpeechTimeoutMs, long finalResultTimeoutMs,
			long hardSessionTimeoutMs, long stablePartialDelayMs,
			int stablePartialRepetitions) {
		if ((noSpeechTimeoutMs <= 0L) || (finalResultTimeoutMs <= 0L) ||
				(hardSessionTimeoutMs <= 0L) || (stablePartialDelayMs <= 0L) ||
				(stablePartialRepetitions < 2)) {
			throw new IllegalArgumentException("Voice endpoint thresholds must be positive");
		}
		this.noSpeechTimeoutMs = noSpeechTimeoutMs;
		this.finalResultTimeoutMs = finalResultTimeoutMs;
		this.hardSessionTimeoutMs = hardSessionTimeoutMs;
		this.stablePartialDelayMs = stablePartialDelayMs;
		this.stablePartialRepetitions = stablePartialRepetitions;
	}

	public long getNoSpeechTimeoutMs() {
		return noSpeechTimeoutMs;
	}

	public long getFinalResultTimeoutMs() {
		return finalResultTimeoutMs;
	}

	public long getHardSessionTimeoutMs() {
		return hardSessionTimeoutMs;
	}

	public long getStablePartialDelayMs() {
		return stablePartialDelayMs;
	}

	public int getStablePartialRepetitions() {
		return stablePartialRepetitions;
	}

	/** Only closed commands that cannot be prefixes of media searches may finalize adaptively. */
	public boolean isAdaptiveCandidate(VoiceIntent intent) {
		if ((intent == null) || (intent.getKind() != VoiceIntent.Kind.PLAYBACK)) return false;
		return switch (intent.getPlaybackAction()) {
			case PAUSE, STOP, NEXT, PREVIOUS, BACK, OPEN_CURRENT -> true;
			default -> false;
		};
	}
}
