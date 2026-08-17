package me.aap.fermata.ui.smarttop;

import java.util.List;

/** Pure measured-width presentation budget shared by the SmartTop renderer and binder. */
public final class SmartTopPresentationPolicy {
	public static final int AUTO_ACTION_CELL_DP = 76;
	public static final int AUTO_PRIMARY_GLYPH_DP = 44;
	public static final int AUTO_SECONDARY_GLYPH_DP = 36;
	public static final int AUTO_MAX_GAP_DP = 24;
	public static final int MOBILE_ACTION_CELL_DP = 48;
	public static final int MOBILE_GLYPH_DP = 22;
	public static final int MOBILE_GAP_DP = 4;
	private static final float AUTO_RAIL_FRACTION = 0.48F;

	private SmartTopPresentationPolicy() {
	}

	public static Presentation resolve(float measuredWidthDp, float fontScale, boolean automotive,
			SmartTopLayoutMode layout, List<SmartTopAction> semanticActions,
			boolean quickRecentAvailable, int titleLength) {
		List<SmartTopAction> actions = List.copyOf(semanticActions);
		boolean showQuickRecent = quickRecentAvailable && (layout != SmartTopLayoutMode.COMPACT);
		int cellDp = automotive ? AUTO_ACTION_CELL_DP : MOBILE_ACTION_CELL_DP;
		int primaryGlyphDp = automotive ? AUTO_PRIMARY_GLYPH_DP : MOBILE_GLYPH_DP;
		int secondaryGlyphDp = automotive ? AUTO_SECONDARY_GLYPH_DP : MOBILE_GLYPH_DP;
		int componentCount = componentCount(actions);
		int baseRailDp = baseRailWidthDp(actions, automotive, cellDp);
		int gapDp;
		if (componentCount <= 1) {
			gapDp = 0;
		} else if (automotive) {
			int preferredBudget = Math.max(baseRailDp,
					Math.round(Math.max(0F, measuredWidthDp) * AUTO_RAIL_FRACTION));
			gapDp = Math.max(0, Math.min(AUTO_MAX_GAP_DP,
					(preferredBudget - baseRailDp) / (componentCount - 1)));
		} else {
			gapDp = MOBILE_GAP_DP;
		}
		int railWidthDp = baseRailDp + (Math.max(0, componentCount - 1) * gapDp);
		return new Presentation(actions, showQuickRecent, cellDp, primaryGlyphDp,
				secondaryGlyphDp, gapDp, railWidthDp);
	}

	static int slotIndex(SmartTopAction action) {
		return switch (action) {
			case PREVIOUS -> 0;
			case PLAY, PLAY_PAUSE -> 1;
			case NEXT -> 2;
			case OPEN_CONTEXT, HISTORY -> 3;
			case FAVORITE -> 4;
			default -> -1;
		};
	}

	static SmartTopAction actionAtSlot(List<SmartTopAction> actions, int slot) {
		for (SmartTopAction action : actions) {
			if (slotIndex(action) == slot) return action;
		}
		return null;
	}

	static boolean isLabeled(SmartTopAction action) {
		return (action == SmartTopAction.OPEN_ADDONS) || (action == SmartTopAction.RETRY);
	}

	static int componentCount(List<SmartTopAction> actions) {
		int count = 0;
		for (SmartTopAction action : actions) {
			if (isLabeled(action) || (slotIndex(action) >= 0)) count++;
		}
		return count;
	}

	private static int baseRailWidthDp(List<SmartTopAction> actions,
			boolean automotive, int cellDp) {
		int width = 0;
		for (SmartTopAction action : actions) {
			if (isLabeled(action)) {
				width += automotive ? SmartTopLayoutPolicy.MAX_LABELED_ACTION_DP :
						SmartTopLayoutPolicy.MAX_MOBILE_LABELED_ACTION_DP;
			} else if (slotIndex(action) >= 0) {
				width += cellDp;
			}
		}
		return width;
	}

	public record Presentation(List<SmartTopAction> visibleActions, boolean showQuickRecent,
			int actionCellDp, int primaryGlyphDp, int secondaryGlyphDp,
			int actionGapDp, int railWidthDp) {
		public Presentation {
			visibleActions = List.copyOf(visibleActions);
		}
	}
}
