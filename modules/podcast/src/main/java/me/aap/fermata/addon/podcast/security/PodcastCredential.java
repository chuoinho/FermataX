package me.aap.fermata.addon.podcast.security;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class PodcastCredential {
	private final String feedUrl;
	private final String username;
	private final String password;

	public PodcastCredential(String feedUrl, @Nullable String username, @Nullable String password) {
		this.feedUrl = feedUrl;
		this.username = emptyToNull(username);
		this.password = (this.username == null) ? null : ((password == null) ? "" : password);
	}

	public String getFeedUrl() { return feedUrl; }
	@Nullable public String getUsername() { return username; }
	@Nullable public String getPassword() { return password; }
	public boolean hasBasicAuth() { return username != null; }

	@Nullable
	public String getAuthorization() {
		if (!hasBasicAuth()) return null;
		String raw = username + ':' + password;
		return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private static String emptyToNull(String value) {
		return ((value == null) || value.isEmpty()) ? null : value;
	}
}
