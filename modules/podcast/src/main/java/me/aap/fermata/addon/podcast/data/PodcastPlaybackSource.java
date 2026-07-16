package me.aap.fermata.addon.podcast.data;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

public final class PodcastPlaybackSource {
	private final String url;
	private final Map<String, String> headers;

	public PodcastPlaybackSource(String url, @Nullable String authorization) {
		this.url = url;
		headers = (authorization == null) ? Collections.emptyMap() :
				Collections.singletonMap("Authorization", authorization);
	}

	public String getUrl() { return url; }
	public Map<String, String> getHeaders() { return headers; }
}
