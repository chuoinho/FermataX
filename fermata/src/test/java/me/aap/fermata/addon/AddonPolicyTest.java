package me.aap.fermata.addon;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import me.aap.utils.pref.BasicPreferenceStore;

public class AddonPolicyTest {
	@Test
	public void freshInstallEnablesDefaultsWithoutOverwritingExplicitChoice() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		AddonInfo explicit = firstDefaultAddon();
		store.applyBooleanPref(false, explicit.enabledPref, false);

		AddonManager.enableAddonsByDefault(store);

		assertFalse(store.getBooleanPref(explicit.enabledPref));
		for (AddonInfo info : AddonRegistry.get().getAvailable()) {
			if (info.enableByDefault && (info != explicit)) {
				assertTrue(info.className, store.getBooleanPref(info.enabledPref));
			}
		}
	}

	@Test
	public void completedDefaultMigrationDoesNotReenableAddonOnUpdate() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		AddonInfo info = firstDefaultAddon();
		AddonManager.enableAddonsByDefault(store);
		store.applyBooleanPref(false, info.enabledPref, false);

		AddonManager.enableAddonsByDefault(store);

		assertFalse(store.getBooleanPref(info.enabledPref));
	}

	@Test
	public void addonStatePriorityMatchesRuntimeManager() {
		assertEquals(AddonState.DISABLED,
				AddonManager.resolveState(false, true, true, true, true));
		assertEquals(AddonState.DISABLED,
				AddonManager.resolveState(true, false, true, true, true));
		assertEquals(AddonState.LOADED,
				AddonManager.resolveState(true, true, true, true, true));
		assertEquals(AddonState.FAILED,
				AddonManager.resolveState(true, true, false, true, true));
		assertEquals(AddonState.LOADING,
				AddonManager.resolveState(true, true, false, false, true));
		assertEquals(AddonState.ENABLED_PENDING,
				AddonManager.resolveState(true, true, false, false, false));
	}

	@Test
	public void registryOnlyIndexesUnambiguousModuleAliases() {
		AddonInfo one = info("shared", "test.One", 1, "depA, depB");
		AddonInfo two = info("shared", "test.Two", 2, "");
		AddonInfo unique = info("unique", "test.Unique", 3, "");
		AddonRegistry registry = new AddonRegistry(new AddonInfo[]{one, two, unique});

		assertNull(registry.get("shared"));
		assertSame(one, registry.get("test.One"));
		assertSame(two, registry.get(2));
		assertSame(unique, registry.get("unique"));
		assertThrows(RuntimeException.class, () -> registry.require("shared"));
		assertArrayEquals(new String[]{"depA", "depB"}, one.depends);
	}

	@Test
	public void generatedFragmentAddonsDeclareExplicitUiCapabilities() {
		assertCapabilities("me.aap.fermata.addon.tv.TvAddon", AddonCapability.TV);
		assertCapabilities("me.aap.fermata.addon.radio.RadioAddon", AddonCapability.RADIO);
		assertCapabilities("me.aap.fermata.addon.podcast.PodcastAddon", AddonCapability.PODCAST);
		assertCapabilities("me.aap.fermata.addon.audiobook.AudiobookAddon",
				AddonCapability.AUDIOBOOK);
		assertCapabilities("me.aap.fermata.addon.web.yt.YoutubeAddon", AddonCapability.YOUTUBE);
		assertCapabilities("me.aap.fermata.addon.web.WebBrowserAddon", AddonCapability.WEB);
		assertCapabilities("me.aap.fermata.addon.felex.FelexAddon", AddonCapability.FELEX);
		assertCapabilities("me.aap.fermata.addon.chat.ChatAddon", AddonCapability.CHATGPT,
				AddonCapability.VOICE_SEARCH);
		assertEquals("tv", AddonRegistry.get().require(
				"me.aap.fermata.addon.tv.TvAddon").voiceTarget);
		assertEquals("youtube", AddonRegistry.get().require(
				"me.aap.fermata.addon.web.yt.YoutubeAddon").voiceTarget);
		assertEquals("chatgpt", AddonRegistry.get().require(
				"me.aap.fermata.addon.chat.ChatAddon").voiceTarget);
	}

	@Test
	public void moduleUninstallOnlyWaitsForAnotherRetainedAddonInSameModule() {
		AddonInfo removed = info("shared", "test.Removed", 1, "");
		AddonInfo sibling = info("shared", "test.Sibling", 2, "");
		AddonInfo unrelated = info("other", "test.Other", 3, "");

		assertTrue(AddonManager.shouldUninstallModule(removed,
				Arrays.asList(removed, sibling, unrelated), info -> false));
		assertTrue(AddonManager.shouldUninstallModule(removed,
				Arrays.asList(removed, unrelated), info -> true));
		assertFalse(AddonManager.shouldUninstallModule(removed,
				Arrays.asList(removed, sibling, unrelated), info -> info == sibling));
		assertTrue(AddonManager.shouldUninstallModule(removed,
				Collections.singletonList(removed), info -> true));
	}

	@Test
	public void dependencyResolverReturnsDeduplicatedTopologicalOrder() {
		AddonInfo shared = info("shared", "test.Shared", 1, "");
		AddonInfo left = info("left", "test.Left", 2, "shared");
		AddonInfo right = info("right", "test.Right", 3, "shared");
		AddonInfo root = info("root", "test.Root", 4, "left,right");
		AddonDependencyResolver resolver = new AddonDependencyResolver(
				new AddonRegistry(new AddonInfo[]{root, right, shared, left}));

		assertEquals(List.of(shared, left, right, root), resolver.resolveInstallOrder(root));
	}

	@Test
	public void dependencyResolverRejectsCyclesBeforeInstallationStarts() {
		AddonInfo left = info("left", "test.Left", 1, "right");
		AddonInfo right = info("right", "test.Right", 2, "left");
		AddonDependencyResolver resolver =
				new AddonDependencyResolver(new AddonRegistry(new AddonInfo[]{left, right}));

		IllegalStateException error =
				assertThrows(IllegalStateException.class, () -> resolver.resolveInstallOrder(left));
		assertTrue(error.getMessage().contains("test.Left -> test.Right -> test.Left"));
	}

	@Test
	public void dependencyResolverRejectsMissingDependencies() {
		AddonInfo root = info("root", "test.Root", 1, "missing");
		AddonDependencyResolver resolver =
				new AddonDependencyResolver(new AddonRegistry(new AddonInfo[]{root}));

		RuntimeException error =
				assertThrows(RuntimeException.class, () -> resolver.resolveInstallOrder(root));
		assertTrue(error.getMessage().contains("Addon not found: missing"));
	}

	@Test
	public void transitiveDependencyIsRetainedByEnabledRoot() {
		AddonInfo dependency = info("dependency", "test.Dependency", 1, "");
		AddonInfo middle = info("middle", "test.Middle", 2, "dependency");
		AddonInfo root = info("root", "test.Root", 3, "middle");
		AddonDependencyResolver resolver = new AddonDependencyResolver(
				new AddonRegistry(new AddonInfo[]{dependency, middle, root}));

		assertTrue(resolver.isRequiredBy(dependency, List.of(dependency, middle, root),
				candidate -> candidate == root));
		assertTrue(resolver.isRequiredBy(middle, List.of(dependency, middle, root),
				candidate -> candidate == root));
		assertFalse(resolver.isRequiredBy(root, List.of(dependency, middle, root),
				candidate -> candidate == root));
	}

	@Test
	public void deliveredRootActivatesRegisteredDependenciesAndIgnoresLegacyModuleOnlyEntry() {
		AddonInfo dependency = info("dependency", "test.Dependency", 1, "");
		AddonInfo root = info("root", "test.Root", 2, "legacyModule,dependency");
		AddonDependencyResolver resolver = new AddonDependencyResolver(
				new AddonRegistry(new AddonInfo[]{dependency, root}));

		assertEquals(List.of(dependency, root), resolver.resolveInstallOrder(root, true));
		assertThrows(RuntimeException.class, () -> resolver.resolveInstallOrder(root));
	}

	private static AddonInfo firstDefaultAddon() {
		for (AddonInfo info : AddonRegistry.get().getAvailable()) {
			if (info.enableByDefault) return info;
		}
		throw new AssertionError("No default addon in build configuration");
	}

	private static void assertCapabilities(String className, AddonCapability... role) {
		AddonInfo info = AddonRegistry.get().require(className);
		assertTrue(className, info.hasCapability(AddonCapability.DASHBOARD));
		assertTrue(className, info.hasCapability(AddonCapability.NAVIGATION));
		for (AddonCapability capability : role)
			assertTrue(className, info.hasCapability(capability));
	}

	private static AddonInfo info(String module, String className, int id, String depends) {
		return new AddonInfo(module, className, id, id, id, id,
				true, true, false, false, depends);
	}
}
