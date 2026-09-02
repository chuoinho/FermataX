package me.aap.fermata.ui.smarttop;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

/** Draws only the SmartTop root background; it owns no layout, input or accessibility surface. */
final class SmartTopCardBackgroundDrawable extends Drawable {
	private static final int ARTWORK_SOFT_EDGE_PX = 48;
	private static final int ARTWORK_DIM_COLOR = 0x70000000;
	private static final int ARTWORK_SCRIM_START = 0xCC070B11;
	private static final int ARTWORK_SCRIM_END = 0xF0070B11;
	private static final float[] SPECTRUM =
			{0.20F, 0.46F, 0.74F, 0.38F, 0.88F, 0.58F, 0.30F, 0.68F, 0.42F};
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private final Rect source = new Rect();
	private final RectF target = new RectF();
	private final RectF border = new RectF();
	private final Path clipPath = new Path();
	private final float radiusPx;
	private final float borderWidthPx;
	private final SmartTopBackground.Kind kind;
	@Nullable
	private Bitmap artwork;
	@Nullable
	private Bitmap softenedArtwork;

	SmartTopCardBackgroundDrawable(float density, SmartTopBackground.Kind kind) {
		radiusPx = 14F * density;
		borderWidthPx = Math.max(1F, density);
		this.kind = kind;
	}

	void setArtwork(@Nullable Bitmap bitmap) {
		artwork = bitmap;
		softenedArtwork = soften(bitmap);
		invalidateSelf();
	}

	@Override
	public void draw(Canvas canvas) {
		Rect bounds = getBounds();
		if (bounds.isEmpty()) return;
		SmartTopBackground.Kind renderedKind =
				((kind == SmartTopBackground.Kind.ARTWORK) && !usable(artwork)) ?
						SmartTopBackground.Kind.SOURCE_FALLBACK : kind;
		int save = canvas.save();
		target.set(bounds);
		clipPath.rewind();
		clipPath.addRoundRect(target, radiusPx, radiusPx, Path.Direction.CW);
		canvas.clipPath(clipPath);

		drawBase(canvas, bounds, renderedKind);
		if ((renderedKind == SmartTopBackground.Kind.ARTWORK) && (artwork != null)) {
			drawCenterCrop(canvas, bounds,
					(softenedArtwork != null) ? softenedArtwork : artwork);
			drawArtworkDim(canvas);
		} else if (renderedKind == SmartTopBackground.Kind.AUDIO_SPECTRUM) {
			drawSpectrum(canvas, bounds);
		} else if (renderedKind == SmartTopBackground.Kind.EMPTY) {
			drawEmptyPattern(canvas, bounds);
		}

		drawReadabilityScrim(canvas, bounds, renderedKind);
		paint.setShader(null);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(borderWidthPx);
		paint.setColor(0x857AA7FF);
		float inset = borderWidthPx * 0.5F;
		border.set(bounds.left + inset, bounds.top + inset,
				bounds.right - inset, bounds.bottom - inset);
		canvas.drawRoundRect(border, Math.max(0F, radiusPx - inset),
				Math.max(0F, radiusPx - inset), paint);
		paint.setStyle(Paint.Style.FILL);
		canvas.restoreToCount(save);
	}

