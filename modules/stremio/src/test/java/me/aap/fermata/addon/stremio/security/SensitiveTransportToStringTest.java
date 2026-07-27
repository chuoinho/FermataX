package me.aap.fermata.addon.stremio.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import me.aap.fermata.addon.stremio.net.AddressKind;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.NormalizedEndpoint;
import me.aap.fermata.addon.stremio.net.RedirectDecision;
import me.aap.fermata.addon.stremio.net.ValidatedEndpoint;
import me.aap.fermata.addon.stremio.net.http.HttpDeadlines;
import me.aap.fermata.addon.stremio.net.http.HttpRequestSpec;
import me.aap.fermata.addon.stremio.net.http.HttpResponseData;
import me.aap.fermata.addon.stremio.net.http.TransportRequest;
import me.aap.fermata.addon.stremio.ui.config.StremioConfigResourceLoader;

import org.junit.Test;

public class SensitiveTransportToStringTest {
	private static final String SECRET = "transport-secret-42";

	@Test
	public void transportRecordsRedactUrlsHeadersAndBodies() throws Exception {
		URI uri = URI.create("https://provider.invalid/manifest.json?token=" + SECRET);
		Map<String, String> headers = Map.of("authorization", "Bearer " + SECRET);
		byte[] body = ("body-" + SECRET).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		InetAddress address = InetAddress.getByName("203.0.113.10");
		var normalized = new NormalizedEndpoint(uri, "https", "provider.invalid", 443,
				"https://provider.invalid");
		var endpoint = new ValidatedEndpoint(normalized, address, AddressKind.PUBLIC,
				List.of(address));

		List<Object> values = List.of(
				new HttpRequestSpec(uri, headers, 1024, NetworkConsent.STRICT,
						HttpDeadlines.DEFAULT, null),
				new TransportRequest(endpoint, headers, HttpDeadlines.DEFAULT, 1024),
				new HttpResponseData(200, uri, headers, body),
				new RedirectDecision(endpoint, headers),
				new StremioConfigResourceLoader.Response(200, uri, headers, body));

		for (Object value : values) {
			String text = value.toString();
			assertTrue(text, text.contains("redacted"));
			assertFalse(text, text.contains(SECRET));
			assertFalse(text, text.contains("provider.invalid"));
			assertFalse(text, text.contains("authorization"));
		}
	}
}
