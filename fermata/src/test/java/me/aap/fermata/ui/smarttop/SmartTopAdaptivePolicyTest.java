package me.aap.fermata.ui.smarttop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class SmartTopAdaptivePolicyTest {
	private static final List<SmartTopAction> CURRENT = List.of(
			SmartTopAction.PREVIOUS, SmartTopAction.PLAY_PAUSE, SmartTopAction.NEXT,
			SmartTopAction.OPEN_CONTEXT, SmartTopAction.FAVORITE);

	@Test
	public void spaceClassDoesNotDependOnHostIdentity() {
		SmartTopEnvironment touch = env(700, 360, 1F, SmartTopInteractionProfile.TOUCH);
		SmartTopEnvironment auto = env(700, 480, 1F, SmartTopInteractionProfile.AUTOMOTIVE);
		assertEquals(SmartTopLayoutMode.STANDARD, SmartTopAdaptivePolicy.resolveMode(touch));
		assertEquals(SmartTopLayoutMode.STANDARD, SmartTopAdaptivePolicy.resolveMode(auto));
	}

	@Test
	public void narrowPortraitKeepsPrimaryTransportAndDropsSpaceHungryActions() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(313, 720, 1F, SmartTopInteractionProfile.TOUCH), CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		assertEquals(SmartTopLayoutMode.COMPACT, spec.mode());
		assertTrue(spec.visibleActions().contains(SmartTopAction.PLAY_PAUSE));
		assertFalse(spec.visibleActions().contains(SmartTopAction.OPEN_CONTEXT));
		assertFalse(spec.visibleActions().contains(SmartTopAction.FAVORITE));
		assertEquals(0, spec.recentRows());
		assertEquals(160, spec.cardHeightDp());
	}

	@Test
	public void phoneLandscapeCanUseStandardCompositionAndRecentWithoutOrientationRules() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(700, 360, 1F, SmartTopInteractionProfile.TOUCH), CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		assertEquals(SmartTopLayoutMode.STANDARD, spec.mode());
		assertEquals(3, spec.recentRows());
		assertTrue(spec.showQuickRecent());
		assertTrue(spec.centerActionRail());
	}

	@Test
	public void automotiveInteractionRaisesControlGeometryWithoutChangingSpaceClass() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(700, 480, 1F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(120, 0, 3));
		assertEquals(SmartTopLayoutMode.STANDARD, spec.mode());
		assertEquals(76, spec.actionCellDp());
		assertEquals(44, spec.primaryGlyphDp());
		assertEquals(36, spec.secondaryGlyphDp());
		assertEquals(0, spec.recentRows());
	}

	@Test
	public void wideAutomotiveViewportFitsThreeRecentRows() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				env(1176, 720, 1F, SmartTopInteractionProfile.AUTOMOTIVE), CURRENT,
				new SmartTopContentMetrics(180, 0, 3));
		assertEquals(SmartTopLayoutMode.EXPANDED, spec.mode());
		assertEquals(3, spec.recentRows());
		assertEquals(196, spec.recentPanelWidthDp());
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
	public void fontPressureCanDemoteWidthClassAndIncreaseRequiredHeight() {
		SmartTopLayoutSpec normal = SmartTopAdaptivePolicy.resolve(
				env(600, 800, 1F, SmartTopInteractionProfile.TOUCH), CURRENT,
				SmartTopContentMetrics.empty());
		SmartTopLayoutSpec accessible = SmartTopAdaptivePolicy.resolve(
				env(600, 800, 1.5F, SmartTopInteractionProfile.TOUCH), CURRENT,
				SmartTopContentMetrics.empty());
		assertEquals(SmartTopLayoutMode.STANDARD, normal.mode());
		assertEquals(SmartTopLayoutMode.COMPACT, accessible.mode());
		assertTrue(accessible.cardHeightDp() > normal.cardHeightDp());
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
		assertEquals(first.cardHeightDp(), restored.cardHeightDp());
		assertEquals(first.visibleActions(), restored.visibleActions());
		assertEquals(0, first.recentRows());
		assertEquals(3, rotated.recentRows());
	}

	private static SmartTopEnvironment env(float width, float height, float scale,
			SmartTopInteractionProfile profile) {
		return new SmartTopEnvironment(width, height, scale, profile);
	}
}
