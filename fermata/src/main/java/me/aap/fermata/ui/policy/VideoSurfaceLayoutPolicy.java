package me.aap.fermata.ui.policy;

import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;

/** Pure sizing policy for the video surface. It never stretches a valid video frame. */
public final class VideoSurfaceLayoutPolicy {
	public static final int MATCH_PARENT = -1;

	private VideoSurfaceLayoutPolicy() {
	}

	public static Size resolve(int screenWidth, int screenHeight, float videoWidth,
			float videoHeight, int scale, float pixelWidthHeightRatio) {
		if ((screenWidth <= 0) || (screenHeight <= 0) || !finite(videoWidth) ||
				!finite(videoHeight) || (videoWidth <= 0f) || (videoHeight <= 0f)) {
			return new Size(MATCH_PARENT, MATCH_PARENT);
		}

		float pixelRatio = finite(pixelWidthHeightRatio) && (pixelWidthHeightRatio > 0f) ?
				pixelWidthHeightRatio : 1f;
		float videoRatio = videoWidth * pixelRatio / videoHeight;
		if (!finite(videoRatio) || (videoRatio <= 0f)) {
			return new Size(MATCH_PARENT, MATCH_PARENT);
		}

		if (scale == SCALE_4_3) videoRatio = 4f / 3f;
		else if (scale == SCALE_16_9) videoRatio = 16f / 9f;
		else if (scale == SCALE_ORIGINAL) {
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

	private static boolean finite(float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}

	private static int positiveRound(float value) {
		return Math.max(1, Math.round(value));
	}

	public record Size(int width, int height) {
	}
}
