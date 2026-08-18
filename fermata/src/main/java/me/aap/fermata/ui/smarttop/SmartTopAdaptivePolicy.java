package me.aap.fermata.ui.smarttop;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure adaptive policy shared by phone, tablet, mirror and Android Auto/DHU.
 * Space decides composition; the interaction profile only decides minimum control geometry.
 */
public final class SmartTopAdaptivePolicy {
	public static final int TOUCH_ACTION_CELL_DP = 48;
	public static final int AUTOMOTIVE_ACTION_CELL_DP = 76;
	public static final int TOUCH_GLYPH_DP = 22;
	public static final int AUTOMOTIVE_PRIMARY_GLYPH_DP = 44;
	public static final int AUTOMOTIVE_SECONDARY_GLYPH_DP = 36;
	public static final int TOUCH_ACTION_GAP_DP = 4;
	public static final int AUTOMOTIVE_MAX_GAP_DP = 24;
	private static final int RECENT_ROW_DP = 28;
	private static final int RECENT_HEADER_DP = 22;
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
		int artworkSizeDp = artworkSizeDp(mode, automotive);
		int artworkPaddingDp = artworkPaddingDp(mode, automotive);
		int cardHeightDp = cardHeightDp(mode, fontScale, env.viewportHeightDp());
		SmartTopTypographyPolicy.Typography typography = SmartTopTypographyPolicy.resolve(mode);

		int cellDp = automotive ? AUTOMOTIVE_ACTION_CELL_DP : TOUCH_ACTION_CELL_DP;
		int primaryGlyphDp = automotive ? AUTOMOTIVE_PRIMARY_GLYPH_DP : TOUCH_GLYPH_DP;
		int secondaryGlyphDp = automotive ? AUTOMOTIVE_SECONDARY_GLYPH_DP : TOUCH_GLYPH_DP;
		int availableDp = Math.max(0, Math.round(env.contentWidthDp()));
		int fixedDp = (cardPaddingDp * 2) + artworkSizeDp + 10 +
				titleReserveDp(mode, fontScale, metrics.titleWidthDp());

		List<SmartTopAction> actions = new ArrayList<>(semanticActions);
		boolean hasTerminal = actions.stream().anyMatch(SmartTopAdaptivePolicy::isLabeled);
		SmartTopTerminalActionStyle terminalStyle = SmartTopTerminalActionStyle.ICON_LABEL;
		int terminalWidthDp = hasTerminal ? terminalWidthDp(metrics.terminalLabelWidthDp(),
				cellDp, secondaryGlyphDp, terminalStyle) : 0;

		int recentRows = recentRows(mode, cardHeightDp, cardPaddingDp, metrics.recentItems());
		int recentPanelWidthDp = (recentRows > 0) ? recentPanelWidthDp(mode) : 0;
		int gapDp = preferredGapDp(env.contentWidthDp(), automotive, actions, cellDp, terminalWidthDp);

		if (requiredWidthDp(fixedDp, actions, cellDp, gapDp, terminalWidthDp,
				recentPanelWidthDp) > availableDp) {
			recentRows = 0;
			recentPanelWidthDp = 0;
		}

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

		for (SmartTopAction auxiliary : new SmartTopAction[]{
				SmartTopAction.FAVORITE, SmartTopAction.OPEN_CONTEXT, SmartTopAction.HISTORY}) {
			if (requiredWidthDp(fixedDp, actions, cellDp, gapDp, terminalWidthDp,
					recentPanelWidthDp) <= availableDp) break;
			actions.remove(auxiliary);
			gapDp = fitGapDp(availableDp, fixedDp, recentPanelWidthDp, actions,
					cellDp, terminalWidthDp, gapDp);
		}

		if (requiredWidthDp(fixedDp, actions, cellDp, gapDp, terminalWidthDp,
				recentPanelWidthDp) > availableDp) {
			// PLAY/PLAY_PAUSE is the invariant transport control. Previous/Next are a pair that
			// yields only when the measured viewport cannot preserve a useful text reserve.
			actions.remove(SmartTopAction.PREVIOUS);
			actions.remove(SmartTopAction.NEXT);
			gapDp = fitGapDp(availableDp, fixedDp, recentPanelWidthDp, actions,
					cellDp, terminalWidthDp, gapDp);
		}

