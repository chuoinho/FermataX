package me.aap.fermata.ui.policy;

/**
 * Immutable decoder format data used to calculate presentation geometry.
 *
 * <p>The coded frame can include decoder padding while the visible frame is the content that the
 * user should see. A valid visible frame therefore takes precedence for display aspect ratio, but
 * the coded frame remains available to size a decoder Surface without clipping that content.</p>
 */
public record VideoFormatSnapshot(float codedWidth, float codedHeight, float visibleWidth,
		float visibleHeight, float pixelWidthHeightRatio) {
	private static final VideoFormatSnapshot UNKNOWN = new VideoFormatSnapshot(0f, 0f, 0f, 0f, 1f);

	public static VideoFormatSnapshot unknown() {
		return UNKNOWN;
	}

	/**
	 * Uses a size reported by a video-size callback only while the engine has not published
	 * decoder geometry yet. Once the engine has a real snapshot, that richer source (which can
	 * include visible-frame and coded-frame differences) always wins.
	 */
	public VideoFormatSnapshot withFallback(VideoFormatSnapshot fallback) {
		return hasKnownGeometry() || !fallback.hasKnownGeometry() ? this : fallback;
	}

	public boolean hasValidCodedSize() {
		return hasValidSize(codedWidth, codedHeight);
	}

	public boolean hasValidVisibleSize() {
		return hasValidSize(visibleWidth, visibleHeight);
	}

	/** True once either the visible or coded decoder geometry is usable. */
	public boolean hasKnownGeometry() {
		return hasValidVisibleSize() || hasValidCodedSize();
	}

	public float displayWidth() {
		return hasValidVisibleSize() ? visibleWidth : codedWidth;
	}

	public float displayHeight() {
		return hasValidVisibleSize() ? visibleHeight : codedHeight;
	}

	public float normalizedPixelWidthHeightRatio() {
		return isFinite(pixelWidthHeightRatio) && (pixelWidthHeightRatio > 0f) ?
				pixelWidthHeightRatio : 1f;
	}

	private static boolean hasValidSize(float width, float height) {
		return isFinite(width) && isFinite(height) && (width > 0f) && (height > 0f);
	}

	private static boolean isFinite(float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}
}
