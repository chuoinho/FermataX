package me.aap.fermata.ui.smarttop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;

public class SmartTopPolicyTest {
	@Test
	public void selectionOrderIsStrict() {
		assertEquals(SmartTopMode.CURRENT,
				SmartTopSelectionPolicy.select(true, true, true, true));
		assertEquals(SmartTopMode.RESUME,
				SmartTopSelectionPolicy.select(false, true, true, true));
		assertEquals(SmartTopMode.RECENT,
				SmartTopSelectionPolicy.select(false, false, true, true));
		assertEquals(SmartTopMode.RECOMMENDED,
				SmartTopSelectionPolicy.select(false, false, false, true));
		assertEquals(SmartTopMode.EMPTY,
				SmartTopSelectionPolicy.select(false, false, false, false));
	}

	@Test
	public void currentActionsAreCapabilityDrivenAndOrdered() {
		assertEquals(List.of(SmartTopAction.PREVIOUS, SmartTopAction.PLAY_PAUSE,
				SmartTopAction.NEXT, SmartTopAction.FAVORITE, SmartTopAction.OPEN_CONTEXT),
				SmartTopActionPolicy.resolve(SmartTopMode.CURRENT, SmartTopLayoutMode.STANDARD,
						SmartTopCapabilities.current(true, true)));
		assertEquals(List.of(SmartTopAction.PLAY_PAUSE, SmartTopAction.NEXT,
				SmartTopAction.HISTORY),
				SmartTopActionPolicy.resolve(SmartTopMode.CURRENT, SmartTopLayoutMode.COMPACT,
						SmartTopCapabilities.current(true, true)));
	}

	@Test
	public void suggestionAndTerminalActionsMatchTheStateMatrix() {
		SmartTopCapabilities suggestion = SmartTopCapabilities.suggestion(true, true);
		assertEquals(List.of(SmartTopAction.PLAY, SmartTopAction.OPEN_CONTEXT,
				SmartTopAction.FAVORITE),
				SmartTopActionPolicy.resolve(SmartTopMode.RESUME,
						SmartTopLayoutMode.STANDARD, suggestion));
		assertEquals(List.of(SmartTopAction.PLAY, SmartTopAction.OPEN_CONTEXT,
				SmartTopAction.HISTORY),
				SmartTopActionPolicy.resolve(SmartTopMode.RECOMMENDED,
						SmartTopLayoutMode.COMPACT, suggestion));
		assertEquals(List.of(SmartTopAction.OPEN_ADDONS),
				SmartTopActionPolicy.resolve(SmartTopMode.EMPTY,
						SmartTopLayoutMode.COMPACT, SmartTopCapabilities.NONE));
		assertEquals(List.of(SmartTopAction.RETRY, SmartTopAction.OPEN_CONTEXT),
				SmartTopActionPolicy.resolve(SmartTopMode.RECOVERY,
						SmartTopLayoutMode.STANDARD, suggestion));
	}

	@Test
	public void measuredLayoutMatchesApprovedViewports() {
		assertEquals(SmartTopLayoutMode.COMPACT, SmartTopLayoutPolicy.resolve(313F, 1F));
		assertEquals(SmartTopLayoutMode.COMPACT, SmartTopLayoutPolicy.resolve(384F, 1F));
		assertEquals(SmartTopLayoutMode.STANDARD, SmartTopLayoutPolicy.resolve(704F, 1F));
		assertEquals(SmartTopLayoutMode.STANDARD, SmartTopLayoutPolicy.resolve(948F, 1F));
		assertEquals(SmartTopLayoutMode.EXPANDED, SmartTopLayoutPolicy.resolve(1176F, 1F));
	}

