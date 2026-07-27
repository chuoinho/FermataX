package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.time.Duration;

import org.junit.Test;

import me.aap.fermata.addon.stremio.net.http.HttpDeadlines;
import me.aap.fermata.addon.stremio.protocol.response.StremioResponseParser;

public class StremioProtocolRequestPolicyTest {
	@Test
	public void preservesExactResourceCacheWindowsAndDeadlines() {
		assertPolicy("catalog", Duration.ofMinutes(5), Duration.ofMinutes(30),
				HttpDeadlines.DEFAULT);
		assertPolicy("meta", Duration.ofMinutes(30), Duration.ofHours(24),
				HttpDeadlines.DEFAULT);
		assertPolicy("stream", Duration.ofSeconds(30), Duration.ofSeconds(90),
				HttpDeadlines.DEFAULT);
		assertPolicy("addon_catalog", Duration.ofMinutes(15), Duration.ofHours(6),
				HttpDeadlines.DEFAULT);

		var subtitles = StremioProtocolRequestPolicy.forResource("subtitles");
		assertEquals(Duration.ofMinutes(5), subtitles.cachePolicy().freshFor());
		assertEquals(Duration.ofMinutes(30), subtitles.cachePolicy().staleFor());
		assertEquals(new HttpDeadlines(Duration.ofSeconds(3), Duration.ofSeconds(5),
				Duration.ofSeconds(7), Duration.ofSeconds(8)), subtitles.deadlines());
		assertEquals(StremioResponseParser.MAX_RESPONSE_BYTES, subtitles.maxBodyBytes());
	}

	@Test
	public void unknownResourceKeepsUnsupportedCapabilityMapping() {
		var error = assertThrows(StremioIntegrationException.class,
				() -> StremioProtocolRequestPolicy.forResource("unknown"));
		assertEquals(StremioIntegrationException.Code.UNSUPPORTED_CAPABILITY, error.code());
	}

	private static void assertPolicy(String resource, Duration fresh, Duration stale,
			HttpDeadlines deadlines) {
		var policy = StremioProtocolRequestPolicy.forResource(resource);
		assertEquals(fresh, policy.cachePolicy().freshFor());
		assertEquals(stale, policy.cachePolicy().staleFor());
		assertSame(deadlines, policy.deadlines());
		assertEquals(StremioResponseParser.MAX_RESPONSE_BYTES, policy.maxBodyBytes());
	}
}
