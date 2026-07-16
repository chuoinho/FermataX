package me.aap.fermata.addon.podcast.net;

import java.io.IOException;

public final class PodcastException extends IOException {
	private final PodcastErrorCode code;
	private final int httpStatus;
	private final long retryAfterMs;

	public PodcastException(PodcastErrorCode code, String message) {
		this(code, message, 0, 0, null);
	}

	public PodcastException(PodcastErrorCode code, String message, Throwable cause) {
		this(code, message, 0, 0, cause);
	}

	public PodcastException(PodcastErrorCode code, String message, int httpStatus,
			long retryAfterMs) {
		this(code, message, httpStatus, retryAfterMs, null);
	}

	private PodcastException(PodcastErrorCode code, String message, int httpStatus,
			long retryAfterMs, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.httpStatus = httpStatus;
		this.retryAfterMs = Math.max(retryAfterMs, 0);
	}

	public PodcastErrorCode getCode() {
		return code;
	}

	public int getHttpStatus() {
		return httpStatus;
	}

	public long getRetryAfterMs() {
		return retryAfterMs;
	}
}
