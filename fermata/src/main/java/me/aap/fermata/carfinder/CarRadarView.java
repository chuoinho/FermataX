package me.aap.fermata.carfinder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * Animated radar view displaying concentric scanning circles and an inertial-damped
 * needle pointing in the relative direction of the vehicle.
 * Styled with FermataX's signature dark navy & electric blue theme.
 */
public final class CarRadarView extends View {
	private static final int ACCENT_BLUE = 0xFF3B82F6;

	private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint innerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path arrowPath = new Path();

	private ValueAnimator pulseAnimator;
	private float pulseProgress;

	private float targetAngleDegrees = 0.0f;
	private float currentAngleDegrees = 0.0f;
	private boolean hasValidDirection = false;

	public CarRadarView(Context context) {
		super(context);
		initRadarPaints();
	}

	public CarRadarView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		initRadarPaints();
	}

	private void initRadarPaints() {
		float density = getResources().getDisplayMetrics().density;

		circlePaint.setStyle(Paint.Style.STROKE);
		circlePaint.setStrokeWidth(1.5f * density);
		circlePaint.setColor(0x333B82F6); // Deep translucent blue ring

		pulsePaint.setStyle(Paint.Style.STROKE);
		pulsePaint.setStrokeWidth(2f * density);

		innerRingPaint.setStyle(Paint.Style.FILL);
		innerRingPaint.setColor(0x153B82F6); // Soft blue disc

		arrowPaint.setStyle(Paint.Style.FILL);
		arrowPaint.setColor(ACCENT_BLUE); // Fermata electric blue
	}

	/**
	 * Updates the vehicle's relative bearing for the radar needle.
	 */
	public void updateRelativeBearing(float relativeBearingDegrees, boolean isBearingValid) {
		this.hasValidDirection = isBearingValid;
		this.targetAngleDegrees = AngleUtils.normalizeAngle(relativeBearingDegrees);
		invalidate();
	}

	/** Backwards-compatible alias */
	public void setRelativeDirection(float relativeBearingDegrees, boolean valid) {
		updateRelativeBearing(relativeBearingDegrees, valid);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		startPulseAnimation();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		stopPulseAnimation();
	}

	private void startPulseAnimation() {
		if (pulseAnimator != null) return;
		pulseAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
		pulseAnimator.setDuration(2200L);
		pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
		pulseAnimator.setInterpolator(new LinearInterpolator());
		pulseAnimator.addUpdateListener(anim -> {
			pulseProgress = (float) anim.getAnimatedValue();
			invalidate();
		});
		pulseAnimator.start();
	}

	private void stopPulseAnimation() {
		if (pulseAnimator != null) {
			pulseAnimator.cancel();
			pulseAnimator = null;
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float cx = getWidth() / 2f;
		float cy = getHeight() / 2f;
		float maxRadius = Math.min(cx, cy) * 0.90f;
		if (maxRadius <= 0f) return;

		drawConcentricReferenceRings(canvas, cx, cy, maxRadius);
		drawAnimatedPulseWaves(canvas, cx, cy, maxRadius);

		float innerR = maxRadius * 0.55f;
		canvas.drawCircle(cx, cy, innerR, innerRingPaint);

		if (hasValidDirection) {
			drawCompassNeedle(canvas, cx, cy, innerR);
		} else {
			drawStationaryCenterBeacon(canvas, cx, cy);
		}
	}

	private void drawConcentricReferenceRings(Canvas canvas, float cx, float cy, float maxRadius) {
		float[] ringRatios = {1.0f, 0.70f, 0.40f};
		for (float r : ringRatios) {
			canvas.drawCircle(cx, cy, maxRadius * r, circlePaint);
		}
	}

	private void drawAnimatedPulseWaves(Canvas canvas, float cx, float cy, float maxRadius) {
		renderSinglePulseRing(canvas, cx, cy, maxRadius, pulseProgress, 0.28f);
		renderSinglePulseRing(canvas, cx, cy, maxRadius, (pulseProgress + 0.5f) % 1.0f, 0.18f);
	}

	private void drawCompassNeedle(Canvas canvas, float cx, float cy, float innerR) {
		float diff = AngleUtils.normalizeAngle(targetAngleDegrees - currentAngleDegrees);
		if (Math.abs(diff) < 0.4f) {
			currentAngleDegrees = targetAngleDegrees;
		} else {
			currentAngleDegrees = AngleUtils.normalizeAngle(currentAngleDegrees + diff * 0.22f);
		}

		canvas.save();
		canvas.rotate(currentAngleDegrees, cx, cy);

		float arrowLen = innerR * 0.72f;
		float arrowWidth = arrowLen * 0.45f;
		arrowPath.reset();
		arrowPath.moveTo(cx, cy - arrowLen); // Tip
		arrowPath.lineTo(cx + arrowWidth, cy + arrowLen * 0.6f); // Right wing
		arrowPath.quadTo(cx + arrowWidth * 0.3f, cy + arrowLen * 0.3f, cx, cy + arrowLen * 0.15f); // Right notch
		arrowPath.quadTo(cx - arrowWidth * 0.3f, cy + arrowLen * 0.3f, cx - arrowWidth, cy + arrowLen * 0.6f); // Left notch
		arrowPath.close();

		canvas.drawPath(arrowPath, arrowPaint);
		canvas.restore();
	}

	private void drawStationaryCenterBeacon(Canvas canvas, float cx, float cy) {
		float density = getResources().getDisplayMetrics().density;
		canvas.drawCircle(cx, cy, 6f * density, arrowPaint);
	}

	private void renderSinglePulseRing(Canvas canvas, float cx, float cy, float maxRadius, float progress, float maxAlpha) {
		float radius = maxRadius * (0.35f + progress * 0.65f);
		int alpha = Math.round(Math.max(0f, 1.0f - progress) * maxAlpha * 255f);
		pulsePaint.setColor((alpha << 24) | 0x3B82F6);
		canvas.drawCircle(cx, cy, radius, pulsePaint);
	}
}
