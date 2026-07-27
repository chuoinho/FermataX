package me.aap.fermata.addon.stremio.integration;

import static me.aap.fermata.addon.stremio.integration.StremioIntegrationException.Code.UNSUPPORTED_CAPABILITY;

import java.time.Duration;
import java.util.Map;

import me.aap.fermata.addon.stremio.net.cache.CachePolicy;
import me.aap.fermata.addon.stremio.net.http.HttpDeadlines;
import me.aap.fermata.addon.stremio.protocol.response.StremioResponseParser;

/** Immutable HTTP and cache limits selected for each Stremio protocol resource. */
final class StremioProtocolRequestPolicy {
	private static final Map<String, Policy> POLICIES = Map.of(
			"catalog", policy(Duration.ofMinutes(5), Duration.ofMinutes(30)),
			"meta", policy(Duration.ofMinutes(30), Duration.ofHours(24)),
			"stream", policy(Duration.ofSeconds(30), Duration.ofSeconds(90)),
			"subtitles", policy(Duration.ofMinutes(5), Duration.ofMinutes(30),
					new HttpDeadlines(Duration.ofSeconds(3), Duration.ofSeconds(5),
							Duration.ofSeconds(7), Duration.ofSeconds(8))),
			"addon_catalog", policy(Duration.ofMinutes(15), Duration.ofHours(6)));

	private StremioProtocolRequestPolicy() {
	}

	static Policy forResource(String resource) {
		Policy policy = POLICIES.get(resource);
		if (policy == null) throw StremioProtocolFailureMapper.failure(UNSUPPORTED_CAPABILITY);
		return policy;
	}

	private static Policy policy(Duration fresh, Duration stale) {
		return policy(fresh, stale, HttpDeadlines.DEFAULT);
	}

	private static Policy policy(Duration fresh, Duration stale, HttpDeadlines deadlines) {
		return new Policy(StremioResponseParser.MAX_RESPONSE_BYTES,
				new CachePolicy(fresh, stale), deadlines);
	}

	record Policy(long maxBodyBytes, CachePolicy cachePolicy, HttpDeadlines deadlines) {
	}
}
