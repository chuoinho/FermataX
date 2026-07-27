package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class YoutubeItemCodecTest {
	@Test
	public void roundTripsEscapedFieldsInOrder() {
		List<YoutubeItem> items = List.of(
				YoutubeItem.fromPageUrl("https://m.youtube.com/watch?v=first", "One | two\nthree", 20L),
				YoutubeItem.fromPageUrl("https://m.youtube.com/shorts/second", "Second + title", 10L));

		assertEquals(items, YoutubeItemCodec.decode(YoutubeItemCodec.encode(items)));
	}

	@Test
	public void pinnedDescriptorSurvivesProcessRestartWithAllPlaybackMetadata() {
		YoutubeItem pinned = new YoutubeItem("favorite", "https://m.youtube.com/watch?v=favorite",
				"Favorite title", "https://img.example/favorite.jpg", 123_456L, 987L);

		List<YoutubeItem> restored = YoutubeItemCodec.decode(
				YoutubeItemCodec.encode(List.of(pinned)));

		assertEquals(List.of(pinned), restored);
		assertEquals(123_456L, restored.get(0).durationMillis());
		assertEquals("https://img.example/favorite.jpg", restored.get(0).thumbnailUrl());
	}

	@Test
	public void damagedEntryDoesNotDiscardValidEntries() {
		YoutubeItem valid = YoutubeItem.fromPageUrl(
				"https://m.youtube.com/watch?v=valid", "Valid", 50L);
		String data = YoutubeItemCodec.encode(List.of(valid)) +
				"\nbroken|https%3A%2F%2Fexample.com|Broken|not-a-time";

		assertEquals(List.of(valid), YoutubeItemCodec.decode(data));
	}

	@Test
	public void unknownVersionIsIgnored() {
		assertEquals(List.of(), YoutubeItemCodec.decode("yt-items-v99\nrecord"));
		assertEquals(List.of(), YoutubeItemCodec.decode("yt-items-v2\n%ZZ|bad|record|fields|0|0"));
		assertEquals(List.of(), YoutubeItemCodec.decode(null));
		assertTrue(YoutubeItemCodec.decodeResult("yt-items-v99\nrecord").isUnsupported());
		assertFalse(YoutubeItemCodec.decodeResult("yt-items-v2\n%ZZ|bad|record|fields|0|0")
				.isUnsupported());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void decodedHistoryIsImmutable() {
		YoutubeItemCodec.decode(YoutubeItemCodec.encode(List.of(
				YoutubeItem.fromPageUrl("https://youtu.be/item", "Item", 1L)))).clear();
	}
}
