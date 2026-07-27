package me.aap.fermata.addon.stremio.torrent;

import com.frostwire.jlibtorrent.TorrentStatus;

/** Stable buffering and piece-window policy for loopback torrent reads. */
final class TorrentStreamPolicy {
	/* Keep enough verified data ahead of VLC to absorb normal torrent-rate jitter. */
	private static final int MIN_PRIORITY_WINDOW_BYTES = 16 * 1024 * 1024;
	private static final int MAX_PRIORITY_WINDOW_BYTES = 64 * 1024 * 1024;
	private static final int MIN_INITIAL_BUFFER_BYTES = 8 * 1024 * 1024;
	private static final int MAX_INITIAL_BUFFER_BYTES = 16 * 1024 * 1024;
	private static final long MIN_STALL_TIMEOUT_MILLIS = 20_000L;
	private static final long DEFAULT_STALL_TIMEOUT_MILLIS = 45_000L;
	private static final long MAX_STALL_TIMEOUT_MILLIS = 75_000L;

	private TorrentStreamPolicy() {
	}

	static int priorityWindowBytes(long downloadRate) {
		long adaptive = Math.max(MIN_PRIORITY_WINDOW_BYTES, downloadRate * 8L);
		return (int) Math.min(MAX_PRIORITY_WINDOW_BYTES, adaptive);
	}

	static int initialBufferLength(long rangeLength, long downloadRate) {
		if (rangeLength <= 0L) return 0;
		long adaptive = (downloadRate <= 0L) ? MIN_INITIAL_BUFFER_BYTES :
				Math.max(MIN_INITIAL_BUFFER_BYTES, downloadRate * 4L);
		long target = Math.min(MAX_INITIAL_BUFFER_BYTES, adaptive);
		return (int) Math.min(rangeLength, target);
	}

	/** Allows a large piece enough time to finish while retaining the 90 second hard read bound. */
	static long stallTimeoutMillis(int pieceLength, long downloadRate) {
		if (pieceLength <= 0) return MIN_STALL_TIMEOUT_MILLIS;
		if (downloadRate <= 0L) return DEFAULT_STALL_TIMEOUT_MILLIS;
		long estimatedPieceMillis = divideRoundedUp((long) pieceLength * 1_000L, downloadRate);
		long adaptive = saturatedAdd(saturatedMultiply(estimatedPieceMillis, 3L), 5_000L);
		return Math.max(MIN_STALL_TIMEOUT_MILLIS,
				Math.min(MAX_STALL_TIMEOUT_MILLIS, adaptive));
	}

	private static long divideRoundedUp(long value, long divisor) {
		return (value / divisor) + ((value % divisor == 0L) ? 0L : 1L);
	}

	private static long saturatedMultiply(long value, long multiplier) {
		if ((value > 0L) && (value > Long.MAX_VALUE / multiplier)) return Long.MAX_VALUE;
		return value * multiplier;
	}

	private static long saturatedAdd(long value, long increment) {
		return (value > Long.MAX_VALUE - increment) ? Long.MAX_VALUE : value + increment;
	}

	static int bufferScore(int peers, long downloadRate, long downloaded, long target) {
		if (target <= 0) return 0;
		// The UI presents this value as a percentage, so only verified bytes in the
		// requested range may contribute. Peer count and global torrent speed are shown
		// separately and do not prove that the player range is readable.
		double ratio = Math.max(downloaded, 0L) / (double) target;
		return Math.min(99, (int) Math.round(Math.min(1d, ratio) * 100d));
	}

	static boolean requestedRangeAdvanced(long available, long previousAvailable) {
		return available > previousAvailable;
	}

	static boolean isChecking(TorrentStatus.State state) {
		return (state == TorrentStatus.State.CHECKING_FILES) ||
				(state == TorrentStatus.State.CHECKING_RESUME_DATA);
	}
}
