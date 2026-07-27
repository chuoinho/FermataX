package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.Test;

import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;

public class StremioProtocolRequestPlannerTest {
	@Test
	public void preservesWireUriAndExactCacheIdentity() {
		var request = new StremioRequest("catalog", "movie", "top",
				Map.of("skip", "10", "search", "Xin chao"));

		var plan = StremioProtocolRequestPlanner.plan(
				"stremio://provider.example.invalid/private/manifest.json?api=secret",
				request, "fingerprint", "org.example.provider", 42L, true, false);

		String requestUri = "https://provider.example.invalid/private/" +
				"catalog/movie/top/search=Xin%20chao&skip=10.json?api=secret";
		assertEquals(requestUri, plan.uri().toASCIIString());
		assertEquals("/catalog/movie/top/search=Xin%20chao&skip=10.json\u0000" +
				"fingerprint\u0000org.example.provider\u000042\u0000true\u0000false\u0000" + requestUri,
				plan.cacheIdentity());
	}

	@Test
	public void preservesSubtitleQueryEncoding() {
		var request = new StremioRequest("subtitles", "movie", "tt1",
				Map.of("filename", "Movie One.mkv", "videoSize", "123"));

		var plan = StremioProtocolRequestPlanner.plan(
				"https://provider.example.invalid/manifest.json", request,
				"fingerprint", "org.example.provider", 0L, false, false);

		assertEquals("https://provider.example.invalid/subtitles/movie/tt1.json?" +
				"filename=Movie%20One.mkv&videoSize=123", plan.uri().toASCIIString());
	}

	@Test
	public void rejectsTheSameInvalidManifestUrlShapesBeforeIo() {
		var request = new StremioRequest("meta", "movie", "tt1");
		for (String value : new String[]{null, "", "file:///manifest.json",
				"https://user@provider.example/manifest.json",
				"https://provider.example/base/../manifest.json",
				"https://provider.example/not-manifest.json"}) {
			assertThrows(IllegalArgumentException.class, () ->
					StremioProtocolRequestPlanner.plan(value, request,
							"fingerprint", "org.example.provider", 0L, false, false));
		}
	}
}
