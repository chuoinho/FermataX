package me.aap.fermata.addon.stremio.net.http;

import java.io.IOException;

public final class HttpFailure extends IOException {
	private final Code code;

	public HttpFailure(Code code, String message) {
		super(message);
		this.code = code;
	}

	public HttpFailure(Code code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public Code code() {
		return code;
	}

	public enum Code {
		CANCELLED,
		CONNECT_TIMEOUT,
		HEADER_TIMEOUT,
		BODY_TIMEOUT,
		CALL_TIMEOUT,
		BODY_TOO_LARGE,
		INVALID_REDIRECT,
		TRANSPORT,
		HTTP_STATUS
	}
}
