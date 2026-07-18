package me.aap.fermata.addon.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RadioPlaybackSemanticsTest {
	@Test
	public void managedRadioKeepsDirectUrlPlayback() {
		RadioSource source = RadioSource.restore("stable", "Station",
				"https://example.test/live.mp3");
		RadioSourceItem item = new RadioSourceItem(parent(), source);

		assertFalse(item.isExternal());
		assertFalse(item.isCacheable());
		assertTrue(item.isStream());
		assertEquals("https://example.test/live.mp3", item.getLocation().toString());
	}

	@Test
	public void browserStationsUseTheSameManagedSemantics() {
		assertTrue(RadioPlayableItem.class.isAssignableFrom(RadioStationItem.class));
		assertTrue(RadioPlayableItem.class.isAssignableFrom(RadioSourceItem.class));
	}

	private static BrowsableItem parent() {
		return (BrowsableItem) Proxy.newProxyInstance(BrowsableItem.class.getClassLoader(),
				new Class<?>[]{BrowsableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> "radio-parent";
					case "getRoot" -> proxy;
					default -> null;
				});
	}
}
