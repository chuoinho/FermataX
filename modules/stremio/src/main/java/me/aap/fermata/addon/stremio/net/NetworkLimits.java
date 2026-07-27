package me.aap.fermata.addon.stremio.net;

import java.time.Duration;

public final class NetworkLimits {
	public static final int MAX_REDIRECTS = 5;
	public static final int MAX_GLOBAL_JSON_CONCURRENCY = 8;
	public static final int MAX_PER_HOST_JSON_CONCURRENCY = 4;
	public static final long MAX_MANIFEST_BODY_BYTES = 512L * 1024L;
	public static final long MAX_JSON_BODY_BYTES = 4L * 1024L * 1024L;
	public static final Duration DNS_TIMEOUT = Duration.ofSeconds(5);
	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	public static final Duration HEADER_TIMEOUT = Duration.ofSeconds(8);
	public static final Duration BODY_TIMEOUT = Duration.ofSeconds(12);
	public static final Duration CALL_TIMEOUT = Duration.ofSeconds(12);

	private NetworkLimits() {
	}
}
