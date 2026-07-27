package me.aap.fermata.addon.stremio.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;

import org.junit.Test;

public class RequestEncoderTest {
	@Test
	public void encodesUnicodeAndReservedCharactersOnce() {
		var path = RequestEncoder.encodePath("meta", "movie", "id/Phim Việt?x=1&y=2", Map.of());

		assertEquals("/meta/movie/id%2FPhim%20Vi%E1%BB%87t%3Fx%3D1%26y%3D2.json", path);
		assertFalse(path.contains("%252F"));
	}

	@Test
	public void sortsExtraKeysAndPreservesRepeatedValueOrder() {
		var extras = new HashMap<String, Object>();
		extras.put("skip", 100);
		extras.put("genre", List.of("Sci-Fi", "Drama"));
		extras.put("search", "game of thrones & more");

		assertEquals(
				"/catalog/series/top/genre=Sci-Fi&genre=Drama&search=game%20of%20thrones%20%26%20more&skip=100.json",
				RequestEncoder.encodePath("catalog", "series", "top", extras));
	}

	@Test
	public void omitsEmptyExtraSegment() {
		var extras = new LinkedHashMap<String, Object>();
		extras.put("search", "");
		extras.put("genre", List.of());
		extras.put("skip", null);

		assertEquals("/catalog/movie/top.json",
				RequestEncoder.encodePath("catalog", "movie", "top", extras));
		assertEquals("/catalog/movie/top.json",
				RequestEncoder.encodePath(new StremioRequest("catalog", "movie", "top")));
	}

	@Test
	public void resolvingManifestUrlDoesNotEncodeRouteTwice() {
		var uri = RequestEncoder.resolve(
				URI.create("https://example.org/user-token/manifest.json?key=redacted"),
				new StremioRequest("stream", "movie", "id/part"));

		assertEquals("https://example.org/user-token/stream/movie/id%2Fpart.json?key=redacted",
				uri.toASCIIString());
		assertFalse(uri.toASCIIString().contains("%252F"));
	}

	@Test
	public void rejectsNonManifestTransportUrl() {
		assertThrows(IllegalArgumentException.class, () -> RequestEncoder.resolve(
				URI.create("https://example.org/configure"),
				new StremioRequest("meta", "movie", "tt123")));
	}
}
