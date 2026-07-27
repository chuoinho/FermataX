package me.aap.fermata.addon.stremio.net.cache;

import java.time.Duration;
import java.util.Objects;

public record CachePolicy(Duration freshFor, Duration staleFor) {
	public CachePolicy {
		Objects.requireNonNull(freshFor, "freshFor");
		Objects.requireNonNull(staleFor, "staleFor");
		if (freshFor.isNegative() || staleFor.isNegative()) {
			throw new IllegalArgumentException("Cache durations cannot be negative");
		}
	}
}
