package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.external.ExternalPlaybackRequest;
import me.aap.fermata.addon.external.ExternalPlaybackTargetKind;

public class YoutubeItemTest {
	@Test
	public void canonicalizesSupportedYoutubeUrls() {
		YoutubeItem watch = YoutubeItem.fromPageUrl(
				"https://www.youtube.com/watch?feature=share&v=video_1#player",
				"(2) Video title - YouTube", 100L);
		YoutubeItem shorts = YoutubeItem.fromPageUrl(
				"https://youtube.com/shorts/short-2?feature=share", "Short", 200L);
		YoutubeItem shared = YoutubeItem.fromPageUrl(
				"https://youtu.be/video_1?t=30", "Shared", 300L);

		assertEquals("video_1", watch.videoId());
		assertEquals("https://m.youtube.com/watch?v=video_1", watch.pageUrl());
		assertEquals("Video title", watch.title());
		assertEquals("youtube:video:video_1", watch.stableId());
		assertEquals("https://m.youtube.com/shorts/short-2", shorts.pageUrl());
		assertEquals(watch.stableId(), shared.stableId());
	}

	@Test
	public void updatesReturnNewImmutableValues() {
		YoutubeItem original = YoutubeItem.fromPageUrl(
				"https://m.youtube.com/watch?v=immutable", "Initial", 10L);
		YoutubeItem titled = original.withTitle("Updated | YouTube");
		YoutubeItem replayed = titled.playedAt(20L);

		assertNotSame(original, titled);
		assertNotSame(titled, replayed);
		assertEquals("Initial", original.title());
		assertEquals(10L, original.lastPlayedAtMillis());
		assertEquals("Updated", titled.title());
		assertEquals(20L, replayed.lastPlayedAtMillis());
	}

	@Test
	public void metadataMergeNeverMovesLastPlayedTimeBackwards() {
		YoutubeItem persisted = new YoutubeItem("video", "https://m.youtube.com/watch?v=video",
				"Persisted", "https://img.example/persisted.jpg", 10_000L, 200L);
		YoutubeItem delayed = new YoutubeItem("video", "https://m.youtube.com/watch?v=video",
				"Resolved title", "", 12_000L, 100L);

		YoutubeItem merged = YoutubeAddon.mergeYoutubeItem(persisted, delayed);

		assertEquals("Resolved title", merged.title());
		assertEquals(12_000L, merged.durationMillis());
		assertEquals(200L, merged.lastPlayedAtMillis());
	}

	@Test
	public void externalHandoffPreservesExactPlaybackMetadata() {
		ExternalPlaybackRequest request = new ExternalPlaybackRequest("movie:exact", "Exact title",
				"https://images.example/exact.jpg", 123_456L,
				ExternalPlaybackTargetKind.YOUTUBE_ID, "video_exact");
		YoutubeItem descriptor = YoutubeAddon.externalDescriptor(request);
		YoutubeAddon.YoutubeHistoryItem item = new YoutubeAddon.YoutubeHistoryItem(
				null, null, descriptor);

		assertEquals("video_exact", descriptor.videoId());
		assertEquals("Exact title", item.getName());
		assertEquals("Exact title", descriptor.title());
		assertEquals("https://images.example/exact.jpg", descriptor.thumbnailUrl());
		assertEquals(123_456L, descriptor.durationMillis());
	}

	@Test
	public void asyncEvictionCleanupCannotDeleteAnItemThatWasReplayed() {
		YoutubeItem replayed = YoutubeItem.fromPageUrl(
				"https://m.youtube.com/watch?v=replayed", "Replayed", 300L);
		YoutubeItem stillEvicted = YoutubeItem.fromPageUrl(
				"https://m.youtube.com/watch?v=evicted", "Evicted", 100L);

		assertEquals(List.of(stillEvicted.stableId()), YoutubeAddon.getStillEvictedIds(
				List.of(replayed), List.of(replayed.playedAt(50L), stillEvicted)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsLookalikeYoutubeUrl() {
		YoutubeItem.fromPageUrl("https://example.com/watch?v=not-youtube", "Title", 0L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsTelevisionYoutubeSurface() {
		YoutubeItem.fromPageUrl("https://tv.youtube.com/watch?v=not-video", "Title", 0L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMismatchedPersistedIdentity() {
		new YoutubeItem("expected", "https://m.youtube.com/watch?v=different", "Title", 0L);
	}
}