		return new SmartTopLayoutSpec(mode, cardHeightDp, cardPaddingDp,
				artworkSizeDp, artworkPaddingDp, typography, cellDp, primaryGlyphDp,
				secondaryGlyphDp, gapDp, actions, terminalStyle, terminalWidthDp,
				recentRows, recentPanelWidthDp, mode != SmartTopLayoutMode.COMPACT);
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
			case NEXT -> 2;
			case OPEN_CONTEXT, HISTORY -> 3;
			case FAVORITE -> 4;
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
			List<SmartTopAction> actions, int cellDp, int terminalWidthDp) {
		int count = componentCount(actions);
		if (count <= 1) return 0;
		if (!automotive) return TOUCH_ACTION_GAP_DP;
		int baseRail = railWidthDp(actions, cellDp, 0, terminalWidthDp);
		int preferredBudget = Math.max(baseRail, Math.round(Math.max(0F, widthDp) * 0.48F));
		return Math.max(0, Math.min(AUTOMOTIVE_MAX_GAP_DP,
				(preferredBudget - baseRail) / (count - 1)));
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

	private static int recentRows(SmartTopLayoutMode mode, int cardHeightDp,
			int paddingDp, int recentItems) {
		if ((mode == SmartTopLayoutMode.COMPACT) || (recentItems <= 0)) return 0;
		int innerHeight = Math.max(0, cardHeightDp - (paddingDp * 2));
		int capacity = Math.max(0, (innerHeight - RECENT_HEADER_DP) / RECENT_ROW_DP);
		return Math.min(recentItems, Math.min(SmartTopViewState.MAX_QUICK_RECENT, capacity));
	}

	private static int recentPanelWidthDp(SmartTopLayoutMode mode) {
		return switch (mode) {
			case COMPACT -> 0;
			case STANDARD -> 160;
			case EXPANDED -> 196;
		};
	}

	private static int cardPaddingDp(SmartTopLayoutMode mode, boolean automotive) {
		return switch (mode) {
			case COMPACT -> automotive ? 10 : 12;
			case STANDARD -> 14;
			case EXPANDED -> 16;
		};
	}

	private static int artworkSizeDp(SmartTopLayoutMode mode, boolean automotive) {
		return switch (mode) {
			case COMPACT -> automotive ? 66 : 56;
			case STANDARD -> 80;
			case EXPANDED -> 88;
		};
	}

	private static int artworkPaddingDp(SmartTopLayoutMode mode, boolean automotive) {
		return switch (mode) {
			case COMPACT -> automotive ? 13 : 11;
			case STANDARD -> 16;
			case EXPANDED -> 18;
		};
	}

	private static int cardHeightDp(SmartTopLayoutMode mode, float fontScale, float viewportHeightDp) {
		int base = switch (mode) {
			case COMPACT -> 160;
			case STANDARD -> 144;
			case EXPANDED -> 152;
		};
		int min = switch (mode) {
			case COMPACT -> 148;
			case STANDARD -> 136;
			case EXPANDED -> 144;
		};
		float boundedScale = Math.max(1F, Math.min(2F, fontScale));
		int target = base + Math.round(40F * (boundedScale - 1F));
		if (viewportHeightDp <= 0F) return target;
		int viewportBudget = Math.max(min, Math.round(viewportHeightDp * 0.52F));
		return Math.max(min, Math.min(target, viewportBudget));
	}

	private static int titleReserveDp(SmartTopLayoutMode mode, float fontScale, float titleWidthDp) {
		int base = switch (mode) {
			case COMPACT -> 110;
			case STANDARD -> 150;
			case EXPANDED -> 190;
		};
		int max = switch (mode) {
			case COMPACT -> 170;
			case STANDARD -> 240;
			case EXPANDED -> 320;
		};
		int scaleExtra = Math.round(Math.max(0F, fontScale - 1F) * 48F);
		int intrinsic = Math.min(max, Math.max(0, (int) Math.ceil(titleWidthDp)));
		return Math.max(base + scaleExtra, intrinsic);
	}
}
