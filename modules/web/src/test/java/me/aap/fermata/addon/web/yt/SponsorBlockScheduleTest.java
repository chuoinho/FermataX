package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class SponsorBlockScheduleTest {
	private static final SponsorBlockClient.Segment FIRST = segment(8d, 12d, "first");
	private static final SponsorBlockClient.Segment SECOND = segment(20d, 24d, "second");

	@Test
	public void findsTheRelevantSegmentAfterForwardAndBackwardSeeks() {
		List<SponsorBlockClient.Segment> segments = List.of(FIRST, SECOND);

		assertEquals(0, SponsorBlockSchedule.findSegmentIndex(segments, 0L));
		assertEquals(0, SponsorBlockSchedule.findSegmentIndex(segments, 10_000L));
		assertEquals(1, SponsorBlockSchedule.findSegmentIndex(segments, 12_000L));
		assertEquals(2, SponsorBlockSchedule.findSegmentIndex(segments, 24_000L));
		assertEquals(0, SponsorBlockSchedule.findSegmentIndex(segments, 9_000L));
	}

	@Test
	public void convertsMediaDistanceToBoundedWallClockDelay() {
		assertEquals(1_000L, SponsorBlockSchedule.delayUntil(0L, 8_000L, 2f));
		assertEquals(375L, SponsorBlockSchedule.delayUntil(7_000L, 7_750L, 2f));
		assertEquals(750L, SponsorBlockSchedule.delayUntil(7_000L, 7_750L, 0f));
		assertEquals(250L, SponsorBlockSchedule.delayUntil(8_000L, 7_750L, 1f));
	}

	@Test
	public void retryBackoffIsBoundedAndFinalRescanIsLessAggressive() {
		assertEquals(1_500L, SponsorBlockSchedule.retryDelayMillis(0));
		assertEquals(4_000L, SponsorBlockSchedule.retryDelayMillis(1));
		assertEquals(10_000L, SponsorBlockSchedule.retryDelayMillis(2));
		assertEquals(-1L, SponsorBlockSchedule.retryDelayMillis(3));
		assertEquals(1_000L, SponsorBlockSchedule.POST_SEGMENT_RESCAN_MS);
	}

	private static SponsorBlockClient.Segment segment(double start, double end, String id) {
		return new SponsorBlockClient.Segment(start, end,
				SponsorBlockClient.Category.SPONSOR, id);
	}
}
