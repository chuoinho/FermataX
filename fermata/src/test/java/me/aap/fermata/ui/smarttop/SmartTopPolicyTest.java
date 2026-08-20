package me.aap.fermata.ui.smarttop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;

public class SmartTopPolicyTest {
	@Test
	public void selectionOrderStopsAtRecentAndRecommendationIsCompatibilityOnly() {
		assertEquals(SmartTopMode.CURRENT,
				SmartTopSelectionPolicy.select(true, true, true, true));
		assertEquals(SmartTopMode.RESUME,
				SmartTopSelectionPolicy.select(false, true, true, true));
		assertEquals(SmartTopMode.RECENT,
				SmartTopSelectionPolicy.select(false, false, true, true));
		assertEquals(SmartTopMode.EMPTY,
				SmartTopSelectionPolicy.select(false, false, false, true));
		assertEquals(SmartTopMode.EMPTY,
				SmartTopSelectionPolicy.select(false, false, false, false));
	}

	@Test
	public void smartTopSemanticsExcludeNextAndBackControls() {
		SmartTopCapabilities current = SmartTopCapabilities.current(true, true);
		assertEquals(List.of(SmartTopAction.PREVIOUS, SmartTopAction.PLAY_PAUSE,
				SmartTopAction.FAVORITE),
				SmartTopActionPolicy.resolve(SmartTopMode.CURRENT, current));

		SmartTopCapabilities suggestion = SmartTopCapabilities.suggestion(true, true);
		assertEquals(List.of(SmartTopAction.PLAY, SmartTopAction.FAVORITE),
				SmartTopActionPolicy.resolve(SmartTopMode.RESUME, suggestion));
		assertEquals(List.of(SmartTopAction.RETRY),
				SmartTopActionPolicy.resolve(SmartTopMode.RECOVERY, suggestion));
		assertEquals(List.of(SmartTopAction.OPEN_ADDONS),
				SmartTopActionPolicy.resolve(SmartTopMode.EMPTY, SmartTopCapabilities.NONE));
		assertFalse(SmartTopActionPolicy.resolve(SmartTopMode.CURRENT, current)
				.contains(SmartTopAction.NEXT));
		assertFalse(SmartTopActionPolicy.resolve(SmartTopMode.CURRENT, current)
				.contains(SmartTopAction.OPEN_CONTEXT));
	}

	@Test
	public void measuredSpaceClassMatchesRepresentativeViewports() {
		assertEquals(SmartTopLayoutMode.COMPACT, mode(313, 720, 1F));
		assertEquals(SmartTopLayoutMode.COMPACT, mode(384, 720, 1F));
		assertEquals(SmartTopLayoutMode.STANDARD, mode(704, 480, 1F));
		assertEquals(SmartTopLayoutMode.STANDARD, mode(948, 600, 1F));
		assertEquals(SmartTopLayoutMode.EXPANDED, mode(1176, 720, 1F));
	}

	@Test
	public void phoneRotationChangesSpaceClassWithoutChangingInteractionProfile() {
		SmartTopEnvironment portrait = env(384, 780, 1F, SmartTopInteractionProfile.TOUCH);
		SmartTopEnvironment landscape = env(704, 360, 1F, SmartTopInteractionProfile.TOUCH);
		assertEquals(SmartTopLayoutMode.COMPACT, SmartTopAdaptivePolicy.resolveMode(portrait));
		assertEquals(SmartTopLayoutMode.STANDARD, SmartTopAdaptivePolicy.resolveMode(landscape));
		assertEquals(SmartTopInteractionProfile.TOUCH, portrait.interaction());
		assertEquals(SmartTopInteractionProfile.TOUCH, landscape.interaction());
	}

	@Test
	public void timelineProgressIsBoundedAndHandlesLargeDurations() {
		assertEquals(0, SmartTopBinder.progress(0L, 0L));
		assertEquals(0, SmartTopBinder.progress(-1L, 1_000L));
		assertEquals(500, SmartTopBinder.progress(5_000L, 10_000L));
		assertEquals(1000, SmartTopBinder.progress(20_000L, 10_000L));
		assertEquals(500, SmartTopBinder.progress(Long.MAX_VALUE / 4,
				Long.MAX_VALUE / 2));
	}

	@Test
	public void timelinePresentationIgnoresRawTicksThatCannotChangeRenderedValues() {
		SmartTopTimeline first = new SmartTopTimeline(PlaybackTimelinePolicy.Mode.SEEKABLE,
				1_100L, 600_000L, true);
		SmartTopTimeline samePresentation = new SmartTopTimeline(
				PlaybackTimelinePolicy.Mode.SEEKABLE, 1_200L, 600_000L, true);
		SmartTopTimeline nextSecond = new SmartTopTimeline(
				PlaybackTimelinePolicy.Mode.SEEKABLE, 2_100L, 600_000L, true);
		assertEquals(SmartTopTimelinePresentation.of(first),
				SmartTopTimelinePresentation.of(samePresentation));
		assertFalse(SmartTopTimelinePresentation.of(first).equals(
					SmartTopTimelinePresentation.of(nextSecond)));
	}

	@Test
	public void finiteTimelineDisplaysReadableRemainingTime() {
		assertEquals(SmartTopBinder.RemainingTime.ALMOST_DONE,
				SmartTopBinder.remainingTime(0L));
		assertEquals(SmartTopBinder.RemainingTime.ALMOST_DONE,
				SmartTopBinder.remainingTime(59_000L));
		assertEquals(new SmartTopBinder.RemainingTime(0, 1),
				SmartTopBinder.remainingTime(60_000L));
		assertEquals(new SmartTopBinder.RemainingTime(1, 0),
				SmartTopBinder.remainingTime(3_600_000L));
	}

	@Test
	public void accessibilityPressureDemotesCompositionAndRaisesHeight() {
		SmartTopLayoutSpec normal = SmartTopAdaptivePolicy.resolve(
				env(600, 800, 1F, SmartTopInteractionProfile.TOUCH),
				SmartTopActionPolicy.resolve(SmartTopMode.CURRENT,
						SmartTopCapabilities.current(true, true)), SmartTopContentMetrics.empty());
		SmartTopLayoutSpec accessible = SmartTopAdaptivePolicy.resolve(
				env(600, 800, 1.5F, SmartTopInteractionProfile.TOUCH),
				SmartTopActionPolicy.resolve(SmartTopMode.CURRENT,
						SmartTopCapabilities.current(true, true)), SmartTopContentMetrics.empty());
		assertEquals(SmartTopLayoutMode.STANDARD, normal.mode());
		assertEquals(SmartTopLayoutMode.COMPACT, accessible.mode());
		assertTrue(accessible.cardHeightDp() > normal.cardHeightDp());
	}

	@Test
	public void resumeRequiresMeaningfulFiniteProgress() {
		assertTrue(SmartTopResumePolicy.isMeaningful(false, true,
				120_000L, 600_000L));
		assertFalse(SmartTopResumePolicy.isMeaningful(true, true,
				120_000L, 600_000L));
		assertFalse(SmartTopResumePolicy.isMeaningful(false, true,
				10_000L, 600_000L));
		assertFalse(SmartTopResumePolicy.isMeaningful(false, false,
				120_000L, 600_000L));
	}

	private static SmartTopLayoutMode mode(float width, float height, float scale) {
		return SmartTopAdaptivePolicy.resolveMode(env(width, height, scale,
				SmartTopInteractionProfile.TOUCH));
	}

	private static SmartTopEnvironment env(float width, float height, float scale,
			SmartTopInteractionProfile profile) {
		return new SmartTopEnvironment(width, height, scale, profile);
	}
}
