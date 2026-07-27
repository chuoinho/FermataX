package me.aap.fermata.addon.stremio.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioUrlRedactorTest {
	@Test
	public void preservesOnlyOriginAndTerminalManifestIdentity() {
		String redacted = StremioUrlRedactor.forStorage(
				"https://driver:private@Catalog.Example.Invalid:8443/config/hidden/manifest.json" +
						"?type=movie&lang=en&token=hidden&api_key=also-hidden#session");

		assertEquals("https://catalog.example.invalid:8443/manifest.json", redacted);
		assertFalse(redacted.contains("driver"));
		assertFalse(redacted.contains("private"));
		assertFalse(redacted.contains("config"));
		assertFalse(redacted.contains("movie"));
		assertFalse(redacted.contains("hidden"));
		assertFalse(redacted.contains("session"));
	}

	@Test
	public void replacesNonManifestConfigurationWithOpaqueStableDigest() {
		String source = "https://catalog.example.invalid/config/secret-value/catalog.json?token=hidden";
		String redacted = StremioUrlRedactor.forStorage(source);

		assertTrue(redacted.matches("https://catalog\\.example\\.invalid/\\.stremio/[0-9a-f]{24}"));
		assertEquals(redacted, StremioUrlRedactor.forStorage(source));
		assertFalse(redacted.contains("config"));
		assertFalse(redacted.contains("secret"));
		assertFalse(redacted.contains("catalog.json"));
		assertFalse(redacted.contains("token"));
		assertFalse(redacted.contains("hidden"));
	}

	@Test
	public void neverRendersEncodedOrUnencodedPathSecrets() {
		String plain = StremioUrlRedactor.forStorage(
				"https://catalog.example.invalid/config/secret-token/resource");
		String encoded = StremioUrlRedactor.forStorage(
				"https://catalog.example.invalid/config/secret%2Dtoken/resource");

		assertTrue(plain.startsWith("https://catalog.example.invalid/.stremio/"));
		assertTrue(encoded.startsWith("https://catalog.example.invalid/.stremio/"));
		assertFalse(plain.contains("secret-token"));
		assertFalse(encoded.contains("secret%2Dtoken"));
		assertFalse(plain.equals(encoded));
	}

	@Test
	public void dropsUserInfoQueryAndFragmentEvenWhenPercentEncoded() {
		String redacted = StremioUrlRedactor.forStorage(
				"https://user%40mail:pass%3Aword@catalog.example.invalid/manifest.json" +
						"?api%5Fkey=secret%2Fvalue#access%2Dtoken");

		assertEquals("https://catalog.example.invalid/manifest.json", redacted);
		assertFalse(redacted.contains("user"));
		assertFalse(redacted.contains("pass"));
		assertFalse(redacted.contains("api"));
		assertFalse(redacted.contains("secret"));
		assertFalse(redacted.contains("access"));
	}

	@Test
	public void rejectsMalformedOrRelativeUrls() {
		assertNull(StremioUrlRedactor.forStorage("not a URL"));
		assertNull(StremioUrlRedactor.forStorage("/manifest.json"));
		assertNull(StremioUrlRedactor.forStorage("file:///manifest.json"));
		assertEquals("<invalid Stremio URL>", StremioUrlRedactor.forMessage("not a URL"));
	}
}
