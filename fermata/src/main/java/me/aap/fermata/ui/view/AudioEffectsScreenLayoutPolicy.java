package me.aap.fermata.ui.view;

import me.aap.fermata.media.audio.AudioEffectsProfile;

/** Pure responsive sizing rules for the shared EQ screen. */
final class AudioEffectsScreenLayoutPolicy {
	enum GestureAxis { UNDECIDED, VERTICAL, HORIZONTAL }

	private static final int PHONE_BAND_WIDTH_DP = 56;
	private static final int AUTO_BAND_WIDTH_DP = 72;
	private static final int BAND_GAP_DP = 8;
	private static final int BAND_TRAILING_PADDING_DP = 8;

	private AudioEffectsScreenLayoutPolicy() {
	}

	static int bandWidthDp(boolean automotive) {
		return automotive ? AUTO_BAND_WIDTH_DP : PHONE_BAND_WIDTH_DP;
	}

	static int bandStripWidthDp(boolean automotive) {
		return (AudioEffectsProfile.CANONICAL_FREQ_HZ.length * bandWidthDp(automotive)) +
				((AudioEffectsProfile.CANONICAL_FREQ_HZ.length - 1) * BAND_GAP_DP) +
				BAND_TRAILING_PADDING_DP;
	}

	static boolean needsHorizontalScroll(int availableWidthDp, boolean automotive) {
		return bandStripWidthDp(automotive) > availableWidthDp;
	}

	static int contentHeightDp(int availableHeightDp, int actionHeightDp) {
		return Math.max(0, availableHeightDp - Math.max(64, actionHeightDp));
	}

	static boolean actionIsWithinBounds(int totalHeightDp, int actionTopDp, int actionHeightDp) {
		return actionTopDp >= 0 && actionHeightDp >= 64 &&
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
