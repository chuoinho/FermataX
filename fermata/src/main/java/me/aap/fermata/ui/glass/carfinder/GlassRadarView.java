package me.aap.fermata.ui.glass.carfinder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import me.aap.fermata.carfinder.AngleUtils;

/**
 * Cyberpunk Glass Radar view featuring Neon Cyan & Electric Blue scanning rings,
 * rotating sweep pulse waves, and an inertial-damped directional needle.
 */
public final class GlassRadarView extends View {
	public static final int ACCENT_CYAN = 0xFF00E5FF;
	public static final int ACCENT_BLUE = 0xFF2979FF;

	private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arrowGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint innerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path arrowPath = new Path();

	private ValueAnimator pulseAnimator;
	private float pulseProgress;

	private float targetAngleDegrees = 0.0f;
	private float currentAngleDegrees = 0.0f;
	private boolean hasValidDirection = false;

	public GlassRadarView(Context context) {
		super(context);
		initPaints();
	}

	public GlassRadarView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		initPaints();
	}

	private void initPaints() {
		float density = getResources().getDisplayMetrics().density;

		circlePaint.setStyle(Paint.Style.STROKE);
		circlePaint.setStrokeWidth(1.5f * density);
		circlePaint.setColor(0x3500E5FF); // Translucent Neon Cyan ring

		pulsePaint.setStyle(Paint.Style.STROKE);
		pulsePaint.setStrokeWidth(2f * density);

		innerRingPaint.setStyle(Paint.Style.FILL);
		innerRingPaint.setColor(0x1800E5FF); // Soft cyan glass disc

		crosshairPaint.setStyle(Paint.Style.STROKE);
		crosshairPaint.setStrokeWidth(1f * density);
		crosshairPaint.setColor(0x20FFFFFF); // Subtle glass crosshairs

		arrowPaint.setStyle(Paint.Style.FILL);
		arrowPaint.setColor(ACCENT_CYAN); // Glowing Neon Cyan needle

		arrowGlowPaint.setStyle(Paint.Style.FILL);
		arrowGlowPaint.setColor(0x4000E5FF); // Halo glow
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

		// 1. Crosshair lines (N-S, E-W)
		canvas.drawLine(cx, cy - maxRadius, cx, cy + maxRadius, crosshairPaint);
		canvas.drawLine(cx - maxRadius, cy, cx + maxRadius, cy, crosshairPaint);

		// 2. Concentric reference rings
		drawConcentricReferenceRings(canvas, cx, cy, maxRadius);

		// 3. Pulse waves
		drawAnimatedPulseWaves(canvas, cx, cy, maxRadius);

		// 4. Center disc
		float innerR = maxRadius * 0.55f;
		canvas.drawCircle(cx, cy, innerR, innerRingPaint);

		// 5. Compass needle or stationary beacon
		if (hasValidDirection) {
			drawCompassNeedle(canvas, cx, cy, innerR);
		} else {
			drawStationaryCenterBeacon(canvas, cx, cy);
		}
	}

	private void drawConcentricReferenceRings(Canvas canvas, float cx, float cy, float maxRadius) {
		float[] ringRatios = {1.0f, 0.70f, 0.40f};
		for (int i = 0; i < ringRatios.length; i++) {
			circlePaint.setColor(i == 0 ? 0x4000E5FF : 0x2500E5FF);
			canvas.drawCircle(cx, cy, maxRadius * ringRatios[i], circlePaint);
		}
	}

	private void drawAnimatedPulseWaves(Canvas canvas, float cx, float cy, float maxRadius) {
		renderSinglePulseRing(canvas, cx, cy, maxRadius, pulseProgress, 0.32f);
		renderSinglePulseRing(canvas, cx, cy, maxRadius, (pulseProgress + 0.5f) % 1.0f, 0.20f);
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

		float arrowLen = innerR * 0.76f;
		float arrowWidth = arrowLen * 0.46f;

		// Halo
		arrowPath.reset();
		arrowPath.moveTo(cx, cy - arrowLen - 4);
		arrowPath.lineTo(cx + arrowWidth + 4, cy + arrowLen * 0.6f + 3);
		arrowPath.quadTo(cx + arrowWidth * 0.3f, cy + arrowLen * 0.3f, cx, cy + arrowLen * 0.15f);
		arrowPath.quadTo(cx - arrowWidth * 0.3f, cy + arrowLen * 0.3f, cx - arrowWidth - 4, cy + arrowLen * 0.6f + 3);
		arrowPath.close();
		canvas.drawPath(arrowPath, arrowGlowPaint);

		// Arrow body
		arrowPath.reset();
		arrowPath.moveTo(cx, cy - arrowLen); // Tip
		arrowPath.lineTo(cx + arrowWidth, cy + arrowLen * 0.6f); // Right wing
		arrowPath.quadTo(cx + arrowWidth * 0.3f, cy + arrowLen * 0.3f, cx, cy + arrowLen * 0.15f); // Center notch
		arrowPath.quadTo(cx - arrowWidth * 0.3f, cy + arrowLen * 0.3f, cx - arrowWidth, cy + arrowLen * 0.6f); // Left wing
		arrowPath.close();

		canvas.drawPath(arrowPath, arrowPaint);
		canvas.restore();
	}

	private void drawStationaryCenterBeacon(Canvas canvas, float cx, float cy) {
		float density = getResources().getDisplayMetrics().density;
		// Pulsing beacon halo
		arrowGlowPaint.setAlpha(Math.round((0.5f + 0.5f * (float) Math.sin(pulseProgress * Math.PI * 2)) * 120f));
		canvas.drawCircle(cx, cy, 14f * density, arrowGlowPaint);
		// Center dot
		canvas.drawCircle(cx, cy, 6f * density, arrowPaint);
	}

	private void renderSinglePulseRing(Canvas canvas, float cx, float cy, float maxRadius, float progress, float maxAlpha) {
		float radius = maxRadius * (0.35f + progress * 0.65f);
		int alpha = Math.round(Math.max(0f, 1.0f - progress) * maxAlpha * 255f);
		pulsePaint.setColor((alpha << 24) | (ACCENT_CYAN & 0x00FFFFFF));
		canvas.drawCircle(cx, cy, radius, pulsePaint);
	}
}
