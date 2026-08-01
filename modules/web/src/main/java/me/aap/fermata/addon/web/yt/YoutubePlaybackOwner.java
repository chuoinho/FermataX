package me.aap.fermata.addon.web.yt;

import java.util.Objects;

final class YoutubePlaybackOwner<T> {
	private T owner;
	private String videoId = "";

	void prepare(T candidate, String videoId, boolean preserve) {
		if (!preserve) {
			clear();
			return;
		}
		owner = candidate;
		this.videoId = (videoId == null) ? "" : videoId;
	}

	T resolve(T fallback) {
		return (owner == null) ? fallback : owner;
	}

	void retain(String activeVideoId) {
		if (!Objects.equals(videoId, activeVideoId)) clear();
	}

	void transferTo(YoutubePlaybackOwner<T> target) {
		target.owner = owner;
		target.videoId = videoId;
		clear();
	}

	void clear() {
		owner = null;
		videoId = "";
	}
}
