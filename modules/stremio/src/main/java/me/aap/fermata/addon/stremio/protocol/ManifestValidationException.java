package me.aap.fermata.addon.stremio.protocol;

public class ManifestValidationException extends IllegalArgumentException {
	private final String field;

	public ManifestValidationException(String field, String message) {
		super(message);
		this.field = field;
	}

	public ManifestValidationException(String field, String message, Throwable cause) {
		super(message, cause);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
