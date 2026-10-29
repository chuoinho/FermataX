package me.aap.fermata.addon.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;

public class RadioSearchResultMergerTest {

	private static BrowsableItem createDummyParent() {
		return (BrowsableItem) Proxy.newProxyInstance(
				BrowsableItem.class.getClassLoader(),
				new Class<?>[]{BrowsableItem.class},
				(proxy, method, args) -> null
		);
	}

	@Test
	public void mergesRemoteAfterLocalAndDeduplicatesByUuid() {
		BrowsableItem parent = createDummyParent();

		RadioBrowserStation s1 = new RadioBrowserStation("uuid-1", "Station One", "http://s1.mp3",
				"", "", "", "", "Vietnam", "VN", "Vietnamese", "MP3", 128, 10, 100);
		RadioBrowserStation s2 = new RadioBrowserStation("uuid-2", "Station Two", "http://s2.mp3",
				"", "", "", "", "Vietnam", "VN", "Vietnamese", "MP3", 128, 10, 100);
		RadioBrowserStation s2Dup = new RadioBrowserStation("uuid-2", "Station Two (Remote)", "http://s2-alt.mp3",
				"", "", "", "", "Vietnam", "VN", "Vietnamese", "MP3", 128, 10, 100);
		RadioBrowserStation s3 = new RadioBrowserStation("uuid-3", "Station Three", "http://s3.mp3",
				"", "", "", "", "Vietnam", "VN", "Vietnamese", "MP3", 128, 10, 100);

		RadioStationItem item1 = new RadioStationItem(parent, s1);
		RadioStationItem item2 = new RadioStationItem(parent, s2);
		RadioStationItem item2Remote = new RadioStationItem(parent, s2Dup);
		RadioStationItem item3Remote = new RadioStationItem(parent, s3);

		List<Item> local = List.of(item1, item2);
		List<Item> remote = List.of(item2Remote, item3Remote);

		List<Item> merged = RadioSearchResultMerger.merge(local, remote);

		assertEquals(3, merged.size());
		assertSame(item1, merged.get(0));
		assertSame(item2, merged.get(1));
		assertSame(item3Remote, merged.get(2));
	}

	@Test
	public void handlesNullOrEmptyLists() {
		List<Item> merged = RadioSearchResultMerger.merge(null, null);
		assertEquals(0, merged.size());

		BrowsableItem parent = createDummyParent();
		RadioBrowserStation s1 = new RadioBrowserStation("uuid-1", "Station One", "http://s1.mp3",
				"", "", "", "", "Vietnam", "VN", "Vietnamese", "MP3", 128, 10, 100);
		RadioStationItem item1 = new RadioStationItem(parent, s1);

		merged = RadioSearchResultMerger.merge(List.of(item1), null);
		assertEquals(1, merged.size());
		assertSame(item1, merged.get(0));

		merged = RadioSearchResultMerger.merge(null, List.of(item1));
		assertEquals(1, merged.size());
		assertSame(item1, merged.get(0));
	}
}
