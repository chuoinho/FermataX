package me.aap.fermata.addon.stremio.browse;

public class BrowseGatewayException extends RuntimeException {
	private final boolean retryable;

	public BrowseGatewayException(String message, boolean retryable) {
		super(message);
		this.retryable = retryable;
	}

	public boolean retryable() {
		return retryable;
	}
}
