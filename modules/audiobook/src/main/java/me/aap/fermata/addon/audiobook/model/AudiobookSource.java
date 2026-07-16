package me.aap.fermata.addon.audiobook.model;

import androidx.annotation.Nullable;

public final class AudiobookSource {
	private final String id;
	private final AudiobookSourceType type;
	private final String name;
	private final String endpoint;
	@Nullable private final String credentialRef;
	private final long createdMs;
	private final long updatedMs;

	public AudiobookSource(String id, AudiobookSourceType type, String name, String endpoint,
			@Nullable String credentialRef, long createdMs, long updatedMs) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.endpoint = endpoint;
		this.credentialRef = credentialRef;
		this.createdMs = createdMs;
		this.updatedMs = updatedMs;
	}

	public String getId() { return id; }
	public AudiobookSourceType getType() { return type; }
	public String getName() { return name; }
	public String getEndpoint() { return endpoint; }
	@Nullable public String getCredentialRef() { return credentialRef; }
	public long getCreatedMs() { return createdMs; }
	public long getUpdatedMs() { return updatedMs; }
}
