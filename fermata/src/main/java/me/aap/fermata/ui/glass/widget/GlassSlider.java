package me.aap.fermata.ui.glass.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * High-performance, touch-optimized Glass Slider ported and modernized from CarStream
 * (com.carstream.tech.launcher.Slider).
 * Built with pure Java Canvas rendering, hardware-accelerated glowing thumb, and gradient track.
 */
public class GlassSlider extends View {

	public interface OnSliderChangeListener {
		void onProgressChanged(GlassSlider slider, float progress, boolean fromUser);
		void onStartTrackingTouch(GlassSlider slider);
		void onStopTrackingTouch(GlassSlider slider);
	}

	private static final float THUMB_RADIUS_DP = 10.0f;
	private static final float TRACK_HEIGHT_DP = 5.0f;

	private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF trackRect = new RectF();
	private final RectF progressRect = new RectF();

	private float density = 1.0f;
	private float progress = 0.0f;
	private float min = 0.0f;
	private boolean isDragging = false;
	private boolean thumbGlow = true;

	@ColorInt
	private int trackColor = 0x2AFFFFFF;
	@ColorInt
	private int progressStartColor = GlassThemeColors.ACCENT_CYAN;
	@ColorInt
	private int progressEndColor = GlassThemeColors.ACCENT_BLUE;

	@Nullable
	private OnSliderChangeListener listener;

	public GlassSlider(Context context) {
		super(context);
		init(context);
	}

	public GlassSlider(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public GlassSlider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		density = context.getResources().getDisplayMetrics().density;
		setClickable(true);
		setFocusable(true);

		boolean isNight = GlassThemeColors.isNight(context);
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);

		trackColor = isNight ? 0x2AFFFFFF : 0x2A000000;
		progressStartColor = colors.getActionText();
		progressEndColor = isNight ? GlassThemeColors.ACCENT_BLUE : colors.getActionText();

		trackPaint.setStyle(Paint.Style.FILL);
		progressPaint.setStyle(Paint.Style.FILL);

		thumbPaint.setStyle(Paint.Style.FILL);
		thumbPaint.setColor(0xFFFFFFFF);

		thumbStrokePaint.setStyle(Paint.Style.STROKE);
		thumbStrokePaint.setStrokeWidth(1.5f * density);
		thumbStrokePaint.setColor(colors.getActionText());
	}

	public float getProgress() {
		return progress;
	}

	public void setProgress(float ratio) {
		setProgressInternal(ratio, false);
	}

	private void setProgressInternal(float ratio, boolean fromUser) {
		float clamped = Math.max(min, Math.min(1.0f, ratio));
		if (this.progress != clamped) {
			this.progress = clamped;
			if (listener != null) {
				listener.onProgressChanged(this, this.progress, fromUser);
			}
			invalidate();
		}
	}

	public void setMin(float min) {
		this.min = Math.max(0.0f, Math.min(1.0f, min));
		if (this.progress < this.min) {
			setProgress(this.min);
		}
	}

	public void setOnSliderChangeListener(@Nullable OnSliderChangeListener listener) {
		this.listener = listener;
	}

	public void setTrackColor(@ColorInt int color) {
		this.trackColor = color;
		invalidate();
	}

	public void setProgressColors(@ColorInt int startColor, @ColorInt int endColor) {
		this.progressStartColor = startColor;
		this.progressEndColor = endColor;
		invalidate();
	}

	public void setThumbGlow(boolean enable) {
		this.thumbGlow = enable;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float width = getWidth();
		float height = getHeight();
		if (width <= 0.0f || height <= 0.0f) return;

		float thumbRadius = THUMB_RADIUS_DP * density;
		float trackHalfH = (TRACK_HEIGHT_DP * density) / 2.0f;
		float centerY = height / 2.0f;
		float startX = thumbRadius;
		float endX = width - thumbRadius;

		if (endX <= startX) return;

		// 1. Draw background track
		trackPaint.setColor(trackColor);
		trackRect.set(startX, centerY - trackHalfH, endX, centerY + trackHalfH);
		canvas.drawRoundRect(trackRect, trackHalfH, trackHalfH, trackPaint);

		// 2. Draw progress track
		float currentX = startX + (endX - startX) * progress;
		if (currentX > startX) {
			progressRect.set(startX, centerY - trackHalfH, currentX, centerY + trackHalfH);
			LinearGradient gradient = new LinearGradient(startX, centerY, currentX, centerY,
					progressStartColor, progressEndColor, Shader.TileMode.CLAMP);
			progressPaint.setShader(gradient);
			canvas.drawRoundRect(progressRect, trackHalfH, trackHalfH, progressPaint);
		}

		// 3. Draw thumb
		if (thumbGlow) {
			int glow = (progressStartColor & 0x00FFFFFF) | 0x80000000;
			thumbPaint.setShadowLayer(4.0f * density, 0.0f, 1.0f * density, glow);
		} else {
			thumbPaint.clearShadowLayer();
		}
		canvas.drawCircle(currentX, centerY, thumbRadius, thumbPaint);
		thumbPaint.clearShadowLayer();

		canvas.drawCircle(currentX, centerY, thumbRadius, thumbStrokePaint);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled()) return false;

		int action = event.getActionMasked();
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				isDragging = true;
				if (getParent() != null) {
					getParent().requestDisallowInterceptTouchEvent(true);
				}
				if (listener != null) {
					listener.onStartTrackingTouch(this);
				}
				handleTouchMove(event.getX());
				return true;

			case MotionEvent.ACTION_MOVE:
				if (isDragging) {
					handleTouchMove(event.getX());
					return true;
				}
				break;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (isDragging) {
					isDragging = false;
					handleTouchMove(event.getX());
					if (listener != null) {
						listener.onStopTrackingTouch(this);
					}
					return true;
				}
				break;
		}
		return super.onTouchEvent(event);
	}

	private void handleTouchMove(float touchX) {
		float thumbRadius = THUMB_RADIUS_DP * density;
		float startX = thumbRadius;
		float endX = getWidth() - thumbRadius;
		if (endX > startX) {
			float newProgress = (touchX - startX) / (endX - startX);
			setProgressInternal(newProgress, true);
		}
	}
}
