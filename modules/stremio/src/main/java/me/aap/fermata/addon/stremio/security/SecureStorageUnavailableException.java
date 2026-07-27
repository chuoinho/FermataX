package me.aap.fermata.addon.stremio.security;

public final class SecureStorageUnavailableException extends IllegalStateException {
	public SecureStorageUnavailableException() {
		super("Encrypted Stremio source storage is unavailable");
	}
}
