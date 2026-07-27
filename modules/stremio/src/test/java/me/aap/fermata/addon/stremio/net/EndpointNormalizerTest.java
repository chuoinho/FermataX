package me.aap.fermata.addon.stremio.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.net.URI;

import org.junit.Test;

public class EndpointNormalizerTest {
	@Test
	public void normalizesSchemeHostDefaultPortAndEmptyPath() throws Exception {
		var endpoint = EndpointNormalizer.normalize(URI.create("HTTPS://EXAMPLE.COM.:443"));

		assertEquals("https", endpoint.scheme());
		assertEquals("example.com", endpoint.host());
		assertEquals(443, endpoint.port());
		assertEquals("https://example.com", endpoint.origin());
		assertEquals("https://example.com/", endpoint.uri().toASCIIString());
	}

	@Test
	public void preservesEscapedPathAndNormalizesNonDefaultPort() throws Exception {
		var endpoint = EndpointNormalizer.normalize(
				URI.create("http://Example.com:8080/token%20value/manifest.json?q=a%20b"));

		assertEquals("http://example.com:8080", endpoint.origin());
		assertEquals("http://example.com:8080/token%20value/manifest.json?q=a%20b",
				endpoint.uri().toASCIIString());
	}

	@Test
	public void preservesBracketedIpv6Origin() throws Exception {
		var endpoint = EndpointNormalizer.normalize(URI.create("https://[2001:db8::1]:8443/a"));

		assertEquals("2001:db8::1", endpoint.host());
		assertEquals("https://[2001:db8::1]:8443", endpoint.origin());
	}

	@Test
	public void normalizesInternationalHostName() throws Exception {
		var endpoint = EndpointNormalizer.normalize(
				URI.create("https://b\u00fccher.example.invalid/manifest.json"));

		assertEquals("xn--bcher-kva.example.invalid", endpoint.host());
		assertEquals("https://xn--bcher-kva.example.invalid/manifest.json", endpoint.uri().toASCIIString());
	}

	@Test
	public void rejectsUnsupportedOrAmbiguousUrls() {
		assertReason(NetworkPolicyViolation.Reason.UNSUPPORTED_SCHEME, "file:///tmp/manifest.json");
		assertReason(NetworkPolicyViolation.Reason.INVALID_URL, "https://user:pass@example.com/a");
		assertReason(NetworkPolicyViolation.Reason.INVALID_URL, "https://example.com/a#fragment");
		assertReason(NetworkPolicyViolation.Reason.INVALID_URL, "https://example.com:0/a");
		assertReason(NetworkPolicyViolation.Reason.INVALID_URL, "https://example.com:not-a-port/a");
		assertReason(NetworkPolicyViolation.Reason.INVALID_URL, "/relative/path");
	}

	private static void assertReason(NetworkPolicyViolation.Reason reason, String value) {
		var error = assertThrows(NetworkPolicyViolation.class,
				() -> EndpointNormalizer.normalize(URI.create(value)));
		assertEquals(reason, error.reason());
	}
}
