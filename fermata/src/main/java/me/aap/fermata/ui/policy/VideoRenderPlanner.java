package me.aap.fermata.ui.policy;

/**
 * Computes video output geometry without reading a View, an engine, or host-specific state.
 *
 * <p>This is deliberately a facade over {@link VideoSurfaceLayoutPolicy} for now: phase one
 * preserves all current scale semantics while giving every engine a common, testable contract for
 * coded-versus-visible frames. Runtime callers are migrated to this planner in later phases.</p>
 */
public final class VideoRenderPlanner {
	private VideoRenderPlanner() {
	}

	public static VideoRenderPlan plan(VideoViewport viewport, VideoFormatSnapshot format,
			int scale) {
		int normalizedScale = VideoSurfaceLayoutPolicy.normalizeScale(scale);
		if (!viewport.isMeasured()) {
			return new VideoRenderPlan(viewport.width(), viewport.height(),
					VideoSurfaceLayoutPolicy.MATCH_PARENT, VideoSurfaceLayoutPolicy.MATCH_PARENT,
					VideoSurfaceLayoutPolicy.MATCH_PARENT, VideoSurfaceLayoutPolicy.MATCH_PARENT,
					normalizedScale, !format.hasKnownGeometry());
		}

		// A decoder can report its first frame before its format. Do not turn that short gap into
		// a guessed 16:9 Surface: it is the source of the historical split-view crop/black-frame
		// regression. Keep the complete viewport until a real display geometry arrives.
		if (!format.hasKnownGeometry()) {
			return new VideoRenderPlan(viewport.width(), viewport.height(), viewport.width(),
					viewport.height(), VideoSurfaceLayoutPolicy.MATCH_PARENT,
					VideoSurfaceLayoutPolicy.MATCH_PARENT, normalizedScale, true);
		}

		VideoSurfaceLayoutPolicy.Size content = VideoSurfaceLayoutPolicy.resolve(viewport.width(),
				viewport.height(), format.displayWidth(), format.displayHeight(), normalizedScale,
				format.normalizedPixelWidthHeightRatio());

		int surfaceWidth = content.width();
		int surfaceHeight = content.height();
		if (format.hasValidCodedSize() && format.hasValidVisibleSize()) {
			surfaceWidth = Math.max(content.width(), scaledDimension(content.width(),
					format.codedWidth(), format.visibleWidth()));
			surfaceHeight = Math.max(content.height(), scaledDimension(content.height(),
					format.codedHeight(), format.visibleHeight()));
		}
		return new VideoRenderPlan(viewport.width(), viewport.height(), even(content.width()),
				even(content.height()), even(surfaceWidth), even(surfaceHeight), normalizedScale,
				!format.hasKnownGeometry());
	}

	private static int scaledDimension(int contentDimension, float codedDimension,
			float visibleDimension) {
		float scaled = contentDimension * codedDimension / visibleDimension;
		if (!Float.isFinite(scaled) || (scaled <= 0f)) return 1;
		return (scaled >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : Math.max(1, (int) Math.ceil(scaled));
	}

	private static int even(int dimension) {
		if (dimension <= 1) return 2;
		return dimension & ~1;
	}
}
