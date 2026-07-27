package me.aap.fermata.addon.stremio.protocol.response;

public final class StremioResponseException extends IllegalArgumentException {
	private final String field;

	public StremioResponseException(String field, String message) {
		super(field + ": " + message);
		this.field = field;
	}

	public StremioResponseException(String field, String message, Throwable cause) {
		super(field + ": " + message, cause);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
