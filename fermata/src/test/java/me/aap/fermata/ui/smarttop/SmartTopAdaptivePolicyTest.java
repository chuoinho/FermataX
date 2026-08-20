package me.aap.fermata.ui.smarttop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class SmartTopAdaptivePolicyTest {
	private static final List<SmartTopAction> CURRENT = List.of(
			SmartTopAction.PREVIOUS, SmartTopAction.PLAY_PAUSE, SmartTopAction.FAVORITE);

	@Test
	public void spaceClassDoesNotDependOnHostIdentity() {
		SmartTopEnvironment touch = env(700, 360, 1F, SmartTopInteractionProfile.TOUCH);
		SmartTopEnvironment auto = env(700, 480, 1F, SmartTopInteractionProfile.AUTOMOTIVE);
		assertEquals(SmartTopLayoutMode.STANDARD, SmartTopAdaptivePolicy.resolveMode(touch));
		assertEquals(SmartTopLayoutMode.STANDARD, SmartTopAdaptivePolicy.resolveMode(auto));
	}

	@Test
	public void compactAutomotiveKeepsQuickRecentAndPrimaryPlaybackAtNarrowWidth() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(420, 480, 1F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		assertEquals(SmartTopLayoutMode.COMPACT, spec.mode());
		assertTrue(spec.visibleActions().contains(SmartTopAction.PLAY_PAUSE));
		assertEquals(3, spec.recentRows());
		assertEquals(148, spec.recentPanelWidthDp());
		assertTrue(spec.showQuickRecent());
		assertEquals(152, spec.cardHeightDp());
	}

	@Test
	public void standardSpaceKeepsThreeRecentRowsWithoutOrientationRules() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(700, 360, 1F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		assertEquals(SmartTopLayoutMode.STANDARD, spec.mode());
		assertEquals(3, spec.recentRows());
		assertTrue(spec.showQuickRecent());
	}

	@Test
	public void automotiveCellsShrinkBeforeTheHorizontalRailCanCrowdMetadata() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(700, 480, 1F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		assertEquals(SmartTopLayoutMode.STANDARD, spec.mode());
		assertEquals(60, spec.actionCellDp());
		assertEquals(35, spec.primaryGlyphDp());
		assertEquals(28, spec.secondaryGlyphDp());
		assertEquals(2, spec.actionGapDp());
		assertEquals(3, spec.recentRows());
		assertTrue(spec.visibleActions().contains(SmartTopAction.PREVIOUS));
		assertTrue(spec.visibleActions().contains(SmartTopAction.PLAY_PAUSE));
	}

	@Test
	public void automotiveRailKeepsCompactGapTiersToProtectTitleWidth() {
		SmartTopLayoutSpec medium = SmartTopAdaptivePolicy.resolve(
				env(900, 480, 1F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(180, 0, 0));
		SmartTopLayoutSpec wide = SmartTopAdaptivePolicy.resolve(
				env(1176, 720, 1F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(180, 0, 0));
		assertEquals(4, medium.actionGapDp());
		assertEquals(SmartTopAdaptivePolicy.AUTOMOTIVE_MAX_GAP_DP, wide.actionGapDp());
		assertEquals(6, SmartTopAdaptivePolicy.AUTOMOTIVE_MAX_GAP_DP);
	}

	@Test
	public void fontPressureCanShrinkAutomotiveCellsFurtherWithoutChangingCardOnPlaybackState() {
		SmartTopEnvironment environment =
				env(700, 480, 1.5F, SmartTopInteractionProfile.AUTOMOTIVE);
		SmartTopLayoutSpec playing = SmartTopAdaptivePolicy.resolve(environment, CURRENT,
				new SmartTopContentMetrics(180, 0, 3));
		SmartTopLayoutSpec paused = SmartTopAdaptivePolicy.resolve(environment, CURRENT,
				new SmartTopContentMetrics(180, 0, 3));
		assertEquals(56, playing.actionCellDp());
		assertEquals(playing.cardHeightDp(), paused.cardHeightDp());
		assertEquals(playing.visibleActions(), paused.visibleActions());
		assertEquals(3, playing.recentRows());
	}

	@Test
	public void sameFontScaleUsesOneHeightAcrossAllWidthClasses() {
		for (float scale : new float[]{1F, 1.3F, 1.5F, 2F}) {
			int compact = SmartTopAdaptivePolicy.cardHeightDp(SmartTopLayoutMode.COMPACT, scale);
			int standard = SmartTopAdaptivePolicy.cardHeightDp(SmartTopLayoutMode.STANDARD, scale);
			int expanded = SmartTopAdaptivePolicy.cardHeightDp(SmartTopLayoutMode.EXPANDED, scale);
			assertEquals(compact, standard);
			assertEquals(standard, expanded);
		}
	}

	@Test
	public void wideAutomotiveViewportFitsThreeRecentRows() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(1176, 720, 1F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(180, 0, 3));
		assertEquals(SmartTopLayoutMode.EXPANDED, spec.mode());
		assertEquals(76, spec.actionCellDp());
		assertEquals(6, spec.actionGapDp());
		assertEquals(3, spec.recentRows());
		assertEquals(196, spec.recentPanelWidthDp());
	}

	@Test
	public void longTitleIsBudgetedAsTwoLinesWithoutDroppingQuickRecent() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(704, 480, 1.3F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(520, 0, 3));
		assertTrue(spec.visibleActions().contains(SmartTopAction.PLAY_PAUSE));
		assertEquals(3, spec.recentRows());
		assertTrue(spec.actionCellDp() >= SmartTopAdaptivePolicy.AUTOMOTIVE_MIN_ACTION_CELL_DP);
	}

	@Test
	public void terminalActionDropsItsIconBeforeEllipsizingTheLabelBudget() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(300, 720, 1F, SmartTopInteractionProfile.TOUCH),
				List.of(SmartTopAction.OPEN_ADDONS),
				new SmartTopContentMetrics(110, 60, 0));
		assertEquals(SmartTopTerminalActionStyle.LABEL_ONLY, spec.terminalActionStyle());
		assertTrue(spec.terminalActionWidthDp() >= 84);
	}

	@Test
	public void fontPressureCanDemoteWidthClassAndUsesBucketedHeight() {
		SmartTopLayoutSpec normal = SmartTopAdaptivePolicy.resolve(
				env(600, 800, 1F, SmartTopInteractionProfile.TOUCH), CURRENT,
				SmartTopContentMetrics.empty());
		SmartTopLayoutSpec accessible = SmartTopAdaptivePolicy.resolve(
				env(600, 800, 1.5F, SmartTopInteractionProfile.TOUCH), CURRENT,
				SmartTopContentMetrics.empty());
		assertEquals(SmartTopLayoutMode.STANDARD, normal.mode());
		assertEquals(SmartTopLayoutMode.COMPACT, accessible.mode());
		assertTrue(accessible.cardHeightDp() > normal.cardHeightDp());
		assertEquals(SmartTopAdaptivePolicy.cardHeightDp(accessible.mode(), 1.5F),
				accessible.cardHeightDp());
	}

	@Test
	public void portraitLandscapePortraitReturnsToTheOriginalAdaptiveSpecClass() {
		SmartTopEnvironment portrait = env(384, 780, 1F, SmartTopInteractionProfile.TOUCH);
		SmartTopEnvironment landscape = env(704, 360, 1F, SmartTopInteractionProfile.TOUCH);
		SmartTopLayoutSpec first = SmartTopAdaptivePolicy.resolve(portrait, CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		SmartTopLayoutSpec rotated = SmartTopAdaptivePolicy.resolve(landscape, CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		SmartTopLayoutSpec restored = SmartTopAdaptivePolicy.resolve(portrait, CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		assertEquals(SmartTopLayoutMode.COMPACT, first.mode());
		assertEquals(SmartTopLayoutMode.STANDARD, rotated.mode());
		assertEquals(first.mode(), restored.mode());
		assertEquals(first.cardHeightDp(), rotated.cardHeightDp());
		assertEquals(first.cardHeightDp(), restored.cardHeightDp());
		assertEquals(first.visibleActions(), restored.visibleActions());
		assertEquals(3, first.recentRows());
		assertEquals(3, rotated.recentRows());
	}

	private static SmartTopEnvironment env(float width, float height, float scale,
			SmartTopInteractionProfile profile) {
		return new SmartTopEnvironment(width, height, scale, profile);
	}
}
