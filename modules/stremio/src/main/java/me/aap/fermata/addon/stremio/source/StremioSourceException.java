package me.aap.fermata.addon.stremio.source;

/** Safe domain failure. Messages and codes must never contain provider secrets. */
public final class StremioSourceException extends RuntimeException {
	private final Code code;

	public StremioSourceException(Code code) {
		this(code, null);
	}

	public StremioSourceException(Code code, Throwable cause) {
		super(code.message, cause);
		this.code = code;
	}

	public Code code() {
		return code;
	}

	public enum Code {
		CANCELLED("Source operation was cancelled"),
		CLOSED("Source manager is closed"),
		CONCURRENT_MODIFICATION("Source state changed concurrently"),
		DUPLICATE_TRANSPORT("Source is already installed"),
		INVALID_MANIFEST("Provider manifest is invalid"),
		INVALID_ORDER("Provider order is invalid"),
		INVALID_TRANSPORT("Provider URL is invalid"),
		NOT_FOUND("Source was not found"),
		PERSISTENCE("Source state could not be persisted"),
		ROLLBACK("Source rollback failed"),
		SECRET_TAINT("Provider data contains secret material"),
		SECURE_STORAGE("Secure source storage is unavailable"),
		TRANSPORT("Provider manifest could not be loaded");

		private final String message;

		Code(String message) {
			this.message = message;
		}
	}
}
