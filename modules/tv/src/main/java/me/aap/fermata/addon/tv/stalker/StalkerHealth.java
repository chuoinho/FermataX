package me.aap.fermata.addon.tv.stalker;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Sanitized result of a Stalker source check. */
public final class StalkerHealth {
	public enum Status {
		PASS,
		DEGRADED
	}

	public enum Stage {
		DISCOVER_ENDPOINT,
		HANDSHAKE,
		PROFILE,
		CATEGORIES,
		CHANNELS,
		CREATE_LINK,
		STREAM_PROBE
	}

	public record Step(boolean successful, long durationMillis, int httpStatus) {
	}

	private final EnumMap<Stage, Step> steps = new EnumMap<>(Stage.class);
	private Status status;
	private int categoryCount;
	private int channelCount;
	private int streamAttempts;
	private int streamStatusCode;
	private String testedChannelName;
	private String warning;

	public Status getStatus() {
		return status;
	}

	public boolean isDegraded() {
		return status == Status.DEGRADED;
	}

	public int getCategoryCount() {
		return categoryCount;
	}

	public int getChannelCount() {
		return channelCount;
	}

	public int getStreamAttempts() {
		return streamAttempts;
	}

	public int getStreamStatusCode() {
		return streamStatusCode;
	}

	@Nullable
	public String getTestedChannelName() {
		return testedChannelName;
	}

	@Nullable
	public String getWarning() {
		return warning;
	}

	public Map<Stage, Step> getSteps() {
		return Collections.unmodifiableMap(steps);
	}

	synchronized void record(Stage stage, boolean successful, long durationMillis,
			int httpStatus) {
		steps.put(stage, new Step(successful, Math.max(0L, durationMillis), httpStatus));
	}

	void setCatalogCounts(int categories, int channels) {
		categoryCount = Math.max(0, categories);
		channelCount = Math.max(0, channels);
	}

	void incrementStreamAttempts() {
		streamAttempts++;
	}

	void recordStreamFailureStatus(int statusCode) {
		streamStatusCode = statusCode;
	}

	void completePass(StalkerChannel channel, int statusCode) {
		status = Status.PASS;
		streamStatusCode = statusCode;
		testedChannelName = channel.name();
		warning = null;
	}

	void completeDegraded(@Nullable String message) {
		status = Status.DEGRADED;
		warning = message;
	}

	@Override
	public String toString() {
		return "StalkerHealth{status=" + status + ", categories=" + categoryCount +
				", channels=" + channelCount + ", attempts=" + streamAttempts +
				", streamStatus=" + streamStatusCode + '}';
	}
}
