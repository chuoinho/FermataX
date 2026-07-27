package me.aap.fermata.addon.stremio.torrent;

import java.util.concurrent.TimeUnit;

/** Immutable storage limits for the private Stremio torrent cache. */
final class TorrentCachePolicy {
	static final long DEFAULT_TTL_MILLIS = TimeUnit.HOURS.toMillis(72);
	static final long DEFAULT_MAX_BYTES = 4L * 1024L * 1024L * 1024L;
	static final long DEFAULT_MIN_FREE_BYTES = 512L * 1024L * 1024L;
	static final TorrentCachePolicy DEFAULT = new TorrentCachePolicy(
			DEFAULT_TTL_MILLIS, DEFAULT_MAX_BYTES, DEFAULT_MIN_FREE_BYTES);

	private final long ttlMillis;
	private final long maxBytes;
	private final long minFreeBytes;

	TorrentCachePolicy(long ttlMillis, long maxBytes, long minFreeBytes) {
		if ((ttlMillis <= 0L) || (maxBytes <= 0L) || (minFreeBytes < 0L)) {
			throw new IllegalArgumentException("Invalid torrent cache policy");
		}
		this.ttlMillis = ttlMillis;
		this.maxBytes = maxBytes;
		this.minFreeBytes = minFreeBytes;
	}

	long ttlMillis() {
		return ttlMillis;
	}

	long maxBytes() {
		return maxBytes;
	}

	long minFreeBytes() {
		return minFreeBytes;
	}
}
