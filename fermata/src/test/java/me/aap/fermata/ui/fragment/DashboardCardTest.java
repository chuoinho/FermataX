package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import java.lang.reflect.Proxy;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

public class DashboardCardTest {
	@Test
	public void recentSummaryShowsAtMostThreeNonEmptyNames() {
		assertEquals("One - Two - Three", DashboardCard.itemSummaryNames(
				Arrays.asList("One", "", null, "Two", "Three", "Four"), "Recent").toString());
	}

	@Test
	public void recentSummaryUsesFallbackWhenNoNamesExist() {
		assertEquals("Recent", DashboardCard.itemSummaryNames(
				Collections.singletonList(""), "Recent").toString());
	}

	@Test
	public void playbackCardUsesResolvedSnapshotTitle() {
		PlayableItem item = (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getName" -> "https://m.youtube.com/watch?v=test";
					case "getIcon" -> 0;
					case "getParent" -> null;
					default -> null;
				});

		assertEquals("Video title", DashboardCard.playable(item, "Video title", true, null)
				.title.toString());
	}
}
