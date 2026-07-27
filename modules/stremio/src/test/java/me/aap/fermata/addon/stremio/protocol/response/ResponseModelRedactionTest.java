package me.aap.fermata.addon.stremio.protocol.response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ResponseModelRedactionTest {
	private static final String SECRET_URL = "https://user:password@example.invalid/token/secret.m3u8?key=value";

	@Test
	public void streamModelsNeverRenderUrlsHeadersOrProviderText() {
		ProxyHeaders headers = new ProxyHeaders(
				Map.of("Authorization", "Bearer secret", "Referer", SECRET_URL),
				Map.of("Set-Cookie", "session=secret"));
		StremioStream stream = new StremioStream(
				SECRET_URL, SECRET_URL, SECRET_URL,
				new DirectStreamTarget(SECRET_URL),
				new StreamBehaviorHints(false, SECRET_URL, "secret-hash", 42L, headers));
		StreamResponse response = new StreamResponse(List.of(stream));

		assertRedacted(new DirectStreamTarget(SECRET_URL));
		assertRedacted(new ExternalStreamTarget(SECRET_URL));
		assertRedacted(new InfoHashStreamTarget("secret-hash", 0, List.of(SECRET_URL)));
		assertRedacted(headers);
		assertRedacted(stream.behaviorHints());
		assertRedacted(stream);
		assertRedacted(response);
	}

	@Test
	public void metadataAndSubtitleModelsNeverRenderMediaUrls() {
		StremioVideo video = new StremioVideo("id", "title", 1, 2, null,
				SECRET_URL, SECRET_URL, null);
		StremioMeta meta = new StremioMeta("id", "movie", "name", SECRET_URL, null,
				SECRET_URL, SECRET_URL, SECRET_URL, null, null, List.of(), null, List.of(video));
		StremioSubtitle subtitle = new StremioSubtitle("id", SECRET_URL, "en");

		assertRedacted(video);
		assertRedacted(meta);
		assertRedacted(new CatalogResponse(List.of(meta)));
		assertRedacted(new MetaResponse(meta));
		assertRedacted(subtitle);
		assertRedacted(new SubtitleResponse(List.of(subtitle)));
		assertRedacted(new StremioDuration(SECRET_URL, StremioDuration.UNKNOWN));
	}

	@Test
	public void redactedRecordsKeepDataAndValueEquality() {
		DirectStreamTarget first = new DirectStreamTarget(SECRET_URL);
		DirectStreamTarget second = new DirectStreamTarget(SECRET_URL);
		ProxyHeaders headers = new ProxyHeaders(Map.of("Authorization", "secret"), Map.of());

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertEquals(SECRET_URL, first.url());
		assertEquals("secret", headers.request().get("Authorization"));
	}

	private static void assertRedacted(Object value) {
		String text = value.toString();
		assertFalse(text, text.contains("https://"));
		assertFalse(text, text.contains("example.invalid"));
		assertFalse(text, text.contains("password"));
		assertFalse(text, text.contains("Bearer"));
		assertFalse(text, text.contains("Authorization"));
		assertFalse(text, text.contains("secret"));
	}
}
