package me.aap.fermata.addon.stremio.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

public class HttpValidatorPolicyTest {
	@Test
	public void boundsAndRemovesKnownShortSecrets() {
		assertEquals("\"public-v1\"", HttpValidatorPolicy.sanitize("\"public-v1\""));
		assertNull(HttpValidatorPolicy.sanitize("x".repeat(
				HttpValidatorPolicy.MAX_CHARS + 1)));
		assertNull(HttpValidatorPolicy.sanitize("value\r\ninjected"));
		assertNull(HttpValidatorPolicy.sanitize("opaque-aB12cd-value", List.of("aB12cd")));
	}
}
