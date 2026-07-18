package me.aap.fermata.addon.podcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.podcast.data.PodcastPlaybackSource;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;
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

	@Test
	public void snapshotProgressUpdatesTheLiveEpisodeBeforeTheNextResume() throws Exception {
		PodcastRootItem root = new PodcastRootItem(new DefaultMediaLib(
				RuntimeEnvironment.getApplication()));
		PodcastSubscription subscription = new PodcastSubscription("feed-key",
				"https://example.test/feed", null, "Road Show", "", "", "", null,
				"", "", false, null, null, 0, 0, 0);
		PodcastEpisodeRecord record = new PodcastEpisodeRecord("feed-key", "episode-key",
				"Road Show", "Episode One", "", "Host", "https://example.test/one.mp3",
				null, "audio/mpeg", "", null, 0, 120_000, 100, false, 0, 0, 0, null);
		AtomicLong storedPosition = new AtomicLong(-1);
		PodcastEpisodeItem episode = new PodcastEpisodeItem(
				new PodcastSubscriptionItem(root, subscription), record,
				new PodcastPlaybackSource(record.getMediaUrl(), null), "",
				(feedKey, episodeKey, position, played, lastPlayedMs) -> {
					storedPosition.set(position);
					return me.aap.utils.async.Completed.completedVoid();
				});

		PodcastAddon.persistEpisodeProgress(episode, 55_000, false).getOrThrow();

		assertEquals(55_000, episode.getResumePosition());
		assertEquals(55_000, root.getLib().getLastPlayedPosition(episode));
		assertEquals(55_000, storedPosition.get());
	}
}
