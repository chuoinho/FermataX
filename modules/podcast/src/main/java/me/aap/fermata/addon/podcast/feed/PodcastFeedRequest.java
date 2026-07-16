package me.aap.fermata.addon.podcast.feed;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import androidx.annotation.Nullable;

public final class PodcastFeedRequest {
	private final String url;
	private final String authorization;
	private final String etag;
	private final String lastModified;
	private final boolean allowAuthenticatedDowngrade;

	public PodcastFeedRequest(String url, @Nullable String username, @Nullable String password,
			@Nullable String etag, @Nullable String lastModified,
			boolean allowAuthenticatedDowngrade) {
		this.url = url;
		this.etag = emptyToNull(etag);
		this.lastModified = emptyToNull(lastModified);
		this.allowAuthenticatedDowngrade = allowAuthenticatedDowngrade;
		if ((username == null) || username.isEmpty()) {
			authorization = null;
		} else {
			String raw = username + ':' + ((password == null) ? "" : password);
			authorization = "Basic " + Base64.getEncoder().encodeToString(
					raw.getBytes(StandardCharsets.UTF_8));
		}
	}

	public static PodcastFeedRequest publicFeed(String url) {
		return new PodcastFeedRequest(url, null, null, null, null, false);
	}

	public String getUrl() { return url; }
	@Nullable public String getAuthorization() { return authorization; }
	@Nullable public String getEtag() { return etag; }
	@Nullable public String getLastModified() { return lastModified; }
	public boolean isAuthenticatedDowngradeAllowed() { return allowAuthenticatedDowngrade; }

	private static String emptyToNull(String value) {
		return ((value == null) || value.isEmpty()) ? null : value;
	}
}