	@Test
	public void rendererGeometryMatchesMobileAndAutomotiveContracts() {
		assertEquals(56, SmartTopLayoutController.artworkSizeDp(
				SmartTopLayoutMode.COMPACT, false));
		assertEquals(66, SmartTopLayoutController.artworkSizeDp(
				SmartTopLayoutMode.COMPACT, true));
		assertEquals(80, SmartTopLayoutController.artworkSizeDp(
				SmartTopLayoutMode.STANDARD, true));
		assertEquals(88, SmartTopLayoutController.artworkSizeDp(
				SmartTopLayoutMode.EXPANDED, true));
		assertEquals(108, SmartTopLayoutController.labeledActionMaxWidthDp(false));
		assertEquals(112, SmartTopLayoutController.labeledActionMaxWidthDp(true));
		assertEquals(0, SmartTopLayoutController.contextPanelWidthDp(
				SmartTopLayoutMode.COMPACT));
		assertEquals(160, SmartTopLayoutController.contextPanelWidthDp(
				SmartTopLayoutMode.STANDARD));
		assertEquals(196, SmartTopLayoutController.contextPanelWidthDp(
				SmartTopLayoutMode.EXPANDED));
		assertEquals(10, SmartTopLayoutController.cardPaddingDp(
				SmartTopLayoutMode.COMPACT, true));
		assertEquals(12, SmartTopLayoutController.cardPaddingDp(
				SmartTopLayoutMode.COMPACT, false));
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
	public void timelinePresentationStillPublishesPlayStateForHiddenAndLiveMedia() {
		for (PlaybackTimelinePolicy.Mode mode : new PlaybackTimelinePolicy.Mode[]{
				PlaybackTimelinePolicy.Mode.HIDDEN, PlaybackTimelinePolicy.Mode.LIVE}) {
			SmartTopTimeline playing = new SmartTopTimeline(mode, 10_000L, 20_000L, true);
			SmartTopTimeline paused = new SmartTopTimeline(mode, 30_000L, 40_000L, false);
			assertFalse(SmartTopTimelinePresentation.of(playing).equals(
					SmartTopTimelinePresentation.of(paused)));
			assertEquals(SmartTopTimelinePresentation.of(playing),
					SmartTopTimelinePresentation.of(new SmartTopTimeline(
							mode, 99_000L, 100_000L, true)));
		}
	}

	@Test
	public void finiteTimelineDisplaysRemainingTime() {
		assertEquals("-01:30", SmartTopBinder.formatRemainingTime(30_000L, 120_000L));
		assertEquals("-00:00", SmartTopBinder.formatRemainingTime(130_000L, 120_000L));
	}

	@Test
	public void fontPressureDemotesLayoutBeforeItCanOverlap() {
		assertEquals(SmartTopLayoutMode.COMPACT, SmartTopLayoutPolicy.resolve(600F, 1.5F));
		assertEquals(SmartTopLayoutMode.STANDARD, SmartTopLayoutPolicy.resolve(1176F, 2F));
		assertEquals(144, SmartTopLayoutPolicy.cardHeightDp(SmartTopLayoutMode.STANDARD, 1F));
		assertEquals(164, SmartTopLayoutPolicy.cardHeightDp(SmartTopLayoutMode.STANDARD, 1.5F));
		assertEquals(184, SmartTopLayoutPolicy.cardHeightDp(SmartTopLayoutMode.STANDARD, 2F));
		assertEquals(216, SmartTopLayoutPolicy.cardHeightDp(SmartTopLayoutMode.COMPACT, 3F));
	}

	@Test
	public void quickRecentYieldsToCompactAndTextPressure() {
		assertTrue(SmartTopLayoutPolicy.showQuickRecent(SmartTopLayoutMode.STANDARD,
				704F, 1F, 4, 28));
		assertFalse(SmartTopLayoutPolicy.showQuickRecent(SmartTopLayoutMode.COMPACT,
				704F, 1F, 3, 28));
		assertFalse(SmartTopLayoutPolicy.showQuickRecent(SmartTopLayoutMode.STANDARD,
				704F, 1.5F, 4, 28));
		assertFalse(SmartTopLayoutPolicy.showQuickRecent(SmartTopLayoutMode.STANDARD,
				704F, 1F, 4, 64));
	}

	@Test
	public void fiveViewportsAndSixStatesStayWithinTheirActionBudget() {
		float[] widths = {313F, 384F, 704F, 948F, 1176F};
		for (float width : widths) {
			SmartTopLayoutMode layout = SmartTopLayoutPolicy.resolve(width, 1F);
			for (SmartTopMode mode : SmartTopMode.values()) {
				SmartTopCapabilities capabilities = switch (mode) {
					case CURRENT -> SmartTopCapabilities.current(true, true);
					case EMPTY -> SmartTopCapabilities.NONE;
					default -> SmartTopCapabilities.suggestion(true, true);
				};
				List<SmartTopAction> actions = SmartTopActionPolicy.resolve(
						mode, layout, capabilities);
				assertTrue(width + "dp " + mode, actions.size() <=
						((layout == SmartTopLayoutMode.COMPACT) ? 3 : 5));
				long labeled = actions.stream().filter(action ->
						(action == SmartTopAction.OPEN_ADDONS) ||
								(action == SmartTopAction.RETRY)).count();
				assertTrue(width + "dp " + mode, labeled <= 1);
			}
		}
	}

	@Test
	public void accessibilityHeightsIncreaseMonotonicallyAcrossRequiredScales() {
		for (SmartTopLayoutMode mode : SmartTopLayoutMode.values()) {
			int previous = 0;
			for (float scale : new float[]{1F, 1.3F, 1.5F, 2F}) {
				int height = SmartTopLayoutPolicy.cardHeightDp(mode, scale);
				assertTrue(mode + " at " + scale, height >= previous);
				previous = height;
			}
			assertEquals(mode.cardHeightDp() + 40, previous);
		}
	}

	@Test
	public void resumeRequiresMeaningfulFiniteProgress() {
		assertTrue(SmartTopResumePolicy.isMeaningful(false, true,
				120_000L, 600_000L));
		assertFalse(SmartTopResumePolicy.isMeaningful(true, true,
				120_000L, 600_000L));
		assertFalse(SmartTopResumePolicy.isMeaningful(false, true,
				10_000L, 600_000L));
		assertFalse(SmartTopResumePolicy.isMeaningful(false, true,
				570_000L, 600_000L));
		assertFalse(SmartTopResumePolicy.isMeaningful(false, false,
				120_000L, 600_000L));
	}
}
