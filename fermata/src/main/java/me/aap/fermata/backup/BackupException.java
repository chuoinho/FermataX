package me.aap.fermata.backup;

import java.io.IOException;

/** Safe, non-sensitive failure returned by the portable backup pipeline. */
public final class BackupException extends IOException {
	public enum Code {
		INVALID_FORMAT,
		UNSUPPORTED_VERSION,
		AUTHENTICATION_FAILED,
		SECURE_STORAGE_UNAVAILABLE,
		INCOMPLETE_BACKUP,
		RESTORE_FAILED
	}

	private final Code code;

	public BackupException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public BackupException(Code code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public Code getCode() {
		return code;
	}
}
