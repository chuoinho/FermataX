package me.aap.fermata.addon.stremio.net;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.Map;

import org.junit.Test;

public class SensitiveHeaderPolicyTest {
	@Test
	public void stripsCredentialsAndConditionalValidatorsAcrossOrigins() throws Exception {
		NormalizedEndpoint source = EndpointNormalizer.normalize(
				URI.create("https://one.example.invalid/data"));
		NormalizedEndpoint target = EndpointNormalizer.normalize(
				URI.create("https://two.example.invalid/data"));
		Map<String, String> filtered = SensitiveHeaderPolicy.forRedirect(source, target,
				Map.of("authorization", "secret", "if-none-match", "opaque-secret",
						"if-modified-since", "yesterday", "accept", "application/json"));

		assertEquals(Map.of("accept", "application/json"), filtered);
	}
}
