package me.aap.fermata.ui.smarttop;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure adaptive policy shared by every SmartTop renderer host. Space decides composition; the
 * interaction profile decides control geometry. SmartTop is surfaced on automotive Dashboard
 * hosts at every width, while phone Dashboard intentionally omits it.
 */
public final class SmartTopAdaptivePolicy {
	public static final int TOUCH_ACTION_CELL_DP = 48;
	public static final int AUTOMOTIVE_MAX_ACTION_CELL_DP = 76;
	public static final int AUTOMOTIVE_MIN_ACTION_CELL_DP = 56;
	public static final int TOUCH_GLYPH_DP = 22;
	public static final int AUTOMOTIVE_PRIMARY_GLYPH_DP = 44;
	public static final int AUTOMOTIVE_SECONDARY_GLYPH_DP = 36;
	public static final int TOUCH_ACTION_GAP_DP = 4;
	public static final int AUTOMOTIVE_MAX_GAP_DP = 6;
	private static final int BASE_CARD_HEIGHT_DP = 152;
	private static final int TERMINAL_HORIZONTAL_PADDING_DP = 24;
	private static final int TERMINAL_ICON_GAP_DP = 8;

	private SmartTopAdaptivePolicy() {
	}

	public static SmartTopLayoutSpec resolve(SmartTopEnvironment env,
			List<SmartTopAction> semanticActions, SmartTopContentMetrics metrics) {
		SmartTopLayoutMode mode = resolveMode(env);
		boolean automotive = env.interaction().isAutomotive();
		float fontScale = Math.max(1F, env.fontScale());
		int cardPaddingDp = cardPaddingDp(mode, automotive);
		int artworkSizeDp = artworkSizeDp(mode, automotive, fontScale);
		int artworkPaddingDp = artworkPaddingDp(mode, automotive);
		int cardHeightDp = cardHeightDp(mode, fontScale);
		SmartTopTypographyPolicy.Typography typography =
				SmartTopTypographyPolicy.resolve(mode, fontScale);

		int cellDp = actionCellDp(env);
		int primaryGlyphDp = primaryGlyphDp(cellDp, automotive);
		int secondaryGlyphDp = secondaryGlyphDp(cellDp, automotive);
		int availableDp = Math.max(0, Math.round(env.contentWidthDp()));
		int fixedDp = (cardPaddingDp * 2) + artworkSizeDp + 10 +
				titleReserveDp(mode, fontScale, metrics.titleWidthDp());

		List<SmartTopAction> actions = new ArrayList<>(semanticActions);
		boolean hasTerminal = actions.stream().anyMatch(SmartTopAdaptivePolicy::isLabeled);
		SmartTopTerminalActionStyle terminalStyle = SmartTopTerminalActionStyle.ICON_LABEL;
		int terminalWidthDp = hasTerminal ? terminalWidthDp(metrics.terminalLabelWidthDp(),
				cellDp, secondaryGlyphDp, terminalStyle) : 0;

		// On Android Auto Quick Recent is a persistent peer of metadata, not an optional region.
		// With valid data it remains visible at every width; narrow layouts compress actions/title
		// around it instead of dropping the panel.
		int recentRows = recentRows(metrics.recentItems());
		int recentPanelWidthDp = (recentRows > 0) ? recentPanelWidthDp(mode) : 0;
		int gapDp = preferredGapDp(env.contentWidthDp(), automotive, actions);

		gapDp = fitGapDp(availableDp, fixedDp, recentPanelWidthDp, actions,
				cellDp, terminalWidthDp, gapDp);

		if (hasTerminal && (requiredWidthDp(fixedDp, actions, cellDp, gapDp,
				terminalWidthDp, recentPanelWidthDp) > availableDp)) {
			terminalStyle = SmartTopTerminalActionStyle.LABEL_ONLY;
			terminalWidthDp = terminalWidthDp(metrics.terminalLabelWidthDp(),
					cellDp, secondaryGlyphDp, terminalStyle);
			gapDp = fitGapDp(availableDp, fixedDp, recentPanelWidthDp, actions,
					cellDp, terminalWidthDp, gapDp);
		}

		// Favorite yields before primary playback controls. Next and Back are not part of
		// SmartTop semantics, leaving more width for title + persistent Quick Recent.
		if ((requiredWidthDp(fixedDp, actions, cellDp, gapDp, terminalWidthDp,
				recentPanelWidthDp) > availableDp) && actions.remove(SmartTopAction.FAVORITE)) {
			gapDp = fitGapDp(availableDp, fixedDp, recentPanelWidthDp, actions,
					cellDp, terminalWidthDp, gapDp);
		}

		if (requiredWidthDp(fixedDp, actions, cellDp, gapDp, terminalWidthDp,
				recentPanelWidthDp) > availableDp) {
			// PLAY/PLAY_PAUSE is invariant. Previous is the final transport fallback.
			actions.remove(SmartTopAction.PREVIOUS);
			gapDp = fitGapDp(availableDp, fixedDp, recentPanelWidthDp, actions,
					cellDp, terminalWidthDp, gapDp);
		}

		return new SmartTopLayoutSpec(mode, cardHeightDp, cardPaddingDp,
				artworkSizeDp, artworkPaddingDp, typography, cellDp, primaryGlyphDp,
				secondaryGlyphDp, gapDp, actions, terminalStyle, terminalWidthDp,
				recentRows, recentPanelWidthDp);
	}

