package me.aap.fermata.addon.stremio.protocol;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import me.aap.fermata.addon.stremio.protocol.model.ProviderCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CapabilityMatcherTest {
	@Test
	public void stringResourcesUseManifestTypesAndPrefixes() {
		var manifest = manifest();

		assertTrue(CapabilityMatcher.supports(manifest, "meta", "movie", "tt123"));
		assertFalse(CapabilityMatcher.supports(manifest, "meta", "series", "tt123"));
		assertFalse(CapabilityMatcher.supports(manifest, "meta", "movie", "custom:123"));
	}

	@Test
	public void objectResourcesOverrideManifestConstraints() {
		var manifest = manifest();

		assertTrue(CapabilityMatcher.supports(manifest, "stream", "series", "series:42"));
		assertFalse(CapabilityMatcher.supports(manifest, "stream", "movie", "tt123"));
		assertFalse(CapabilityMatcher.supports(manifest, "stream", "series", "tt123"));
		assertTrue(CapabilityMatcher.supports(manifest, "subtitles", "movie", "any-id"));
		assertFalse(CapabilityMatcher.supports(manifest, "empty-prefix", "movie", "tt123"));
	}

	@Test
	public void catalogUsesExactTypeAndIdAndIgnoresPrefixRules() {
		var manifest = manifest();

		assertTrue(CapabilityMatcher.supports(manifest, "catalog", "series", "featured"));
		assertFalse(CapabilityMatcher.supports(manifest, "catalog", "movie", "featured"));
		assertFalse(CapabilityMatcher.supports(manifest, "catalog", "series", "other"));
	}

	@Test
	public void disabledProviderNeverMatches() {
		var request = new StremioRequest("meta", "movie", "tt123");
		assertTrue(CapabilityMatcher.supports(new ProviderCapability(manifest(), true), request));
		assertFalse(CapabilityMatcher.supports(new ProviderCapability(manifest(), false), request));
	}

	@Test
	public void addonCatalogUsesDeclaredTypeAndIdOnly() {
		var manifest = ManifestValidator.parse("""
				{"id":"org.example.discovery","name":"Discovery","version":"1.0.0",
				 "types":["movie"],"resources":["addon_catalog"],
				 "catalogs":[],
				 "addonCatalogs":[{"type":"community","id":"all","name":"Community"}]}
				""");

		assertTrue(CapabilityMatcher.supports(manifest,
				new StremioRequest("addon_catalog", "community", "all")));
		assertTrue(CapabilityMatcher.supports(manifest,
				"addon_catalog", "community", "all"));
		assertFalse(CapabilityMatcher.supports(manifest,
				new StremioRequest("addon_catalog", "community", "other")));
		assertFalse(CapabilityMatcher.supports(manifest,
				new StremioRequest("addon_catalog", "community", "all", Map.of("skip", 1))));
	}

	@Test
	public void catalogEnforcesRequiredAndSupportedExtras() {
		var manifest = catalogManifest();

		assertFalse(CapabilityMatcher.supports(manifest,
				new StremioRequest("catalog", "movie", "searchable")));
		assertTrue(CapabilityMatcher.supports(manifest, new StremioRequest(
				"catalog", "movie", "searchable", Map.of("search", "FermataX"))));
		assertFalse(CapabilityMatcher.supports(manifest, new StremioRequest(
				"catalog", "movie", "searchable", Map.of("search", "FermataX", "token", "x"))));
		assertFalse(CapabilityMatcher.supports(manifest, new StremioRequest(
				"catalog", "movie", "searchable", Map.of("search", ""))));
	}

	@Test
	public void catalogEnforcesOptionsAndLimitsWithoutBreakingStandardExtras() {
		var manifest = catalogManifest();

		assertTrue(CapabilityMatcher.supports(manifest, new StremioRequest(
				"catalog", "movie", "searchable",
				Map.of("search", "car", "genre", "Drama", "skip", 100))));
		assertTrue(CapabilityMatcher.supports(manifest, new StremioRequest(
				"catalog", "movie", "searchable",
				Map.of("search", "car", "genre", new String[]{"Drama", "Comedy"}))));
		assertFalse(CapabilityMatcher.supports(manifest, new StremioRequest(
				"catalog", "movie", "searchable",
				Map.of("search", "car", "genre", "Horror"))));
		assertFalse(CapabilityMatcher.supports(manifest, new StremioRequest(
				"catalog", "movie", "searchable",
				Map.of("search", "car", "genre", List.of("Drama", "Comedy", "Drama")))));
	}

	private static me.aap.fermata.addon.stremio.protocol.model.StremioManifest manifest() {
		return ManifestValidator.parse("""
				{
				 "id":"org.example.matcher","name":"Matcher","description":"Fixture","version":"1.0.0",
				 "types":["movie"],"idPrefixes":["tt"],
				 "resources":[
				   "meta",
				   {"name":"stream","types":["series"],"idPrefixes":["series:"]},
				   {"name":"subtitles","types":["movie"]},
				   {"name":"empty-prefix","types":["movie"],"idPrefixes":[]}
				 ],
				 "catalogs":[{"type":"series","id":"featured","name":"Featured"}]
				}
				""");
	}

	private static me.aap.fermata.addon.stremio.protocol.model.StremioManifest catalogManifest() {
		return ManifestValidator.parse("""
				{
				 "id":"org.example.catalog","name":"Catalog","description":"Fixture","version":"1.0.0",
				 "types":["movie"],"resources":["catalog"],
				 "catalogs":[{
				   "type":"movie","id":"searchable","extra":[
				     {"name":"search","isRequired":true},
				     {"name":"genre","options":["Drama","Comedy"],"optionsLimit":2},
				     {"name":"skip"}
				   ]
				 }]
				}
				""");
	}
}
