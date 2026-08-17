package me.aap.fermata.ui.smarttop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class SmartTopPresentationPolicyTest {
	private static final List<SmartTopAction> CURRENT = List.of(
			SmartTopAction.PREVIOUS, SmartTopAction.PLAY_PAUSE, SmartTopAction.NEXT,
			SmartTopAction.OPEN_CONTEXT, SmartTopAction.FAVORITE);

	@Test
	public void automotiveCellsNeverShrinkOrOverlap() {
		SmartTopPresentationPolicy.Presentation narrow = SmartTopPresentationPolicy.resolve(
				700F, 1F, true, SmartTopLayoutMode.STANDARD, CURRENT, true, 24);
		assertEquals(76, narrow.actionCellDp());
		assertEquals(44, narrow.primaryGlyphDp());
		assertEquals(36, narrow.secondaryGlyphDp());
		assertEquals(0, narrow.actionGapDp());
		assertEquals(380, narrow.railWidthDp());
		assertTrue(narrow.actionCellDp() + narrow.actionGapDp() >= 76);

		SmartTopPresentationPolicy.Presentation wide = SmartTopPresentationPolicy.resolve(
				1186F, 1F, true, SmartTopLayoutMode.EXPANDED, CURRENT, true, 24);
		assertEquals(24, wide.actionGapDp());
		assertEquals(476, wide.railWidthDp());
		assertTrue(wide.actionCellDp() + wide.actionGapDp() >= 76);
	}

	@Test
	public void labeledActionsParticipateInTheSameMeasuredRail() {
		List<SmartTopAction> recovery = List.of(SmartTopAction.RETRY, SmartTopAction.OPEN_CONTEXT);
		SmartTopPresentationPolicy.Presentation presentation = SmartTopPresentationPolicy.resolve(
				700F, 1F, true, SmartTopLayoutMode.STANDARD, recovery, false, 12);
		assertEquals(24, presentation.actionGapDp());
		assertEquals(212, presentation.railWidthDp());
		assertEquals(recovery, presentation.visibleActions());
	}

	@Test
	public void mobileKeepsTheExistingVisualBaseline() {
		SmartTopPresentationPolicy.Presentation presentation = SmartTopPresentationPolicy.resolve(
				384F, 1F, false, SmartTopLayoutMode.COMPACT,
				List.of(SmartTopAction.PLAY_PAUSE, SmartTopAction.OPEN_CONTEXT), false, 20);
		assertEquals(48, presentation.actionCellDp());
		assertEquals(22, presentation.primaryGlyphDp());
		assertEquals(22, presentation.secondaryGlyphDp());
		assertEquals(4, presentation.actionGapDp());
	}

	@Test
	public void semanticActionsMapToStablePhysicalSlots() {
		assertEquals(0, SmartTopPresentationPolicy.slotIndex(SmartTopAction.PREVIOUS));
		assertEquals(1, SmartTopPresentationPolicy.slotIndex(SmartTopAction.PLAY));
		assertEquals(1, SmartTopPresentationPolicy.slotIndex(SmartTopAction.PLAY_PAUSE));
		assertEquals(2, SmartTopPresentationPolicy.slotIndex(SmartTopAction.NEXT));
		assertEquals(3, SmartTopPresentationPolicy.slotIndex(SmartTopAction.OPEN_CONTEXT));
		assertEquals(4, SmartTopPresentationPolicy.slotIndex(SmartTopAction.FAVORITE));
		assertEquals(-1, SmartTopPresentationPolicy.slotIndex(SmartTopAction.OPEN_ADDONS));
		assertEquals(-1, SmartTopPresentationPolicy.slotIndex(SmartTopAction.RETRY));
	}
}
