package me.aap.fermata.ui.view;

import static android.view.View.MeasureSpec.getMode;
import static android.view.View.MeasureSpec.getSize;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.View.MeasureSpec.EXACTLY;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;

import me.aap.fermata.R;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.pref.PreferenceStore;

/** Read-only view of the persisted ten-band profile. */
public final class EqualizerCurveView extends View implements PreferenceStore.Listener {
	private static final float HORIZONTAL_PADDING_DP = 12f;
	private static final float VERTICAL_PADDING_DP = 10f;
	private static final float HEIGHT_DP = 88f;
	private final PreferenceStore store;
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final float density;
	private final int accentColor;
	private final int neutralColor;

	public EqualizerCurveView(Context context, PreferenceStore store) {
		super(context);
		this.store = store;
		density = context.getResources().getDisplayMetrics().density;
		accentColor = resolveColor(context, android.R.attr.colorAccent, 0xff4caf50);
		neutralColor = resolveColor(context, android.R.attr.textColorSecondary, 0xff808080);
		setContentDescription(context.getString(R.string.equalizer));
		setMinimumHeight(dp(HEIGHT_DP));
	}

	public boolean isGlassTheme() {
		try {
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			return a != null && a.getPrefs().isGlassTheme(a.isCarActivity());
		} catch (Throwable e) {
			return false;
		}
	}

	public boolean isGlassNight() {
		try {
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			return a != null ? a.getPrefs().isGlassNightMode() : true;
		} catch (Throwable e) {
			return true;
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		store.addBroadcastListener(this);
	}

	@Override
	protected void onDetachedFromWindow() {
		store.removeBroadcastListener(this);
		super.onDetachedFromWindow();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, java.util.List<PreferenceStore.Pref<?>> changed) {
		if (AudioEffectsProfileRepository.containsProfilePreference(changed)) invalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int height = dp(HEIGHT_DP);
		if (getMode(heightMeasureSpec) == EXACTLY) height = getSize(heightMeasureSpec);
		super.onMeasure(widthMeasureSpec, makeMeasureSpec(height, EXACTLY));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float left = getPaddingLeft() + dp(HORIZONTAL_PADDING_DP);
		float right = getWidth() - getPaddingRight() - dp(HORIZONTAL_PADDING_DP);
		float top = getPaddingTop() + dp(VERTICAL_PADDING_DP);
		float bottom = getHeight() - getPaddingBottom() - dp(VERTICAL_PADDING_DP);
		float center = (top + bottom) / 2f;
		boolean active = store.getBooleanPref(AudioEffectsProfileRepository.ENABLED);

		if (isGlassTheme()) {
			boolean isNight = isGlassNight();
			int cyan = isNight ? 0xFF00E5FF : 0xFF007A87;
			int white = isNight ? 0xFFFFFFFF : 0xFF15181A;
			int gridColor = isNight ? 0x33FFFFFF : 0x26000000;
			int glowColor = isNight ? 0x3000E5FF : 0x20007A87;
			int inactiveLine = isNight ? 0x6000E5FF : 0x60007A87;

			paint.setStrokeWidth(dp(1f));
			paint.setColor(gridColor);
			paint.setAlpha(active ? 180 : 80);
			canvas.drawLine(left, center, right, center, paint);

			Path path = new Path();
			Path fillPath = new Path();
			fillPath.moveTo(left, bottom);

			float[] xs = new float[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
			float[] ys = new float[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
			for (int i = 0; i < AudioEffectsProfile.CANONICAL_FREQ_HZ.length; i++) {
				xs[i] = left + (right - left) * EqualizerCurveGeometry.frequencyPosition(
						AudioEffectsProfile.CANONICAL_FREQ_HZ[i]);
				ys[i] = gainY(store.getIntPref(AudioEffectsProfileRepository.CANONICAL_CURVE_DB[i]), top, bottom);
				if (i == 0) {
					path.moveTo(xs[0], ys[0]);
					fillPath.lineTo(xs[0], ys[0]);
				} else {
					path.lineTo(xs[i], ys[i]);
					fillPath.lineTo(xs[i], ys[i]);
				}
			}
			fillPath.lineTo(right, bottom);
			fillPath.close();

			if (active) {
				paint.setStyle(Paint.Style.FILL);
				int gradStart = isNight ? 0x4500E5FF : 0x30007A87;
				int gradEnd = isNight ? 0x0500E5FF : 0x05007A87;
				paint.setShader(new LinearGradient(0, top, 0, bottom,
						gradStart, gradEnd, Shader.TileMode.CLAMP));
				canvas.drawPath(fillPath, paint);
				paint.setShader(null);
			}

			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeCap(Paint.Cap.ROUND);
			paint.setStrokeJoin(Paint.Join.ROUND);

			if (active) {
				paint.setStrokeWidth(dp(5f));
				paint.setColor(glowColor);
				canvas.drawPath(path, paint);
			}

			paint.setStrokeWidth(dp(2.5f));
			paint.setColor(active ? cyan : inactiveLine);
			paint.setAlpha(active ? 255 : 96);
			canvas.drawPath(path, paint);

			paint.setStyle(Paint.Style.FILL);
			for (int i = 0; i < xs.length; i++) {
				if (active) {
					paint.setColor(glowColor);
					canvas.drawCircle(xs[i], ys[i], dp(5f), paint);
					paint.setColor(white);
					canvas.drawCircle(xs[i], ys[i], dp(2.5f), paint);
				} else {
					paint.setColor(0x60808080);
					canvas.drawCircle(xs[i], ys[i], dp(2.5f), paint);
				}
			}
			return;
		}

		paint.setStrokeWidth(dp(1f));
		paint.setColor(neutralColor);
		paint.setAlpha(active ? 150 : 90);
		canvas.drawLine(left, center, right, center, paint);

		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(dp(2f));
		paint.setColor(accentColor);
		paint.setAlpha(active ? 255 : 96);
		float previousX = left;
		float previousY = gainY(0, top, bottom);
		for (int i = 0; i < AudioEffectsProfile.CANONICAL_FREQ_HZ.length; i++) {
			float x = left + (right - left) * EqualizerCurveGeometry.frequencyPosition(
					AudioEffectsProfile.CANONICAL_FREQ_HZ[i]);
			float y = gainY(store.getIntPref(AudioEffectsProfileRepository.CANONICAL_CURVE_DB[i]), top,
					bottom);
			if (i != 0) canvas.drawLine(previousX, previousY, x, y, paint);
			previousX = x;
			previousY = y;
		}

		paint.setStyle(Paint.Style.FILL);
		for (int i = 0; i < AudioEffectsProfile.CANONICAL_FREQ_HZ.length; i++) {
			float x = left + (right - left) * EqualizerCurveGeometry.frequencyPosition(
					AudioEffectsProfile.CANONICAL_FREQ_HZ[i]);
			float y = gainY(store.getIntPref(AudioEffectsProfileRepository.CANONICAL_CURVE_DB[i]), top,
					bottom);
			canvas.drawCircle(x, y, dp(2.5f), paint);
		}
	}

	private float gainY(int gainDb, float top, float bottom) {
		return top + (bottom - top) * EqualizerCurveGeometry.gainPosition(gainDb);
	}

	private int dp(float value) {
		return Math.round(value * density);
	}

	private static int resolveColor(Context context, int attribute, int fallback) {
		TypedValue value = new TypedValue();
		return context.getTheme().resolveAttribute(attribute, value, true) ? value.data : fallback;
	}
}