	public static SmartTopLayoutMode resolveMode(SmartTopEnvironment env) {
		float widthDp = env.contentWidthDp();
		float scale = Math.max(1F, env.fontScale());
		if ((widthDp < 460F) || ((widthDp < 700F) && (scale >= 1.5F))) {
			return SmartTopLayoutMode.COMPACT;
		}
		if ((widthDp >= 1000F) && (scale <= 1.5F)) return SmartTopLayoutMode.EXPANDED;
		return SmartTopLayoutMode.STANDARD;
	}

	static int slotIndex(SmartTopAction action) {
		return switch (action) {
			case PREVIOUS -> 0;
			case PLAY, PLAY_PAUSE -> 1;
			case FAVORITE -> 2;
			default -> -1;
		};
	}

	static SmartTopAction actionAtSlot(List<SmartTopAction> actions, int slot) {
		for (SmartTopAction action : actions) if (slotIndex(action) == slot) return action;
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

	static int actionCellDp(SmartTopEnvironment env) {
		if (!env.interaction().isAutomotive()) return TOUCH_ACTION_CELL_DP;
		float width = Math.max(0F, env.contentWidthDp());
		int cell = (width >= 1000F) ? AUTOMOTIVE_MAX_ACTION_CELL_DP :
				(width >= 820F) ? 68 : 60;
		return switch (SmartTopTypographyPolicy.fontScaleBucket(env.fontScale())) {
			case 0, 1 -> cell;
			case 2 -> Math.max(AUTOMOTIVE_MIN_ACTION_CELL_DP, cell - 4);
			default -> Math.max(AUTOMOTIVE_MIN_ACTION_CELL_DP, cell - 8);
		};
	}

	private static int primaryGlyphDp(int cellDp, boolean automotive) {
		if (!automotive) return TOUCH_GLYPH_DP;
		return Math.min(AUTOMOTIVE_PRIMARY_GLYPH_DP,
				Math.max(30, Math.round(cellDp * 0.58F)));
	}

	private static int secondaryGlyphDp(int cellDp, boolean automotive) {
		if (!automotive) return TOUCH_GLYPH_DP;
		return Math.min(AUTOMOTIVE_SECONDARY_GLYPH_DP,
				Math.max(24, Math.round(cellDp * 0.46F)));
	}

	private static int requiredWidthDp(int fixedDp, List<SmartTopAction> actions,
			int cellDp, int gapDp, int terminalWidthDp, int recentPanelWidthDp) {
		return fixedDp + railWidthDp(actions, cellDp, gapDp, terminalWidthDp) + recentPanelWidthDp;
	}

	private static int railWidthDp(List<SmartTopAction> actions, int cellDp,
			int gapDp, int terminalWidthDp) {
		int width = 0;
		int count = 0;
		for (SmartTopAction action : actions) {
			if (isLabeled(action)) {
				width += terminalWidthDp;
				count++;
			} else if (slotIndex(action) >= 0) {
				width += cellDp;
				count++;
			}
		}
		return width + Math.max(0, count - 1) * gapDp;
	}

	private static int preferredGapDp(float widthDp, boolean automotive,
			List<SmartTopAction> actions) {
		if (componentCount(actions) <= 1) return 0;
		if (!automotive) return TOUCH_ACTION_GAP_DP;
		float width = Math.max(0F, widthDp);
		int gap = (width >= 1000F) ? AUTOMOTIVE_MAX_GAP_DP : (width >= 820F) ? 4 : 2;
		return Math.min(AUTOMOTIVE_MAX_GAP_DP, gap);
	}

	private static int fitGapDp(int availableDp, int fixedDp, int recentPanelWidthDp,
			List<SmartTopAction> actions, int cellDp, int terminalWidthDp, int preferredGapDp) {
		int count = componentCount(actions);
		if (count <= 1) return 0;
		int baseRail = railWidthDp(actions, cellDp, 0, terminalWidthDp);
		int gapBudget = availableDp - fixedDp - recentPanelWidthDp - baseRail;
		return Math.max(0, Math.min(preferredGapDp, gapBudget / (count - 1)));
	}

	private static int terminalWidthDp(float labelWidthDp, int cellDp, int glyphDp,
			SmartTopTerminalActionStyle style) {
		int label = Math.max(0, (int) Math.ceil(labelWidthDp));
		int width = label + TERMINAL_HORIZONTAL_PADDING_DP;
		if (style == SmartTopTerminalActionStyle.ICON_LABEL) {
			width += glyphDp + TERMINAL_ICON_GAP_DP;
		}
		return Math.max(cellDp, width);
	}

	private static int recentRows(int recentItems) {
		if (recentItems <= 0) return 0;
		return Math.min(recentItems, SmartTopViewState.MAX_QUICK_RECENT);
	}

	private static int recentPanelWidthDp(SmartTopLayoutMode mode) {
		return switch (mode) {
			case COMPACT -> 148;
			case STANDARD -> 160;
			case EXPANDED -> 196;
		};
	}

	private static int cardPaddingDp(SmartTopLayoutMode mode, boolean automotive) {
		return switch (mode) {
			case COMPACT -> automotive ? 10 : 12;
			case STANDARD -> 12;
			case EXPANDED -> 14;
		};
	}

	private static int artworkSizeDp(SmartTopLayoutMode mode, boolean automotive, float fontScale) {
		int base = switch (mode) {
			case COMPACT -> automotive ? 64 : 56;
			case STANDARD -> 72;
			case EXPANDED -> 80;
		};
		if (!automotive) return base;
		return switch (SmartTopTypographyPolicy.fontScaleBucket(fontScale)) {
			case 0, 1 -> base;
			case 2 -> Math.max(58, base - 4);
			default -> Math.max(56, base - 8);
		};
	}

	private static int artworkPaddingDp(SmartTopLayoutMode mode, boolean automotive) {
		return switch (mode) {
			case COMPACT -> automotive ? 12 : 11;
			case STANDARD -> 14;
			case EXPANDED -> 16;
		};
	}

	/**
	 * Card height is width-class invariant. Cold-start fallback width and the first measured
	 * RecyclerView width may resolve different composition classes, but that transition must never
	 * resize the Dashboard card. Only the stable font-scale bucket can change height.
	 */
	static int cardHeightDp(SmartTopLayoutMode ignoredMode, float fontScale) {
		int extra = switch (SmartTopTypographyPolicy.fontScaleBucket(fontScale)) {
			case 0 -> 0;
			case 1 -> 10;
			case 2 -> 18;
			default -> 30;
		};
		return BASE_CARD_HEIGHT_DP + extra;
	}

	/** Reserve a useful two-line text column; a long intrinsic one-line title never owns the rail. */
	private static int titleReserveDp(SmartTopLayoutMode mode, float fontScale, float titleWidthDp) {
		int base = switch (mode) {
			case COMPACT -> 132;
			case STANDARD -> 178;
			case EXPANDED -> 220;
		};
		int max = switch (mode) {
			case COMPACT -> 180;
			case STANDARD -> 230;
			case EXPANDED -> 300;
		};
		int scaleExtra = switch (SmartTopTypographyPolicy.fontScaleBucket(fontScale)) {
			case 0 -> 0;
			case 1 -> 10;
			case 2 -> 18;
			default -> 26;
		};
		int twoLineIntrinsic = Math.max(0, (int) Math.ceil(titleWidthDp / 2F));
		return Math.min(max, Math.max(base + scaleExtra, twoLineIntrinsic));
	}
}
