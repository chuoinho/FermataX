package me.aap.fermata.addon.stremio.protocol.response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class StremioResponseParserTest {
	@Test
	public void parsesCatalogAndPreservesUnicodeExactly() {
		var response = StremioResponseParser.parseCatalog(fixture("catalog-unicode.json"));
		var meta = response.metas().get(0);

		assertEquals("Điện ảnh 東京 🎬", meta.name());
		assertEquals("https://images.example.invalid/Điện ảnh 東京.jpg", meta.poster());
		assertEquals("01:02:03", meta.runtime().text());
		assertEquals(3_723_000L, meta.runtime().milliseconds());
		assertEquals(List.of("Khoa học viễn tưởng", "日本映画"), meta.genres());
		assertThrows(UnsupportedOperationException.class, () -> response.metas().clear());
	}

	@Test
	public void parsesMovieMetaFixtureWithExactPresentationFields() {
		var meta = StremioResponseParser.parseMeta(fixture("meta-movie.json")).meta();

		assertEquals("Example Public Domain Movie", meta.name());
		assertEquals("https://images.example.invalid/posters/example-movie.jpg", meta.poster());
		assertEquals("72 min", meta.runtime().text());
		assertEquals(4_320_000L, meta.runtime().milliseconds());
		assertEquals("8.7", meta.imdbRating());
		assertEquals("en", meta.language());
		assertTrue(meta.videos().isEmpty());
	}

	@Test
	public void sortsSeriesNumericallyAndKeepsExactVideoValues() {
		var meta = StremioResponseParser.parseMeta(fixture("meta-series-unsorted.json")).meta();

		assertEquals(List.of("s1e1", "s1e2", "s1e11", "s2e10", "special"),
				meta.videos().stream().map(StremioVideo::id).toList());
		var unicode = meta.videos().get(2);
		assertEquals("Tập 十一", unicode.title());
		assertEquals("https://images.example.invalid/十一.jpg", unicode.thumbnail());
		var numericDuration = meta.videos().get(3).duration();
		assertEquals("2820000", numericDuration.text());
		assertEquals(2_820_000L, numericDuration.milliseconds());
		var textDuration = meta.videos().get(4).duration();
		assertEquals("00:12:30", textDuration.text());
		assertEquals(750_000L, textDuration.milliseconds());
	}

	@Test
	public void acceptsCinemetaEpisodeNameAsTitle() {
		var meta = StremioResponseParser.parseCatalog("""
				{"metas":[{"id":"tt11198330","type":"series","name":"Example Series",
				"videos":[{"id":"tt11198330:1:4","name":"Episode Four",
				"season":1,"episode":4,"overview":""}]}]}
				""").metas().get(0);

		assertEquals("Episode Four", meta.videos().get(0).title());
		assertNull(meta.videos().get(0).overview());
	}

	@Test
	public void parsesEverySupportedStreamTargetFixture() {
		var direct = StremioResponseParser.parseStreams(fixture("streams-direct.json"));
		assertEquals(3, direct.streams().size());
		assertEquals("Example Movie - HLS", direct.streams().get(0).title());
		assertEquals("https://media.example.invalid/example-movie/master.m3u8",
				assertType(DirectStreamTarget.class, direct.streams().get(0).target()).url());

		var youtube = StremioResponseParser.parseStreams(fixture("streams-ytid.json"));
		assertEquals("Example0001",
				assertType(YoutubeStreamTarget.class, youtube.streams().get(0).target()).videoId());

		var external = StremioResponseParser.parseStreams(fixture("streams-external.json"));
		assertEquals("https://player.example.invalid/watch/example-movie",
				assertType(ExternalStreamTarget.class, external.streams().get(0).target()).url());

		var torrent = StremioResponseParser.parseStreams(fixture("streams-infohash.json"));
		var infoHash = assertType(InfoHashStreamTarget.class, torrent.streams().get(0).target());
		assertEquals("0123456789abcdef0123456789abcdef01234567", infoHash.infoHash());
		assertEquals(Integer.valueOf(0), infoHash.fileIndex());
		assertEquals(2, infoHash.sources().size());
	}

	@Test
	public void parsesBoundedProxyHeadersAndBehaviorHints() {
		var stream = StremioResponseParser.parseStreams(
				fixture("streams-proxy-headers.json")).streams().get(0);
		var hints = stream.behaviorHints();

		assertTrue(hints.notWebReady());
		assertEquals("FermataX-Protocol-Fixture/1.0",
				hints.proxyHeaders().request().get("User-Agent"));
		assertEquals("application/vnd.apple.mpegurl",
				hints.proxyHeaders().response().get("Content-Type"));
		assertThrows(UnsupportedOperationException.class,
				() -> hints.proxyHeaders().request().put("X-Test", "value"));
	}

	@Test
	public void parsesModernArchiveUsenetHintsAndEmbeddedSubtitles() {
		var response = StremioResponseParser.parseStreams("""
				{"streams":[
				 {"nzbUrl":"https://files.example.invalid/item.nzb","fileIdx":2,
				  "fileMustInclude":"mkv","servers":["nntps://user:pass@news.invalid:563/4"]},
				 {"rarUrls":[{"url":"https://files.example.invalid/a.part1.rar","bytes":1234},
				               {"url":"https://files.example.invalid/a.part2.rar"}]},
				 {"url":"https://media.example.invalid/movie.mp4",
				  "subtitles":[{"id":"embedded-en","url":"https://subs.example.invalid/a.vtt","lang":"eng"}],
				  "behaviorHints":{"filename":"movie.mkv","countryWhitelist":["usa","vnm"]}}
				]}
				""");

		NzbStreamTarget nzb = assertType(NzbStreamTarget.class,
				response.streams().get(0).target());
		assertEquals(Integer.valueOf(2), nzb.fileIndex());
		assertEquals(1, nzb.servers().size());
		ArchiveStreamTarget archive = assertType(ArchiveStreamTarget.class,
				response.streams().get(1).target());
		assertEquals(ArchiveStreamTarget.Kind.RAR, archive.kind());
		assertEquals(Long.valueOf(1234), archive.sources().get(0).bytes());
		StremioStream direct = response.streams().get(2);
		assertEquals("movie.mkv", direct.behaviorHints().filename());
		assertEquals(List.of("usa", "vnm"), direct.behaviorHints().countryWhitelist());
		assertEquals("embedded-en", direct.subtitles().get(0).id());
	}

	@Test
	public void parsesAddonCatalogAndOptionalManifestDescription() {
		AddonCatalogResponse response = StremioResponseParser.parseAddonCatalog("""
				{"addons":[{"transportName":"http",
				 "transportUrl":"https://addon.example.invalid/manifest.json",
				 "manifest":{"id":"org.example.catalog","name":"Example","version":"1.0.0",
				  "types":["movie"],"resources":["stream"],"catalogs":[]},
				 "flags":{"official":true,"protected":false}}]}
				""");

		assertEquals(1, response.addons().size());
		StremioAddonCatalogEntry addon = response.addons().get(0);
		assertEquals("org.example.catalog", addon.manifest().id());
		assertEquals("", addon.manifest().description());
		assertTrue(addon.official());
	}

	@Test
	public void addonCatalogRejectsDuplicateAddonIdentities() {
		String manifest = """
				{"id":"org.example.same","name":"Same","version":"1.0.0",
				 "types":["movie"],"resources":["stream"],"catalogs":[]}
				""".trim();
		String response = "{\"addons\":[" +
				"{\"transportName\":\"one\",\"transportUrl\":\"https://one.invalid/manifest.json\"," +
				"\"manifest\":" + manifest + "}," +
				"{\"transportName\":\"two\",\"transportUrl\":\"https://two.invalid/manifest.json\"," +
				"\"manifest\":" + manifest + "}]}";

		StremioResponseException failure = assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseAddonCatalog(response));
		assertTrue(failure.getMessage().contains("Duplicate addon identity"));
	}

	@Test
	public void representsAmbiguousMissingAndInvalidTargetsSafely() {
		var streams = StremioResponseParser.parseStreams(
				fixture("streams-target-collisions.json")).streams();

		assertEquals(UnsupportedStreamTarget.Reason.MULTIPLE_TARGETS,
				assertType(UnsupportedStreamTarget.class, streams.get(0).target()).reason());
		assertEquals(UnsupportedStreamTarget.Reason.MISSING_TARGET,
				assertType(UnsupportedStreamTarget.class, streams.get(1).target()).reason());
		assertEquals(UnsupportedStreamTarget.Reason.INVALID_TARGET,
				assertType(UnsupportedStreamTarget.class, streams.get(2).target()).reason());
	}

	@Test
	public void parsesSubtitleFixture() {
		var subtitles = StremioResponseParser.parseSubtitles(fixture("subtitles.json")).subtitles();

		assertEquals(2, subtitles.size());
		assertEquals(new StremioSubtitle("example.movie.1-en-vtt",
				"https://subtitles.example.invalid/example-movie/en.vtt", "eng"), subtitles.get(0));
	}

	@Test
	public void rejectsMalformedHtmlMissingAndIdentityCollisions() {
		assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseCatalog(fixture("malformed-json.txt")));
		var html = assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseCatalog(fixture("html-auth-response.html")));
		assertTrue(html.getMessage().contains("HTML-like"));
		var missing = assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseCatalog(fixture("missing-required-fields.json")));
		assertEquals("$.metas[0].name", missing.field());
		var collision = assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseCatalog(fixture("catalog-collision.json")));
		assertTrue(collision.getMessage().contains("Duplicate meta identity"));
		var headers = assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseStreams(fixture("streams-header-collision.json")));
		assertTrue(headers.getMessage().contains("header collision"));
	}

	@Test
	public void enforcesBodyItemStringAndHeaderLimits() throws Exception {
		var marker = new JSONObject(fixture("oversized-response-marker.json"));
		assertEquals(StremioResponseParser.MAX_RESPONSE_BYTES + 1,
				marker.getJSONObject("_fixture").getInt("generatedBytes"));
		var oversized = new byte[StremioResponseParser.MAX_RESPONSE_BYTES + 1];
		Arrays.fill(oversized, (byte) 'x');
		assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseMeta(oversized));

		var items = new JSONArray();
		for (int i = 0; i <= StremioResponseParser.MAX_ITEMS; i++) {
			items.put(new JSONObject().put("id", "m" + i).put("type", "movie").put("name", "M" + i));
		}
		assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseCatalog(new JSONObject().put("metas", items).toString()));

		var longName = "x".repeat(StremioResponseParser.MAX_STRING_BYTES + 1);
		assertThrows(StremioResponseException.class, () -> StremioResponseParser.parseCatalog(
				new JSONObject().put("metas", new JSONArray().put(new JSONObject()
						.put("id", "m").put("type", "movie").put("name", longName))).toString()));

		var requestHeaders = new JSONObject();
		for (int i = 0; i <= StremioResponseParser.MAX_HEADERS; i++) requestHeaders.put("X-" + i, "v");
		var response = new JSONObject().put("streams", new JSONArray().put(new JSONObject()
				.put("url", "https://example.invalid/a")
				.put("behaviorHints", new JSONObject().put("proxyHeaders",
						new JSONObject().put("request", requestHeaders)))));
		assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseStreams(response.toString()));
	}

	@Test
	public void rejectsInvalidUtf8DeepNestingAndHeaderInjection() {
		assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseCatalog(new byte[]{(byte) 0xC3, (byte) 0x28}));

		var nested = new StringBuilder("{\"metas\":[]");
		for (int i = 0; i <= StremioResponseParser.MAX_NESTING_DEPTH; i++) nested.append(",\"x\":{");
		nested.append("\"v\":1");
		for (int i = 0; i <= StremioResponseParser.MAX_NESTING_DEPTH; i++) nested.append('}');
		nested.append('}');
		assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseCatalog(nested.toString()));

		var injected = """
				{"streams":[{"url":"https://example.invalid/video","behaviorHints":{
				  "proxyHeaders":{"request":{"X-Safe":"value\\r\\nInjected: true"}}
				}}]}
				""";
		var error = assertThrows(StremioResponseException.class,
				() -> StremioResponseParser.parseStreams(injected));
		assertTrue(error.getMessage().contains("control character"));
	}

	@Test
	public void missingOptionalFieldsRemainAbsentAndNoDtoRetainsRawJson() {
		var meta = StremioResponseParser.parseCatalog("""
				{"metas":[{"id":"minimal","type":"movie","name":"Minimal"}]}
				""").metas().get(0);

		assertNull(meta.poster());
		assertNull(meta.runtime());
		assertTrue(meta.genres().isEmpty());
		assertTrue(meta.videos().isEmpty());
		for (var type : List.of(CatalogResponse.class, MetaResponse.class, StremioMeta.class,
				StremioVideo.class, StreamResponse.class, StremioStream.class,
				SubtitleResponse.class, StremioSubtitle.class, StreamBehaviorHints.class,
				ProxyHeaders.class)) {
			assertFalse(Arrays.stream(type.getDeclaredFields())
					.filter(field -> !Modifier.isStatic(field.getModifiers()))
					.anyMatch(field -> JSONObject.class.isAssignableFrom(field.getType()) ||
							JSONArray.class.isAssignableFrom(field.getType())));
		}
	}

	private static String fixture(String name) {
		try (var input = StremioResponseParserTest.class.getResourceAsStream("/stremio/" + name)) {
			if (input == null) throw new AssertionError("Missing fixture: " + name);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static <T> T assertType(Class<T> type, Object value) {
		assertTrue("Expected " + type.getName() + " but was " + value.getClass().getName(),
				type.isInstance(value));
		return type.cast(value);
	}
}
