package me.aap.fermata.ui.policy;

import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;

/** Pure sizing policy for the video surface. It never stretches a valid video frame. */
public final class VideoSurfaceLayoutPolicy {
	public static final int MATCH_PARENT = -1;
	private static final float UNKNOWN_VIDEO_RATIO = 16f / 9f;

	private VideoSurfaceLayoutPolicy() {
	}

	public static Size resolve(int screenWidth, int screenHeight, float videoWidth,
			float videoHeight, int scale, float pixelWidthHeightRatio) {
		if ((screenWidth <= 0) || (screenHeight <= 0)) {
			return new Size(MATCH_PARENT, MATCH_PARENT);
		}

		scale = normalizeScale(scale);
		boolean validVideoSize = hasValidVideoSize(videoWidth, videoHeight);
		float pixelRatio = finite(pixelWidthHeightRatio) && (pixelWidthHeightRatio > 0f) ?
				pixelWidthHeightRatio : 1f;
		float videoRatio = validVideoSize ? videoWidth * pixelRatio / videoHeight :
				UNKNOWN_VIDEO_RATIO;
		if (!finite(videoRatio) || (videoRatio <= 0f)) videoRatio = UNKNOWN_VIDEO_RATIO;

		if (scale == SCALE_4_3) videoRatio = 4f / 3f;
		else if (scale == SCALE_16_9) videoRatio = 16f / 9f;
		else if ((scale == SCALE_ORIGINAL) && validVideoSize) {
			return new Size(positiveRound(videoWidth * pixelRatio), positiveRound(videoHeight));
		}

		float width;
		float height;
		if (scale == SCALE_FILL) {
			if (videoRatio > 1f) {
				width = screenWidth;
				height = screenWidth / videoRatio;
			} else {
				width = screenHeight * videoRatio;
				height = screenHeight;
			}
		} else {
			float screenRatio = (float) screenWidth / screenHeight;
			if (videoRatio > screenRatio) {
				width = screenWidth;
				height = screenWidth / videoRatio;
			} else {
				width = screenHeight * videoRatio;
				height = screenHeight;
			}
		}
		return new Size(positiveRound(width), positiveRound(height));
	}

	/**
	 * Fits the complete video inside an automotive viewport. Some live streams expose an
	 * already display-corrected widescreen size together with a legacy anamorphic pixel ratio.
	 * Applying both produces an artificial ultra-wide surface, so discard only that duplicate
	 * correction while retaining genuine anamorphic correction for raw 4:3-ish dimensions.
	 */
	public static Size resolveAutomotiveContain(int screenWidth, int screenHeight,
			float videoWidth, float videoHeight, float pixelWidthHeightRatio) {
		float pixelRatio = normalizeAutomotivePixelRatio(
				videoWidth, videoHeight, pixelWidthHeightRatio);
		return resolve(screenWidth, screenHeight, videoWidth, videoHeight,
				SCALE_BEST, pixelRatio);
	}

	static float normalizeAutomotivePixelRatio(float videoWidth, float videoHeight,
			float pixelWidthHeightRatio) {
		if (!hasValidVideoSize(videoWidth, videoHeight) || !finite(pixelWidthHeightRatio) ||
				(pixelWidthHeightRatio <= 0f)) return 1f;
		float rawRatio = videoWidth / videoHeight;
		float correctedRatio = rawRatio * pixelWidthHeightRatio;
		return ((rawRatio >= 1.6f) && (rawRatio <= 1.9f) && (correctedRatio > 2.1f)) ?
				1f : pixelWidthHeightRatio;
	}

	public static boolean hasValidVideoSize(float width, float height) {
		return finite(width) && finite(height) && (width > 0f) && (height > 0f);
	}

	static int normalizeScale(int scale) {
		return switch (scale) {
			case SCALE_BEST, SCALE_FILL, SCALE_ORIGINAL, SCALE_4_3, SCALE_16_9 -> scale;
			default -> SCALE_BEST;
		};
	}

	private static boolean finite(float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}

	private static int positiveRound(float value) {
		return Math.max(1, Math.round(value));
	}

	public record Size(int width, int height) {
	}
}
