package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;
import java.util.List;

public record StreamBehaviorHints(
		boolean notWebReady,
		String bingeGroup,
		String videoHash,
		Long videoSize,
		String filename,
		List<String> countryWhitelist,
		ProxyHeaders proxyHeaders) {
	public static final StreamBehaviorHints EMPTY =
			new StreamBehaviorHints(false, null, null, null, null, List.of(), ProxyHeaders.EMPTY);

	public StreamBehaviorHints(boolean notWebReady, String bingeGroup, String videoHash,
			Long videoSize, ProxyHeaders proxyHeaders) {
		this(notWebReady, bingeGroup, videoHash, videoSize, null, List.of(), proxyHeaders);
	}

	public StreamBehaviorHints {
		if ((videoSize != null) && (videoSize < 0L)) throw new IllegalArgumentException("Invalid videoSize");
		countryWhitelist = List.copyOf(Objects.requireNonNull(
				countryWhitelist, "countryWhitelist"));
		Objects.requireNonNull(proxyHeaders, "proxyHeaders");
	}

	@Override
	public String toString() {
		return "StreamBehaviorHints[notWebReady=" + notWebReady +
				", bingeGroup=<redacted>, videoHash=<redacted>, videoSize=" + videoSize +
				", filename=<redacted>, countries=" + countryWhitelist.size() +
				", proxyHeaders=<redacted>]";
	}
}
