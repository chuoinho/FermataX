package me.aap.fermata.ui.policy;

/**
 * A side-effect-free request for one video output target.
 *
 * <p>{@code contentWidth/contentHeight} describe the visible frame after the selected scale
 * policy. {@code surfaceWidth/surfaceHeight} additionally retain any coded-frame padding so a
 * decoder Surface can show the complete visible frame. They may also contain an Android layout
 * sentinel such as {@code MATCH_PARENT}; engine APIs must not consume them unless
 * {@link #hasFinalSurfaceSize()} is {@code true}. A provisional plan is valid for layout but must
 * not be treated as final decoder metadata.</p>
 */
public record VideoRenderPlan(int viewportWidth, int viewportHeight, int contentWidth,
		int contentHeight, int surfaceWidth, int surfaceHeight, int scale, boolean provisional) {
	public boolean isDeferred() {
		return (viewportWidth <= 0) || (viewportHeight <= 0);
	}

	/** Returns true only when the Surface dimensions are real positive pixel sizes. */
	public boolean hasFinalSurfaceSize() {
		return (surfaceWidth > 0) && (surfaceHeight > 0);
	}
}
