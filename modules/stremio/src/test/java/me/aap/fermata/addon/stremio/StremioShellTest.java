package me.aap.fermata.addon.stremio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.MediaItemResolverAddon;
import me.aap.fermata.addon.VoiceSearchAddon;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioProviderItem;
import me.aap.fermata.addon.stremio.protocol.model.ManifestBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.model.PrefixConstraint;
import me.aap.fermata.addon.stremio.protocol.model.ResourceCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;
import me.aap.fermata.addon.stremio.session.StremioItemAvailability;
import me.aap.fermata.addon.stremio.session.StremioItemResolution;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;
import me.aap.utils.async.FutureSupplier;

public class StremioShellTest {
	@Test
	public void generatedRegistrationExposesOnlyStremioShellContracts() {
		StremioAddon addon = new StremioAddon();
		StremioFragment fragment = new StremioFragment();

		assertEquals(me.aap.fermata.R.id.stremio_fragment, addon.getAddonId());
		assertTrue(addon.getInfo().hasCapability(AddonCapability.DASHBOARD));
		assertTrue(addon.getInfo().hasCapability(AddonCapability.NAVIGATION));
		assertTrue(addon.getInfo().hasCapability(AddonCapability.STREMIO));
		assertTrue(addon.getInfo().hasResolverScheme("stremio"));
		assertTrue(addon.getInfo().enableByDefault);
		assertTrue(addon instanceof MediaItemResolverAddon);
		assertTrue(addon instanceof VoiceSearchAddon);
		assertTrue(addon.getInfo().hasCapability(AddonCapability.VOICE_SEARCH));
		assertEquals("stremio", addon.getInfo().voiceTarget);
		assertEquals("stremio", addon.getVoiceTarget());
		assertTrue(addon.isSupportedItem(addon.getRootItem(null)));
		assertFalse(addon.isSupportedItem(new ExtRoot("other", null)));
		assertTrue(fragment.isVideoModeSupported());
		assertFalse(fragment.isSplitViewSupported());
	}

	@Test
	public void actionIdsAreStableAndDistinct() {
		StremioRootItem root = new StremioRootItem(null);
		String add = StremioRootItem.actionId(StremioAction.ADD);
		String addons = StremioRootItem.actionId(StremioAction.ADDONS);

		assertEquals(AddonCapability.STREMIO, root.getRouteCapability());
		assertSame(root, root.getItem(null, StremioRootItem.ID).peek());
		assertEquals("stremio:action:add", add);
		assertEquals("stremio:action:addons", addons);
		assertNotEquals(add, addons);
	}

	@Test
	public void stableIdsSurviveTemporarilyDisabledExternalHandlers() {
		StremioSessionItem item = new StremioSessionItem("stremio:video:a",
				"stremio:content:a", "source-a", "Movie", "", null, 1_000L,
				"stremio:root", null, -1, -1);

		assertTrue(StremioAddon.retainMissingResolution(new StremioItemResolution(
				StremioItemAvailability.AVAILABLE, item)));
		assertTrue(StremioAddon.retainMissingResolution(new StremioItemResolution(
				StremioItemAvailability.PROVIDER_DISABLED, item)));
		assertFalse(StremioAddon.retainMissingResolution(new StremioItemResolution(
				StremioItemAvailability.PROVIDER_REMOVED, item)));
		assertFalse(StremioAddon.retainMissingResolution(StremioItemResolution.missing()));
	}

	@Test
	public void rootListsOnlyEnabledProvidersAndResolvesTheirStableIds() throws Exception {
		BrowseProvider enabled = provider("source-enabled", true, 0);
		BrowseProvider disabled = provider("source-disabled", false, 1);
		StremioRootItem root = new StremioRootItem(null, gateway(List.of(enabled, disabled)));

		var children = root.getUnsortedChildren().get();
		var providers = children.stream().filter(StremioProviderItem.class::isInstance).toList();

		assertEquals(1, providers.size());
		assertEquals("source-enabled",
				((StremioProviderItem) providers.get(0)).provider().sourceUuid());
		assertSame(providers.get(0), root.getItem(StremioRootItem.SCHEME,
				providers.get(0).getId()).get());
	}

	private static BrowseProvider provider(String sourceUuid, boolean enabled, int position) {
		StremioManifest manifest = new StremioManifest("org.fixture", "Fixture", "Fixture",
				"1.0.0", List.of("movie"), PrefixConstraint.unrestricted(),
				List.of(ResourceCapability.inherited("catalog")), List.of(),
				ManifestBehaviorHints.NONE);
		return new BrowseProvider(sourceUuid, "Fixture", manifest, enabled, position);
	}

	private static StremioItemGateway gateway(List<BrowseProvider> providers) {
		return new StremioItemGateway() {
			@Override
			public FutureSupplier<List<BrowseProvider>> providers() {
				return me.aap.utils.async.Completed.completed(providers);
			}

			@Override
			public FutureSupplier<List<me.aap.fermata.addon.stremio.browse.CatalogDescriptor>>
			catalogs(String sourceUuid) {
				return me.aap.utils.async.Completed.completedEmptyList();
			}

			@Override
			public FutureSupplier<me.aap.fermata.addon.stremio.browse.CatalogPage> catalog(
					me.aap.fermata.addon.stremio.browse.CatalogRoute route, String genre, int skip) {
				return me.aap.utils.async.Completed.completedNull();
			}

			@Override
			public FutureSupplier<me.aap.fermata.addon.stremio.browse.BrowseDetails> meta(
					me.aap.fermata.addon.stremio.browse.BrowseMedia media) {
				return me.aap.utils.async.Completed.completedNull();
			}

			@Override
			public FutureSupplier<me.aap.fermata.addon.stremio.browse.SearchResults> search(
					String query) {
				return me.aap.utils.async.Completed.completedNull();
			}

			@Override
			public FutureSupplier<me.aap.fermata.addon.stremio.playback.StreamAggregationResult>
			streams(me.aap.fermata.addon.stremio.playback.StreamAggregationRequest request) {
				return me.aap.utils.async.Completed.completedNull();
			}

			@Override
			public FutureSupplier<me.aap.fermata.addon.stremio.playback.PlaybackDescriptor> resolve(
					me.aap.fermata.addon.stremio.playback.PlaybackDescriptor.DescriptorRefreshRequest request) {
				return me.aap.utils.async.Completed.completedNull();
			}

			@Override
			public FutureSupplier<Void> saveProgress(
					me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity identity,
					long position, boolean completed) {
				return me.aap.utils.async.Completed.completedVoid();
			}
		};
	}
}
