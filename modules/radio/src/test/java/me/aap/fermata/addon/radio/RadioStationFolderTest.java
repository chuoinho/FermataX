package me.aap.fermata.addon.radio;

import static me.aap.utils.async.Completed.completed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RadioStationFolderTest {

	@Test
	public void popularAndTopVotedDoNotHaveLoadMoreItem() throws Exception {
		RadioRootItem root = createMockRootWithStations(100);

		RadioStationFolder popular = RadioStationFolder.popular(root);
		List<Item> popularChildren = popular.listChildren().get();
		assertEquals(100, popularChildren.size());
		assertFalse("Popular should not have load more item",
				hasLoadMore(popularChildren));

		RadioStationFolder topVoted = RadioStationFolder.topVoted(root);
		List<Item> topVotedChildren = topVoted.listChildren().get();
		assertEquals(100, topVotedChildren.size());
		assertFalse("Top Voted should not have load more item",
				hasLoadMore(topVotedChildren));

		RadioBrowserApi.DirectoryEntry vnEntry = new RadioBrowserApi.DirectoryEntry("Vietnam", "VN", 150);
		RadioStationFolder country = RadioStationFolder.country(root, vnEntry);
		List<Item> countryChildren = country.listChildren().get();
		assertEquals(101, countryChildren.size());
		assertTrue("Country with 100 stations should have load more item",
				hasLoadMore(countryChildren));
	}

	@Test
	public void offset200PageReconstructedCorrectlyPreservingCountryName() throws Exception {
		RadioRootItem root = createMockRootWithStations(100);

		// Page 1: Country "Vietnam"
		RadioBrowserApi.DirectoryEntry vnEntry = new RadioBrowserApi.DirectoryEntry("Vietnam", "VN", 250);
		RadioStationFolder page1 = RadioStationFolder.country(root, vnEntry);
		assertEquals("Vietnam", page1.getName());

		// Page 2: "Load more" clicked
		List<Item> page1Items = page1.listChildren().get();
		Item moreItem1 = page1Items.get(page1Items.size() - 1);
		assertTrue(moreItem1 instanceof RadioStationFolder);
		RadioStationFolder page2 = (RadioStationFolder) moreItem1;
		assertEquals("Vietnam", page2.getName());

		// Page 3: "Load more" clicked from Page 2
		List<Item> page2Items = page2.listChildren().get();
		Item moreItem2 = page2Items.get(page2Items.size() - 1);
		assertTrue(moreItem2 instanceof RadioStationFolder);
		RadioStationFolder page3 = (RadioStationFolder) moreItem2;
		assertEquals("Vietnam", page3.getName());

		// Test restoration of offset=200 ID through RadioRootItem.createFolder
		String page3Id = page3.getId();
		assertTrue("Page 3 ID should contain offset=200", page3Id.contains("offset=200"));
		assertTrue("Page 3 ID should contain encoded country name", page3Id.contains("Vietnam"));
		assertFalse("Page 3 ID should not contain Load more text", page3Id.contains("Load%20more") && !page3Id.contains("Vietnam"));

		Item restored = root.getItem("radio", page3Id).get();
		assertNotNull("Restored page should not be null", restored);
		assertTrue("Restored page should be RadioStationFolder", restored instanceof RadioStationFolder);
		RadioStationFolder restoredFolder = (RadioStationFolder) restored;
		assertEquals("Vietnam", restoredFolder.getName());
		assertEquals(200, restoredFolder.getOffset());
	}

	private static boolean hasLoadMore(List<Item> items) {
		if (items.isEmpty()) return false;
		Item last = items.get(items.size() - 1);
		return (last instanceof RadioStationFolder) &&
				(last.getIcon() == me.aap.fermata.R.drawable.more);
	}

	private static RadioRootItem createMockRootWithStations(int count) {
		Context base = RuntimeEnvironment.getApplication();
		Resources customRes = new Resources(base.getAssets(), base.getResources().getDisplayMetrics(),
				base.getResources().getConfiguration()) {
			@Override
			public String getString(int id) throws NotFoundException {
				if (id == R.string.radio_popular) return "Popular";
				if (id == R.string.radio_top_voted) return "Top Voted";
				if (id == R.string.radio_near_you) return "Near you";
				if (id == R.string.radio_load_more) return "Load more";
				try {
					return super.getString(id);
				} catch (Exception ex) {
					return "String_" + id;
				}
			}

			@Override
			public String getString(int id, Object... formatArgs) throws NotFoundException {
				if (id == R.string.radio_station_count) {
					return (formatArgs != null && formatArgs.length > 0) ? formatArgs[0] + " stations" : "stations";
				}
				try {
					return super.getString(id, formatArgs);
				} catch (Exception ex) {
					return "String_" + id;
				}
			}
		};
		Context context = new ContextWrapper(base) {
			@Override
			public Resources getResources() {
				return customRes;
			}
		};
		MediaLib lib = (MediaLib) Proxy.newProxyInstance(
				MediaLib.class.getClassLoader(),
				new Class<?>[]{MediaLib.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getContext" -> context;
					case "getId" -> "Radio";
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					default -> null;
				});

		List<RadioBrowserStation> dummyStations = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			dummyStations.add(new RadioBrowserStation("uuid-" + i, "Station " + i,
					"http://stream.test/" + i, "", "", "", "", "Vietnam", "VN",
					"Vietnamese", "mp3", 128, 10, 100));
		}

		RadioBrowserApi fakeApi = new RadioBrowserApi() {
			@Override
			FutureSupplier<List<RadioBrowserStation>> getPopularStations(int limit) {
				return completed(dummyStations.subList(0, Math.min(limit, dummyStations.size())));
			}

			@Override
			FutureSupplier<List<RadioBrowserStation>> getTopVotedStations(int limit) {
				return completed(dummyStations.subList(0, Math.min(limit, dummyStations.size())));
			}

			@Override
			FutureSupplier<List<RadioBrowserStation>> getCountryStations(String countryCode, int offset, int limit) {
				return completed(dummyStations.subList(0, Math.min(limit, dummyStations.size())));
			}
		};

		return new RadioRootItem(lib, fakeApi, new RadioSourceStore(context));
	}
}
