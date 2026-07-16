package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.addon.tv.m3u.TvM3uFile.EPG_FILE_AGE;

final class XmlTvLoadPolicy {
	static final long REPLACEMENT_DELAY_MS = 30_000L;
	static final long RETRY_DELAY_MS = 5 * 60_000L;

	private XmlTvLoadPolicy() {
	}

	static DownloadAction resolveDownload(boolean hasIndex, long bytesDownloaded) {
		if (hasIndex && (bytesDownloaded == 0L)) return DownloadAction.USE_EXISTING;
		return hasIndex ? DownloadAction.PARSE_AFTER_DELAY : DownloadAction.PARSE_NOW;
	}

	static StartupAction resolveStartup(boolean hasIndex) {
		return hasIndex ? StartupAction.REFRESH_IN_BACKGROUND : StartupAction.WAIT_FOR_INITIAL_LOAD;
	}

	static FailureAction resolveFailure(boolean hasIndex) {
		return hasIndex ? FailureAction.RETRY : FailureAction.CLOSE;
	}

	static long nextUpdateTime(long timestamp, int maxAgeSeconds, long now) {
		if (timestamp <= 0L) timestamp = now;
		if (maxAgeSeconds <= 0) maxAgeSeconds = EPG_FILE_AGE;
		long updateTime = timestamp + maxAgeSeconds * 1000L;
		return (updateTime <= now) ? now + EPG_FILE_AGE * 1000L : updateTime;
	}

	enum DownloadAction {
		USE_EXISTING, PARSE_NOW, PARSE_AFTER_DELAY
	}

	enum StartupAction {
		REFRESH_IN_BACKGROUND, WAIT_FOR_INITIAL_LOAD
	}

	enum FailureAction {
		RETRY, CLOSE
	}
}
