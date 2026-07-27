package me.aap.fermata.addon.stremio.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ManifestValidatorTest {
	@Test
	public void parsesStringAndObjectResources() {
		var manifest = ManifestValidator.parse("""
				{
				  "id": "org.example.media",
				  "name": "Example",
				  "description": "Fixture provider",
				  "version": "1.2.3",
				  "types": ["movie", "series"],
				  "idPrefixes": ["tt"],
				  "resources": [
				    "meta",
				    {"name":"stream","types":["series"],"idPrefixes":["series:"]},
				    {"name":"subtitles","types":["movie"]}
				  ],
				  "catalogs": [{
				    "type":"movie",
				    "id":"top",
				    "name":"Popular",
				    "extra":[
				      {"name":"search","isRequired":false},
				      {"name":"genre","options":["Drama","Sci-Fi"],"optionsLimit":1}
				    ]
				  }],
				  "behaviorHints": {"configurable":true,"p2p":false},
				  "unknownFutureField": {"safe":"ignored"}
				}
				""");

		assertEquals("org.example.media", manifest.id());
		assertEquals(3, manifest.resources().size());
		assertTrue(manifest.resources().get(0).inheritsManifestConstraints());
		assertFalse(manifest.resources().get(1).inheritsManifestConstraints());
		assertFalse(manifest.resources().get(2).idPrefixes().declared());
		assertEquals("Popular", manifest.catalogs().get(0).displayName());
		assertEquals(2, manifest.catalogs().get(0).extras().size());
		assertEquals("Sci-Fi", manifest.catalogs().get(0).extra("genre").orElseThrow()
				.options().get(1));
		assertTrue(manifest.behaviorHints().configurable());
		assertFalse(manifest.behaviorHints().p2p());
	}

	@Test
	public void normalizesLegacyCatalogExtrasAndMissingCatalogName() {
		var manifest = ManifestValidator.parse("""
				{
				  "id":"org.example.legacy",
				  "name":"Legacy",
				  "description":"Legacy v3 catalog fields",
				  "version":"1.0.0",
				  "types":["movie"],
				  "resources":["catalog"],
				  "catalogs":[{
				    "type":"movie",
				    "id":"top",
				    "extraSupported":["search","genre","skip"],
				    "extraRequired":["search"],
				    "genres":["Drama","Comedy"]
				  }]
				}
				""");

		var catalog = manifest.catalogs().get(0);
		assertEquals("top", catalog.displayName());
		assertTrue(catalog.extra("search").orElseThrow().required());
		assertEquals(2, catalog.extra("genre").orElseThrow().options().size());
		assertTrue(catalog.extra("skip").isPresent());
	}

	@Test
	public void parsedModelsAreImmutable() {
		var manifest = ManifestValidator.parse(minimalManifest());
		assertThrows(UnsupportedOperationException.class,
				() -> manifest.types().add("channel"));
		assertThrows(UnsupportedOperationException.class,
				() -> manifest.resources().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> manifest.catalogs().get(0).extras().add(null));
	}

	@Test
	public void rejectsMalformedAndHtmlLikeResponsesClearly() {
		var html = assertThrows(ManifestValidationException.class,
				() -> ManifestValidator.parse("<html><body>Unauthorized</body></html>"));
		assertEquals("$", html.field());
		assertTrue(html.getMessage().contains("HTML-like"));

		var malformed = assertThrows(ManifestValidationException.class,
				() -> ManifestValidator.parse("{\"id\":"));
		assertEquals("$", malformed.field());
		assertTrue(malformed.getMessage().contains("Malformed"));
	}

	@Test
	public void acceptsOptionalDescriptionAndRejectsDuplicateCatalogs() {
		var withoutDescription = ManifestValidator.parse(minimalManifest().replace(
				"\"description\":\"Fixture\",", ""));
		assertEquals("", withoutDescription.description());

		var duplicate = assertThrows(ManifestValidationException.class,
				() -> ManifestValidator.parse(minimalManifest().replace(
						"{\"type\":\"movie\",\"id\":\"top\",\"name\":\"Top\"}",
						"{\"type\":\"movie\",\"id\":\"top\",\"name\":\"Top\"}," +
								"{\"type\":\"movie\",\"id\":\"top\",\"name\":\"Again\"}")));
		assertTrue(duplicate.getMessage().contains("Duplicate catalog"));
	}

	@Test
	public void rejectsManifestLargerThanConfiguredLimit() {
		var oversized = "{\"padding\":\"" +
				"a".repeat(ManifestValidator.MAX_MANIFEST_BYTES) + "\"}";
		var error = assertThrows(ManifestValidationException.class,
				() -> ManifestValidator.parse(oversized));
		assertTrue(error.getMessage().contains("exceeds 524288 bytes"));
	}

	@Test
	public void rejectsExcessiveCollectionAndNestingBeforeDomainConstruction() {
		String resources = java.util.stream.IntStream.rangeClosed(
				0, ManifestValidator.MAX_RESOURCES).mapToObj(i -> "\"r" + i + "\"")
				.collect(java.util.stream.Collectors.joining(","));
		String tooManyResources = minimalManifest().replace(
				"\"resources\":[\"catalog\",\"meta\"]",
				"\"resources\":[" + resources + "]");
		ManifestValidationException count = assertThrows(ManifestValidationException.class,
				() -> ManifestValidator.parse(tooManyResources));
		assertTrue(count.getMessage().contains("exceeds"));

		String nested = "0";
		for (int i = 0; i <= ManifestValidator.MAX_JSON_DEPTH; i++) nested = "[" + nested + "]";
		String base = minimalManifest().trim();
		String tooDeep = base.substring(0, base.length() - 1) +
				",\"unknown\":" + nested + "}";
		ManifestValidationException depth = assertThrows(ManifestValidationException.class,
				() -> ManifestValidator.parse(tooDeep));
		assertTrue(depth.getMessage().contains("nesting"));
	}

	private static String minimalManifest() {
		return """
				{"id":"org.example","name":"Example","description":"Fixture","version":"1.0.0",
				 "types":["movie"],"resources":["catalog","meta"],
				 "catalogs":[{"type":"movie","id":"top","name":"Top"}]}
				""";
	}
}
