package me.aap.fermata.addon.podcast.model;

import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

import me.aap.fermata.addon.podcast.feed.PodcastFeedRequest;
import me.aap.fermata.addon.podcast.security.PodcastCredential;
import me.aap.fermata.addon.podcast.security.PodcastUrlRedactor;
import me.aap.fermata.addon.podcast.util.PodcastIds;
import me.aap.fermata.addon.podcast.util.PodcastUrls;

public final class PodcastSource {
	private final String feedKey;
	private final String requestUrl;
	private final String storageUrl;
	private final String username;
	private final String password;
	private final String credentialRef;

	@Nullable
	public static PodcastSource create(String url, @Nullable String username,
			@Nullable String password) {
		String normalized = PodcastUrls.normalizeHttpUrl(url);
		if (normalized == null) return null;
		try {
			URI uri = new URI(normalized);
			String userInfo = uri.getUserInfo();
			if (((username == null) || username.isEmpty()) && (userInfo != null)) {
				int colon = userInfo.indexOf(':');
				username = (colon == -1) ? userInfo : userInfo.substring(0, colon);
				password = (colon == -1) ? "" : userInfo.substring(colon + 1);
			}
			String requestUrl = PodcastUrls.composeHttpUrl(uri.getScheme(), null, uri.getHost(),
					uri.getPort(), uri.getRawPath(), uri.getRawQuery());
			String feedKey = PodcastIds.hash(PodcastUrls.identity(normalized));
			boolean privateSource = ((username != null) && !username.isEmpty()) ||
					PodcastUrlRedactor.containsSecrets(normalized);
			return new PodcastSource(feedKey, requestUrl,
					PodcastUrlRedactor.forStorage(normalized), username, password,
					privateSource ? "feed:" + feedKey : null);
		} catch (URISyntaxException | IllegalArgumentException ex) {
			return null;
		}
	}

	private PodcastSource(String feedKey, String requestUrl, String storageUrl,
			@Nullable String username, @Nullable String password, @Nullable String credentialRef) {
		this.feedKey = feedKey;
		this.requestUrl = requestUrl;
		this.storageUrl = storageUrl;
		this.username = emptyToNull(username);
		this.password = (this.username == null) ? null : ((password == null) ? "" : password);
		this.credentialRef = credentialRef;
	}

	public String getFeedKey() { return feedKey; }
	public String getRequestUrl() { return requestUrl; }
	public String getStorageUrl() { return storageUrl; }
	@Nullable public String getCredentialRef() { return credentialRef; }
	public boolean isPrivate() { return credentialRef != null; }
	public boolean hasBasicAuth() { return username != null; }

	public PodcastFeedRequest toRequest(@Nullable String etag, @Nullable String lastModified) {
		return new PodcastFeedRequest(requestUrl, username, password, etag, lastModified, false);
	}

	public PodcastCredential toCredential() {
		return new PodcastCredential(requestUrl, username, password);
	}

	@Nullable
	public PodcastCredential credentialForUrl(String url) {
		PodcastSource nested = create(url, null, null);
		if (nested == null) return null;
		if (nested.hasBasicAuth() || !hasBasicAuth()) return nested.toCredential();
		return new PodcastCredential(nested.requestUrl, username, password);
	}

	private static String emptyToNull(String value) {
		return ((value == null) || value.isEmpty()) ? null : value;
	}
}
