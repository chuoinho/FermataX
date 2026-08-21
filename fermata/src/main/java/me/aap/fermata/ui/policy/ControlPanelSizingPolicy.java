package me.aap.fermata.ui.policy;

/** Pure geometry for transport controls. Keeps hit cells layout-owned while sizing visuals to the
 * actual measured cell instead of fixed dp glyph assumptions. */
public final class ControlPanelSizingPolicy {
	private static final float GLYPH_RATIO = 0.52F;

	private ControlPanelSizingPolicy() {
	}

	public static Geometry resolve(int width, int height) {
		int w = Math.max(0, width);
		int h = Math.max(0, height);
		int visual = Math.min(w, h);
		int glyph = (visual == 0) ? 0 : Math.max(1, Math.round(visual * GLYPH_RATIO));
		int horizontalPadding = Math.max(0, (w - glyph) / 2);
		int verticalPadding = Math.max(0, (h - glyph) / 2);
		int backgroundInsetX = Math.max(0, (w - visual) / 2);
		int backgroundInsetY = Math.max(0, (h - visual) / 2);
		return new Geometry(visual, glyph, horizontalPadding, verticalPadding,
				backgroundInsetX, backgroundInsetY);
	}

	/**
	 * Returns the measured horizontal correction needed to put a child exactly on its parent's
	 * physical centre. ConstraintLayout weighted chains can land half/a few pixels off-centre when
	 * the available width is not evenly divisible by all transport cells.
	 */
	public static float centerOffset(int parentWidth, int childLeft, int childRight) {
		return (parentWidth - childLeft - childRight) / 2F;
	}

	public record Geometry(int visualSize, int glyphSize, int horizontalPadding,
			int verticalPadding, int backgroundInsetX, int backgroundInsetY) {
	}
}