	private void drawBase(Canvas canvas, Rect bounds, SmartTopBackground.Kind renderedKind) {
		int start = (renderedKind == SmartTopBackground.Kind.EMPTY) ?
				Color.rgb(18, 25, 35) : Color.rgb(20, 31, 45);
		int end = Color.rgb(8, 11, 17);
		paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
				start, end, Shader.TileMode.CLAMP));
		canvas.drawRect(target, paint);
		paint.setShader(null);

		if (renderedKind == SmartTopBackground.Kind.SOURCE_FALLBACK) {
			paint.setShader(new LinearGradient(bounds.left, bounds.bottom, bounds.right, bounds.top,
					0x2A7AA7FF, 0x007AA7FF, Shader.TileMode.CLAMP));
			canvas.drawRect(target, paint);
			paint.setShader(null);
		}
	}

	private void drawReadabilityScrim(Canvas canvas, Rect bounds,
			SmartTopBackground.Kind renderedKind) {
		// Artwork stays atmospheric: uniformly softer/darker, with extra protection for Recent.
		int start = (renderedKind == SmartTopBackground.Kind.ARTWORK) ?
				ARTWORK_SCRIM_START : 0x42070B11;
		int end = (renderedKind == SmartTopBackground.Kind.ARTWORK) ?
				ARTWORK_SCRIM_END : 0xE6070B11;
		paint.setShader(new LinearGradient(bounds.left, 0F, bounds.right, 0F,
				start, end, Shader.TileMode.CLAMP));
		canvas.drawRect(target, paint);
	}

	private void drawArtworkDim(Canvas canvas) {
		paint.setShader(null);
		paint.setColor(ARTWORK_DIM_COLOR);
		canvas.drawRect(target, paint);
	}

	private void drawCenterCrop(Canvas canvas, Rect bounds, Bitmap bitmap) {
		float scale = Math.max((float) bounds.width() / bitmap.getWidth(),
				(float) bounds.height() / bitmap.getHeight());
		int srcWidth = Math.max(1, Math.round(bounds.width() / scale));
		int srcHeight = Math.max(1, Math.round(bounds.height() / scale));
		int left = Math.max(0, (bitmap.getWidth() - srcWidth) / 2);
		int top = Math.max(0, (bitmap.getHeight() - srcHeight) / 2);
		source.set(left, top, Math.min(bitmap.getWidth(), left + srcWidth),
				Math.min(bitmap.getHeight(), top + srcHeight));
		canvas.drawBitmap(bitmap, source, bounds, paint);
	}

	@Nullable
	private static Bitmap soften(@Nullable Bitmap bitmap) {
		if (!usable(bitmap)) return null;
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int longest = Math.max(width, height);
		if (longest <= ARTWORK_SOFT_EDGE_PX) return bitmap;
		float scale = (float) ARTWORK_SOFT_EDGE_PX / longest;
		return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)),
				Math.max(1, Math.round(height * scale)), true);
	}

	private void drawSpectrum(Canvas canvas, Rect bounds) {
		float areaWidth = bounds.width() * 0.52F;
		float gap = areaWidth / (SPECTRUM.length * 2F + 1F);
		float barWidth = gap;
		float bottom = bounds.bottom - bounds.height() * 0.12F;
		float maxHeight = bounds.height() * 0.62F;
		paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.left + areaWidth,
				bounds.bottom, 0x927AA7FF, 0x1F6AE6C8, Shader.TileMode.CLAMP));
		for (int i = 0; i < SPECTRUM.length; i++) {
			float left = bounds.left + gap + i * gap * 2F;
			float top = bottom - maxHeight * SPECTRUM[i];
			canvas.drawRoundRect(left, top, left + barWidth, bottom,
					barWidth * 0.5F, barWidth * 0.5F, paint);
		}
		paint.setShader(null);
	}

	private void drawEmptyPattern(Canvas canvas, Rect bounds) {
		float step = Math.max(24F, bounds.height() * 0.24F);
		paint.setColor(0x127AA7FF);
		paint.setStrokeWidth(Math.max(1F, borderWidthPx * 0.75F));
		for (float x = bounds.left - bounds.height(); x < bounds.right; x += step) {
			canvas.drawLine(x, bounds.bottom, x + bounds.height(), bounds.top, paint);
		}
	}

	private static boolean usable(@Nullable Bitmap bitmap) {
		return (bitmap != null) && !bitmap.isRecycled() &&
				(bitmap.getWidth() > 0) && (bitmap.getHeight() > 0);
	}

	@Override
	public void setAlpha(int alpha) {
		// This background is always opaque; RippleDrawable controls interaction feedback separately.
	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter) {
		paint.setColorFilter(colorFilter);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.OPAQUE;
	}
}
