package me.aap.fermata.addon.audiobook.security;

import androidx.annotation.Nullable;

public record AudiobookCredential(String endpoint, String username, String accessToken,
		@Nullable String refreshToken, @Nullable String password) {
	public AudiobookCredential(String endpoint, String username, String accessToken,
			@Nullable String refreshToken) {
		this(endpoint, username, accessToken, refreshToken, null);
	}

	public AudiobookCredential {
		endpoint = endpoint == null ? "" : endpoint;
		username = username == null ? "" : username;
		accessToken = accessToken == null ? "" : accessToken;
		refreshToken = ((refreshToken == null) || refreshToken.isEmpty()) ? null : refreshToken;
		password = ((password == null) || password.isEmpty()) ? null : password;
	}

	public String authorization() {
		if (!accessToken.isEmpty()) return "Bearer " + accessToken;
		if ((password != null) && !username.isEmpty()) {
			String pair = username + ':' + password;
			return "Basic " + java.util.Base64.getEncoder().encodeToString(
					pair.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		return "";
	}
}
