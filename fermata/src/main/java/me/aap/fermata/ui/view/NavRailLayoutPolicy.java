package me.aap.fermata.ui.view;

/** Pure shared-rail geometry and gesture-threshold policy. */
final class NavRailLayoutPolicy {
	private static final float PROJECTION_VERTICAL_SLOP_MULTIPLIER = 1.8F;
	private static final float HORIZONTAL_SLOP_MULTIPLIER = 2.5F;
	private static final int STANDARD_WIDTH_DP = 96;
	private static final int WIDE_WIDTH_DP = 104;
	private static final int MAX_WIDTH_DP = 112;
	private static final int MOBILE_WIDTH_DP = 80;
	private static final int MOBILE_MAX_WIDTH_DP = 96;
	private static final int MOBILE_TARGET_DP = 64;
	private static final int STANDARD_TARGET_DP = 76;
	private static final int TALL_TARGET_DP = 80;
	private static final int VISUAL_INSET_DP = 10;
	private static final int ICON_EXTENT_DP = 36;
	private static final int SEPARATOR_EXTENT_DP = 8;
	private static final int TALL_RAIL_HEIGHT_DP = 600;

	private NavRailLayoutPolicy() {
	}

	enum GestureAxis {
		UNDECIDED,
		VERTICAL,
		HORIZONTAL
	}

	static int railWidthDp(boolean automotive, int screenWidthDp, float userScale) {
		if (!automotive) {
			int scaled = Math.round(44F * Math.max(0F, userScale));
			return Math.min(MOBILE_MAX_WIDTH_DP, Math.max(MOBILE_WIDTH_DP, scaled));
		}
		int floor = (screenWidthDp >= 1200) ? WIDE_WIDTH_DP : STANDARD_WIDTH_DP;
		int scaled = Math.round(52F * Math.max(0F, userScale));
		return Math.min(MAX_WIDTH_DP, Math.max(floor, scaled));
	}

	static int touchTargetExtentDp(boolean projection, int railHeightDp) {
		if (!projection) return MOBILE_TARGET_DP;
		return (railHeightDp >= TALL_RAIL_HEIGHT_DP) ? TALL_TARGET_DP : STANDARD_TARGET_DP;
	}

	static int visualTileExtentDp(int touchTargetExtentDp) {
		return Math.max(0, touchTargetExtentDp - (VISUAL_INSET_DP * 2));
	}

	static int iconExtentDp() {
		return ICON_EXTENT_DP;
	}

	static int separatorExtentDp() {
		return SEPARATOR_EXTENT_DP;
	}

	static int verticalGestureSlopPx(int platformSlopPx, boolean projection) {
		int slop = Math.max(1, platformSlopPx);
		return projection ? Math.round(slop * PROJECTION_VERTICAL_SLOP_MULTIPLIER) : slop;
	}

	static int horizontalGestureSlopPx(int platformSlopPx) {
		return Math.round(Math.max(1, platformSlopPx) * HORIZONTAL_SLOP_MULTIPLIER);
	}

	static GestureAxis resolveGestureAxis(float dx, float dy, int platformSlopPx,
			boolean projection) {
		dx = Math.abs(dx);
		dy = Math.abs(dy);
		if (dy >= dx) {
			return (dy > verticalGestureSlopPx(platformSlopPx, projection)) ?
					GestureAxis.VERTICAL : GestureAxis.UNDECIDED;
		}
		return (dx > horizontalGestureSlopPx(platformSlopPx)) ?
				GestureAxis.HORIZONTAL : GestureAxis.UNDECIDED;
	}
}
