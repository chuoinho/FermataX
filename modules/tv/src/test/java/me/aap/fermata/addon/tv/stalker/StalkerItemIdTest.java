package me.aap.fermata.addon.tv.stalker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StalkerItemIdTest {
	@Test
	public void roundTripsReservedCharacters() {
		String id = StalkerItemId.channel("tvst", 7, "genre:news", "Tin tức / News",
				"channel:42");
		StalkerItemId.Channel parsed = StalkerItemId.parseChannel(id, "tvst");

		assertEquals(7, parsed.sourceId());
		assertEquals("genre:news", parsed.categoryId());
		assertEquals("Tin tức / News", parsed.categoryName());
		assertEquals("channel:42", parsed.channelId());
	}
}
