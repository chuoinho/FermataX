package me.aap.fermata.ui.smarttop;

import java.util.List;
import java.util.Objects;

/** Complete renderer geometry resolved from a measured environment and immutable SmartTop content. */
public record SmartTopLayoutSpec(
		SmartTopLayoutMode mode,
		int cardHeightDp,
		int cardPaddingDp,
		int artworkSizeDp,
		int artworkPaddingDp,
		SmartTopTypographyPolicy.Typography typography,
		int actionCellDp,
		int primaryGlyphDp,
		int secondaryGlyphDp,
		int actionGapDp,
		List<SmartTopAction> visibleActions,
		SmartTopTerminalActionStyle terminalActionStyle,
		int terminalActionWidthDp,
		int recentRows,
		int recentPanelWidthDp) {
	public SmartTopLayoutSpec {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(typography, "typography");
		visibleActions = List.copyOf(Objects.requireNonNull(visibleActions, "visibleActions"));
		Objects.requireNonNull(terminalActionStyle, "terminalActionStyle");
		cardHeightDp = Math.max(0, cardHeightDp);
		cardPaddingDp = Math.max(0, cardPaddingDp);
		artworkSizeDp = Math.max(0, artworkSizeDp);
		artworkPaddingDp = Math.max(0, artworkPaddingDp);
		actionCellDp = Math.max(0, actionCellDp);
		primaryGlyphDp = Math.max(0, primaryGlyphDp);
		secondaryGlyphDp = Math.max(0, secondaryGlyphDp);
		actionGapDp = Math.max(0, actionGapDp);
		terminalActionWidthDp = Math.max(0, terminalActionWidthDp);
		recentRows = Math.max(0, Math.min(SmartTopViewState.MAX_QUICK_RECENT, recentRows));
		recentPanelWidthDp = (recentRows == 0) ? 0 : Math.max(0, recentPanelWidthDp);
	}

	public boolean showQuickRecent() {
		return recentRows > 0;
	}
}
