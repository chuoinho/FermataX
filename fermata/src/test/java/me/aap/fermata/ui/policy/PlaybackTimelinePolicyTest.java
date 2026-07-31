package me.aap.fermata.ui.policy;

import static me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.HIDDEN;
import static me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.LIVE;
import static me.aap.fermata.ui.policy.PlaybackTimelinePolicy.Mode.SEEKABLE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackTimelinePolicyTest {
	@Test
	public void liveStreamNeverExposesZeroDurationSeekBar() {
		assertEquals(LIVE, PlaybackTimelinePolicy.resolve(true, false, true, 0L));
		assertEquals(LIVE, PlaybackTimelinePolicy.resolve(true, true, true, 0L));
	}

	@Test
	public void catchUpRequiresItemEngineAndDurationAgreement() {
		assertEquals(SEEKABLE, PlaybackTimelinePolicy.resolve(false, true, true, 90_000L));
		assertEquals(HIDDEN, PlaybackTimelinePolicy.resolve(false, true, true, 0L));
		assertEquals(HIDDEN, PlaybackTimelinePolicy.resolve(false, true, false, 90_000L));
	}

	@Test
	public void unknownLocalDurationStaysHiddenUntilResolved() {
		assertEquals(HIDDEN, PlaybackTimelinePolicy.resolve(false, true, true, 0L));
	}
}
