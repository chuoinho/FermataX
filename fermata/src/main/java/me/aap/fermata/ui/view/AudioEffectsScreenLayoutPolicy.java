package me.aap.fermata.ui.view;

import me.aap.fermata.media.audio.AudioEffectsProfile;

/** Pure responsive sizing rules for the shared EQ screen. */
final class AudioEffectsScreenLayoutPolicy {
	enum GestureAxis { UNDECIDED, VERTICAL, HORIZONTAL }

	private static final int BAND_GAP_DP = 2;
	static final int EQ_SCALE_WIDTH_DP = 28;

	private AudioEffectsScreenLayoutPolicy() {
	}

	static int bandWidthDp(int availableWidthDp, int bandCount) {
		if ((bandCount <= 0) || (bandCount > AudioEffectsProfile.CANONICAL_FREQ_HZ.length)) {
			throw new IllegalArgumentException("Unexpected EQ band count");
		}
		int gaps = (bandCount - 1) * BAND_GAP_DP;
		return Math.max(1, (Math.max(0, availableWidthDp) - gaps) / bandCount);
	}

	static int bandGapDp() {
		return BAND_GAP_DP;
	}

	static int contentHeightDp(int availableHeightDp, int actionHeightDp) {
		return Math.max(0, availableHeightDp - Math.max(56, actionHeightDp));
	}

	static boolean actionIsWithinBounds(int totalHeightDp, int actionTopDp, int actionHeightDp) {
		return actionTopDp >= 0 && actionHeightDp >= 56 &&
				actionTopDp + actionHeightDp <= totalHeightDp;
	}

	static GestureAxis resolveBandGestureAxis(float dx, float dy, int touchSlop) {
		dx = Math.abs(dx);
		dy = Math.abs(dy);
		int slop = Math.max(1, touchSlop);
		if (Math.max(dx, dy) <= slop) return GestureAxis.UNDECIDED;
		return (dy >= dx) ? GestureAxis.VERTICAL : GestureAxis.HORIZONTAL;
	}

}
