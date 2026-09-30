package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.lang.reflect.Proxy;

import me.aap.utils.ui.menu.OverlayMenuItem;

public class YoutubeQualityMenuTest {

	private OverlayMenuItem createDummyItem(Object data) {
		return (OverlayMenuItem) Proxy.newProxyInstance(
				OverlayMenuItem.class.getClassLoader(),
				new Class<?>[]{OverlayMenuItem.class},
				(proxy, method, args) -> {
					if ("getData".equals(method.getName())) {
						return data;
					}
					return null;
				});
	}

	@Test
	public void selectRejectsNonQualityItems() {
		OverlayMenuItem nullItem = createDummyItem(null);
		assertFalse(YoutubeQualityMenu.select(null, nullItem));

		OverlayMenuItem stringItem = createDummyItem("not-an-integer");
		assertFalse(YoutubeQualityMenu.select(null, stringItem));

		OverlayMenuItem unmaskedItem = createDummyItem(42);
		assertFalse(YoutubeQualityMenu.select(null, unmaskedItem));
	}
}
