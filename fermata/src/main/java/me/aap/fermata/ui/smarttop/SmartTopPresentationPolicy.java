package me.aap.fermata.ui.smarttop;

import java.util.ArrayList;
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
		List<SmartTopAction> actions = new ArrayList<>(semanticActions);
		int cellDp = automotive ? AUTO_ACTION_CELL_DP : MOBILE_ACTION_CELL_DP;
		int primaryGlyphDp = automotive ? AUTO_PRIMARY_GLYPH_DP : MOBILE_GLYPH_DP;
		int secondaryGlyphDp = automotive ? AUTO_SECONDARY_GLYPH_DP : MOBILE_GLYPH_DP;
		int availableDp = Math.max(0, Math.round(measuredWidthDp));
		int fixedDp = fixedContentDp(layout, automotive) + titleReserveDp(layout, fontScale, titleLength);
		boolean showQuickRecent = quickRecentAvailable && (layout != SmartTopLayoutMode.COMPACT);
		int recentDp = showQuickRecent ? quickRecentWidthDp(layout) : 0;
		int gapDp = preferredGapDp(measuredWidthDp, automotive, actions, cellDp);

		if (requiredWidthDp(fixedDp, actions, automotive, cellDp, gapDp, recentDp) > availableDp) {
			showQuickRecent = false;
			recentDp = 0;
		}

		gapDp = fitGapDp(availableDp, fixedDp, recentDp, actions, automotive, cellDp, gapDp);
		if (requiredWidthDp(fixedDp, actions, automotive, cellDp, gapDp, recentDp) > availableDp) {
			removeAuxiliary(actions, SmartTopAction.FAVORITE);
			gapDp = fitGapDp(availableDp, fixedDp, recentDp, actions, automotive, cellDp, gapDp);
		}
		if (requiredWidthDp(fixedDp, actions, automotive, cellDp, gapDp, recentDp) > availableDp) {
			removeAuxiliary(actions, SmartTopAction.OPEN_CONTEXT);
			gapDp = fitGapDp(availableDp, fixedDp, recentDp, actions, automotive, cellDp, gapDp);
		}

		int railWidthDp = railWidthDp(actions, automotive, cellDp, gapDp);
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

	private static int preferredGapDp(float measuredWidthDp, boolean automotive,
			List<SmartTopAction> actions, int cellDp) {
		int count = componentCount(actions);
		if (count <= 1) return 0;
		if (!automotive) return MOBILE_GAP_DP;
		int baseRailDp = baseRailWidthDp(actions, true, cellDp);
		int preferredBudget = Math.max(baseRailDp,
				Math.round(Math.max(0F, measuredWidthDp) * AUTO_RAIL_FRACTION));
		return Math.max(0, Math.min(AUTO_MAX_GAP_DP,
				(preferredBudget - baseRailDp) / (count - 1)));
	}

	private static int fitGapDp(int availableDp, int fixedDp, int recentDp,
			List<SmartTopAction> actions, boolean automotive, int cellDp, int preferredGapDp) {
		int count = componentCount(actions);
		if ((count <= 1) || !automotive) return preferredGapDp;
		int baseRailDp = baseRailWidthDp(actions, true, cellDp);
		int gapBudget = availableDp - fixedDp - recentDp - baseRailDp;
		return Math.max(0, Math.min(preferredGapDp, gapBudget / (count - 1)));
	}

	private static int requiredWidthDp(int fixedDp, List<SmartTopAction> actions,
			boolean automotive, int cellDp, int gapDp, int recentDp) {
		return fixedDp + railWidthDp(actions, automotive, cellDp, gapDp) + recentDp;
	}

	private static int railWidthDp(List<SmartTopAction> actions,
			boolean automotive, int cellDp, int gapDp) {
		int count = componentCount(actions);
		return baseRailWidthDp(actions, automotive, cellDp) + Math.max(0, count - 1) * gapDp;
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

	private static void removeAuxiliary(List<SmartTopAction> actions, SmartTopAction action) {
		actions.remove(action);
	}

	private static int quickRecentWidthDp(SmartTopLayoutMode layout) {
		return switch (layout) {
			case COMPACT -> 0;
			case STANDARD -> 160;
			case EXPANDED -> 196;
		};
	}

	private static int fixedContentDp(SmartTopLayoutMode layout, boolean automotive) {
		int padding = switch (layout) {
			case COMPACT -> automotive ? 10 : 12;
			case STANDARD -> 14;
			case EXPANDED -> 16;
		};
		int artwork = (!automotive && (layout == SmartTopLayoutMode.COMPACT)) ?
				SmartTopLayoutPolicy.MOBILE_ARTWORK_DP : layout.artworkSizeDp();
		return (padding * 2) + artwork + 10;
	}

	private static int titleReserveDp(SmartTopLayoutMode layout, float fontScale, int titleLength) {
		int base = switch (layout) {
			case COMPACT -> 148;
			case STANDARD -> 180;
			case EXPANDED -> 220;
		};
		int scaleExtra = Math.round(Math.max(0F, fontScale - 1F) * 72F);
		int lengthExtra = Math.max(0, Math.min(48, (titleLength - 48) * 2));
		return base + scaleExtra + lengthExtra;
	}

	public record Presentation(List<SmartTopAction> visibleActions, boolean showQuickRecent,
			int actionCellDp, int primaryGlyphDp, int secondaryGlyphDp,
			int actionGapDp, int railWidthDp) {
		public Presentation {
			visibleActions = List.copyOf(visibleActions);
		}
	}
}
