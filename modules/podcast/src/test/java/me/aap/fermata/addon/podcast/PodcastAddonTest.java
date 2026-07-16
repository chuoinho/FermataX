package me.aap.fermata.addon.podcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;

@RunWith(RobolectricTestRunner.class)
@Config(application = FermataApplication.class)
public class PodcastAddonTest {
	@Test
	public void generatedRegistrationExposesOnlyPodcastContracts() {
		PodcastAddon addon = new PodcastAddon();

		assertEquals(me.aap.fermata.R.id.podcast_fragment, addon.getAddonId());
		assertTrue(addon.getInfo().hasCapability(AddonCapability.DASHBOARD));
		assertTrue(addon.getInfo().hasCapability(AddonCapability.NAVIGATION));
		assertTrue(addon.getInfo().hasCapability(AddonCapability.PODCAST));
		assertTrue(addon.getInfo().hasSettings);
		assertTrue(addon.getInfo().enableByDefault);
		assertTrue(addon.isSupportedItem(addon.getRootItem(null)));
		assertFalse(addon.isSupportedItem(new ExtRoot("other", null)));
	}

	@Test
	public void stopReleasesOnlyPodcastRuntimeRoot() {
		PodcastAddon addon = new PodcastAddon();
		PodcastRootItem before = addon.getRootItem(null);

		addon.stop();

		assertNotSame(before, addon.getRootItem(null));
	}

	@Test
	public void replacingMediaLibKeepsOldRootRepositoryUsableUntilStop() throws Exception {
		PodcastAddon addon = new PodcastAddon();
		DefaultMediaLib firstLib = new DefaultMediaLib(RuntimeEnvironment.getApplication());
		DefaultMediaLib secondLib = new DefaultMediaLib(RuntimeEnvironment.getApplication());
		PodcastRootItem first = addon.getRootItem(firstLib);
		PodcastRootItem second = addon.getRootItem(secondLib);

		assertNotSame(first, second);
		first.listChildren().get(5, SECONDS);
		second.listChildren().get(5, SECONDS);
		addon.stop();
	}

	@Test
	public void addActionRemainsAvailableOnEveryPodcastPage() {
		PodcastFragment fragment = new PodcastFragment();

		assertTrue(fragment.isAddSourceSupported());
		assertEquals(me.aap.fermata.R.drawable.playlist_add, fragment.getAddSourceIcon());
	}
}
