package me.aap.fermata.media.net;

/** Immutable progress emitted while a remote playback target is becoming readable. */
public record RemotePlaybackProgress(Phase phase, int peers, int seeds,
		long downloadRateBytes, long downloadedBytes, long targetBytes, int percent,
		Failure failure) {
	public RemotePlaybackProgress(Phase phase, int peers, int seeds,
			long downloadRateBytes, long downloadedBytes, long targetBytes, int percent) {
		this(phase, peers, seeds, downloadRateBytes, downloadedBytes, targetBytes, percent, null);
	}

	public RemotePlaybackProgress {
		java.util.Objects.requireNonNull(phase, "phase");
		if ((peers < 0) || (seeds < 0) || (downloadRateBytes < 0) ||
				(downloadedBytes < 0) || (targetBytes < 0) ||
				(percent < -1) || (percent > 100)) {
			throw new IllegalArgumentException("Invalid remote playback progress");
		}
		if ((phase == Phase.FAILED) != (failure != null)) {
			throw new IllegalArgumentException("Failure must only be present for failed progress");
		}
	}

	public static RemotePlaybackProgress resolving() {
		return new RemotePlaybackProgress(Phase.RESOLVING, 0, 0, 0, 0, 0, -1);
	}

	public static RemotePlaybackProgress findingPeers() {
		return new RemotePlaybackProgress(Phase.FINDING_PEERS, 0, 0, 0, 0, 0, -1);
	}

	public static RemotePlaybackProgress buffering(int peers, int seeds, long rate,
			long downloaded, long target, int percent) {
		return new RemotePlaybackProgress(Phase.BUFFERING, peers, seeds, rate,
				downloaded, target, Math.max(0, Math.min(percent, 99)));
	}

	public static RemotePlaybackProgress rebuffering(int peers, int seeds, long rate) {
		return new RemotePlaybackProgress(Phase.REBUFFERING, peers, seeds, rate, 0, 0, -1);
	}

	public static RemotePlaybackProgress streaming(int peers, int seeds, long rate) {
		return new RemotePlaybackProgress(Phase.STREAMING, peers, seeds, rate, 0, 0, -1);
	}

	public static RemotePlaybackProgress ready(int peers, int seeds, long rate,
			long downloaded, long target) {
		return new RemotePlaybackProgress(Phase.READY, peers, seeds, rate,
				downloaded, target, 100);
	}

	public static RemotePlaybackProgress failed(Failure failure) {
		return new RemotePlaybackProgress(Phase.FAILED, 0, 0, 0, 0, 0, -1,
				java.util.Objects.requireNonNull(failure, "failure"));
	}

	public enum Phase {
		RESOLVING,
		FINDING_PEERS,
		BUFFERING,
		READY,
		STREAMING,
		REBUFFERING,
		FAILED
	}

	public enum Failure {
		METADATA_UNAVAILABLE,
		NO_PEERS,
		DATA_TIMEOUT,
		ENGINE_UNAVAILABLE,
		FILE_UNAVAILABLE,
		LOW_STORAGE
	}
}
