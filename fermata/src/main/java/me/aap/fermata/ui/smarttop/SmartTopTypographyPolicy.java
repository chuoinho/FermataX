package me.aap.fermata.ui.smarttop;

/**
 * Stable type roles for SmartTop V2. The title always owns a two-line slot; font scaling is
 * bucketed and capped so accessibility growth never causes state-dependent reflow.
 */
public final class SmartTopTypographyPolicy {
	private SmartTopTypographyPolicy() {
	}

	public static Typography resolve(SmartTopLayoutMode mode) {
		return resolve(mode, 1F);
	}

	public static Typography resolve(SmartTopLayoutMode mode, float systemFontScale) {
		float scale = Math.max(1F, systemFontScale);
		float titleVisualScale = titleVisualScale(scale);
		float secondaryVisualScale = secondaryVisualScale(scale);
		float requestedTitleScale = titleVisualScale / scale;
		float requestedSecondaryScale = secondaryVisualScale / scale;

		return switch (mode) {
			case COMPACT -> new Typography(
					12F * requestedSecondaryScale,
					20F * requestedTitleScale,
					13F * requestedSecondaryScale,
					Math.round(18F * secondaryVisualScale),
					Math.round(50F * titleVisualScale),
					Math.round(20F * secondaryVisualScale), 2);
			case STANDARD -> new Typography(
					12F * requestedSecondaryScale,
					23F * requestedTitleScale,
					14F * requestedSecondaryScale,
					Math.round(18F * secondaryVisualScale),
					Math.round(56F * titleVisualScale),
					Math.round(21F * secondaryVisualScale), 2);
			case EXPANDED -> new Typography(
					13F * requestedSecondaryScale,
					25F * requestedTitleScale,
					15F * requestedSecondaryScale,
					Math.round(19F * secondaryVisualScale),
					Math.round(60F * titleVisualScale),
					Math.round(23F * secondaryVisualScale), 2);
		};
	}

	static int fontScaleBucket(float systemFontScale) {
		float scale = Math.max(1F, systemFontScale);
		if (scale <= 1.05F) return 0;
		if (scale <= 1.35F) return 1;
		if (scale <= 1.60F) return 2;
		return 3;
	}

	private static float titleVisualScale(float scale) {
		return switch (fontScaleBucket(scale)) {
			case 0 -> 1F;
			case 1 -> 1.15F;
			case 2 -> 1.25F;
			default -> 1.35F;
		};
	}

	private static float secondaryVisualScale(float scale) {
		return switch (fontScaleBucket(scale)) {
			case 0 -> 1F;
			case 1 -> 1.08F;
			case 2 -> 1.14F;
			default -> 1.20F;
		};
	}

	public record Typography(float eyebrowSp, float titleSp, float subtitleSp,
			int eyebrowMinHeightDp, int titleMinHeightDp, int subtitleMinHeightDp,
			int titleLines) {
		public Typography {
			titleLines = Math.max(1, titleLines);
		}
	}
}
