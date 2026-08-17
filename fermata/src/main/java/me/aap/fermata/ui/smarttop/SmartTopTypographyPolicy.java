package me.aap.fermata.ui.smarttop;

/** Stable type roles for SmartTop V2. Text remains SP-scaled while row minimums prevent state/focus reflow. */
public final class SmartTopTypographyPolicy {
	private SmartTopTypographyPolicy() {
	}

	public static Typography resolve(SmartTopLayoutMode mode) {
		return switch (mode) {
			case COMPACT -> new Typography(12F, 20F, 13F, 18, 28, 20);
			case STANDARD -> new Typography(12F, 23F, 14F, 18, 31, 21);
			case EXPANDED -> new Typography(13F, 25F, 15F, 19, 34, 23);
		};
	}

	public record Typography(float eyebrowSp, float titleSp, float subtitleSp,
			int eyebrowMinHeightDp, int titleMinHeightDp, int subtitleMinHeightDp) {
	}
}
