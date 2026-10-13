package me.aap.fermata.ui.glass.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Checkable;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Pure Java Glassmorphic Switch widget replacing SwitchCompat.
 * Features animated gliding thumb, neon cyan active state, and hardware shadow layers.
 */
public class GlassSwitch extends View implements Checkable {

	public interface OnCheckedChangeListener {
		void onCheckedChanged(GlassSwitch view, boolean isChecked);
	}

	private static final float DEFAULT_WIDTH_DP = 48.0f;
	private static final float DEFAULT_HEIGHT_DP = 26.0f;
	private static final float THUMB_MARGIN_DP = 3.0f;

	private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint trackStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF trackRect = new RectF();

	private float density = 1.0f;
	private boolean checked = false;
	private float thumbPosition = 0.0f; // 0.0f = OFF, 1.0f = ON
	private ValueAnimator animator;

	@Nullable
	private OnCheckedChangeListener listener;

	public GlassSwitch(Context context) {
		super(context);
		init(context);
	}

	public GlassSwitch(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public GlassSwitch(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		density = context.getResources().getDisplayMetrics().density;
		setClickable(true);
		setFocusable(true);

		trackPaint.setStyle(Paint.Style.FILL);
		trackStrokePaint.setStyle(Paint.Style.STROKE);
		trackStrokePaint.setStrokeWidth(1.0f * density);

		thumbPaint.setStyle(Paint.Style.FILL);
		thumbPaint.setColor(0xFFFFFFFF);

		setOnClickListener(v -> toggle());
	}

	@Override
	public boolean isChecked() {
		return checked;
	}

	@Override
	public void setChecked(boolean checked) {
		setCheckedInternal(checked, true);
	}

	public void setChecked(boolean checked, boolean animate) {
		setCheckedInternal(checked, animate);
	}

	private void setCheckedInternal(boolean isChecked, boolean animate) {
		if (this.checked != isChecked) {
			this.checked = isChecked;
			if (listener != null) {
				listener.onCheckedChanged(this, this.checked);
			}

			if (animate && getWidth() > 0) {
				startAnimation(isChecked ? 1.0f : 0.0f);
			} else {
				thumbPosition = isChecked ? 1.0f : 0.0f;
				invalidate();
			}
		}
	}

	@Override
	public void toggle() {
		setChecked(!checked);
	}

	public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
		this.listener = listener;
	}

	private void startAnimation(float target) {
		if (animator != null && animator.isRunning()) {
			animator.cancel();
		}
		animator = ValueAnimator.ofFloat(thumbPosition, target);
		animator.setDuration(180L);
		animator.setInterpolator(new DecelerateInterpolator());
		animator.addUpdateListener(animation -> {
			thumbPosition = (float) animation.getAnimatedValue();
			invalidate();
		});
		animator.start();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int desiredWidth = Math.round(DEFAULT_WIDTH_DP * density);
		int desiredHeight = Math.round(DEFAULT_HEIGHT_DP * density);

		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int width = (widthMode == MeasureSpec.EXACTLY) ? widthSize : desiredWidth;
		int height = (heightMode == MeasureSpec.EXACTLY) ? heightSize : desiredHeight;

		setMeasuredDimension(width, height);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float width = getWidth();
		float height = getHeight();
		if (width <= 0.0f || height <= 0.0f) return;

		float cornerRadius = height / 2.0f;
		trackRect.set(0.0f, 0.0f, width, height);

		// 1. Render Track
		if (thumbPosition > 0.0f) {
			// Checked / active transition: Cyan glow
			trackPaint.setColor(GlassThemeColors.ACCENT_CYAN);
			trackPaint.setAlpha(Math.round(255 * thumbPosition * 0.9f));
			canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);
		}
		if (thumbPosition < 1.0f) {
			// Unchecked track: Translucent obsidian glass
			trackPaint.setColor(0x2AFFFFFF);
			trackPaint.setAlpha(Math.round(255 * (1.0f - thumbPosition) * 0.4f));
			canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);
		}

		// Track outline border
		trackStrokePaint.setColor(0x33FFFFFF);
		canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackStrokePaint);

		// 2. Render Thumb
		float margin = THUMB_MARGIN_DP * density;
		float thumbRadius = (height - margin * 2.0f) / 2.0f;
		float startX = margin + thumbRadius;
		float endX = width - margin - thumbRadius;
		float currentThumbX = startX + (endX - startX) * thumbPosition;
		float centerY = height / 2.0f;

		thumbPaint.setShadowLayer(3.0f * density, 0.0f, 1.0f * density, 0x55000000);
		canvas.drawCircle(currentThumbX, centerY, thumbRadius, thumbPaint);
		thumbPaint.clearShadowLayer();
	}
}
