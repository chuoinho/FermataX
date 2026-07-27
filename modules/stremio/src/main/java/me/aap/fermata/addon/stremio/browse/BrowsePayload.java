package me.aap.fermata.addon.stremio.browse;

import java.util.Arrays;
import java.util.Objects;

public final class BrowsePayload {
	private final byte[] body;
	private final boolean stale;

	public BrowsePayload(byte[] body, boolean stale) {
		this.body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
		this.stale = stale;
	}

	public byte[] body() {
		return Arrays.copyOf(body, body.length);
	}

	public boolean stale() {
		return stale;
	}
}
