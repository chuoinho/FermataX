package me.aap.fermata.addon.stremio.net.http;

import java.time.Duration;
import java.util.Objects;

import me.aap.fermata.addon.stremio.net.NetworkLimits;

public record HttpDeadlines(
		Duration connect,
		Duration headers,
		Duration body,
		Duration call) {
	public static final HttpDeadlines DEFAULT = new HttpDeadlines(
			NetworkLimits.CONNECT_TIMEOUT,
			NetworkLimits.HEADER_TIMEOUT,
			NetworkLimits.BODY_TIMEOUT,
			NetworkLimits.CALL_TIMEOUT);

	public HttpDeadlines {
		requirePositive(connect, "connect");
		requirePositive(headers, "headers");
		requirePositive(body, "body");
		requirePositive(call, "call");
	}

	private static void requirePositive(Duration value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(name + " deadline must be positive");
		}
	}
}
