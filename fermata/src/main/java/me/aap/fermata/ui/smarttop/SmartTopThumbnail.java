package me.aap.fermata.ui.smarttop;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Objects;

/** A small foreground artwork source for SmartTop's fixed image slot. */
public record SmartTopThumbnail(String identity, @Nullable Bitmap bitmap, @Nullable Uri uri) {
	private static final int MIN_SHORT_EDGE_PX = 64;
	private static final int MAX_EDGE_PX = 1024;
	private static final long MAX_DECODED_BYTES = 4L * 1024L * 1024L;

	public SmartTopThumbnail {
		identity = Objects.requireNonNull(identity, "identity");
		if (identity.isBlank() || ((bitmap == null) == (uri == null))) {
			throw new IllegalArgumentException("Thumbnail requires one source and an identity");
		}
	}

	@Nullable
	public static SmartTopThumbnail fromBitmap(String identity, @Nullable Bitmap bitmap) {
		if (fromSource(identity, bitmap) == null) return null;
		return new SmartTopThumbnail(identity, normalize(bitmap), null);
	}

	/**
	 * Keeps a validated caller-owned source for the UI binder's background normalization pipeline.
	 * It must not be rendered until that pipeline supplies its bounded bitmap.
	 */
	@Nullable
	static SmartTopThumbnail fromSource(String identity, @Nullable Bitmap bitmap) {
		if ((bitmap == null) || bitmap.isRecycled() || (bitmap.getWidth() <= 0) ||
				(bitmap.getHeight() <= 0) ||
				(Math.min(bitmap.getWidth(), bitmap.getHeight()) < MIN_SHORT_EDGE_PX)) return null;
		return new SmartTopThumbnail(identity, bitmap, null);
	}

	@Nullable
	public static SmartTopThumbnail fromUri(String identity, @Nullable Uri uri) {
		return (uri == null) ? null : new SmartTopThumbnail(identity, null, uri);
	}

	private static Bitmap normalize(Bitmap bitmap) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		double scale = Math.min(1D, (double) MAX_EDGE_PX / Math.max(width, height));
		long bytes = (long) width * height * 4L;
		if (bytes > MAX_DECODED_BYTES) {
			scale = Math.min(scale, Math.sqrt((double) MAX_DECODED_BYTES / bytes));
		}
		if (scale >= 1D) return bitmap;
		int targetWidth = Math.max(1, (int) Math.floor(width * scale));
		int targetHeight = Math.max(1, (int) Math.floor(height * scale));
		return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
	}
}
