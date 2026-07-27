package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class YoutubeRetentionPolicyTest {
	@Test
	public void keepsNewestUniqueItemsWithinAgeAndCountLimits() {
		YoutubeRetentionPolicy policy = new YoutubeRetentionPolicy(2, 100L);
		YoutubeItem oldDuplicate = item("same", "Old title", 930L);
		YoutubeItem latestDuplicate = item("same", "Latest title", 980L);
		YoutubeItem second = item("second", "Second", 970L);
		YoutubeItem overLimit = item("third", "Third", 960L);
		YoutubeItem expired = item("expired", "Expired", 899L);

		assertEquals(List.of(latestDuplicate, second), policy.retain(List.of(
				oldDuplicate, expired, overLimit, second, latestDuplicate), 1000L));
	}

	@Test
	public void ageBoundaryIsInclusiveAndFutureClockSkewSurvives() {
		YoutubeRetentionPolicy policy = new YoutubeRetentionPolicy(3, 100L);
		YoutubeItem boundary = item("boundary", "Boundary", 900L);
		YoutubeItem future = item("future", "Future", 1010L);

		assertEquals(List.of(future, boundary), policy.retain(List.of(boundary, future), 1000L));
	}

	@Test
	public void clampsAnUnreasonableFutureTimestampBeforeApplyingQuota() {
		YoutubeRetentionPolicy policy = new YoutubeRetentionPolicy(1, Long.MAX_VALUE);
		YoutubeItem farFuture = item("future", "Future", 10_000_000L);

		assertEquals(1_000L, policy.retain(List.of(farFuture), 1_000L).get(0).lastPlayedAtMillis());
	}

	@Test
	public void zeroLimitsProduceEmptyHistory() {
		YoutubeItem item = item("item", "Item", 10L);

		assertEquals(List.of(), new YoutubeRetentionPolicy(0, 100L).retain(List.of(item), 10L));
		assertEquals(List.of(), new YoutubeRetentionPolicy(10, 0L).retain(List.of(item), 11L));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void retainedHistoryIsImmutable() {
		new YoutubeRetentionPolicy(1, 100L).retain(
				List.of(item("item", "Item", 10L)), 10L).clear();
	}

	private static YoutubeItem item(String id, String title, long playedAt) {
		return YoutubeItem.fromPageUrl("https://m.youtube.com/watch?v=" + id, title, playedAt);
	}
}
