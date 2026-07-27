package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

public record YoutubeStreamTarget(String videoId) implements StreamTarget {
	public YoutubeStreamTarget {
		Objects.requireNonNull(videoId, "videoId");
		if (videoId.isBlank()) throw new IllegalArgumentException("videoId cannot be blank");
	}

	@Override
	public String toString() {
		return "YoutubeStreamTarget[videoId=<redacted>]";
	}
}
