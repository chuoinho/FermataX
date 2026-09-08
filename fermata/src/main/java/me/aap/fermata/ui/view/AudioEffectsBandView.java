package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.core.content.ContextCompat;

import me.aap.fermata.R;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.utils.pref.PreferenceStore;

/** A single accessible, whole-dB equalizer band. */
final class AudioEffectsBandView extends View implements PreferenceStore.Listener {
	interface ValueClickListener {
		void onValueClick(AudioEffectsBandView band);
	}

	private static final int PHONE_TOUCH_WIDTH_DP = 48;
	private static final int AUTO_TOUCH_WIDTH_DP = 64;
	private static final int MIN_HEIGHT_DP = 224;
	private static final int VALUE_HEIGHT_DP = 44;
	private static final int FREQUENCY_HEIGHT_DP = 42;
	private final PreferenceStore store;
	private final PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref;
	private final int frequencyHz;
	private final float density;
	private final float scaledDensity;
	private final int accentColor;
	private final int primaryColor;
	private final int secondaryColor;
	private final int touchSlop;
	private final ValueClickListener valueClickListener;
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private float downX;
	private float downY;
	private AudioEffectsScreenLayoutPolicy.GestureAxis gestureAxis =
			AudioEffectsScreenLayoutPolicy.GestureAxis.UNDECIDED;

	AudioEffectsBandView(Context context, PreferenceStore store,
			PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref, int frequencyHz,
			boolean automotive, ValueClickListener valueClickListener) {
		super(context);
		this.store = store;
		this.pref = pref;
		this.frequencyHz = frequencyHz;
		this.valueClickListener = valueClickListener;
		density = getResources().getDisplayMetrics().density;
		scaledDensity = getResources().getDisplayMetrics().scaledDensity;
		accentColor = resolveColor(context, android.R.attr.colorAccent, 0xff4f9cff);
		primaryColor = resolveColor(context, android.R.attr.textColorPrimary, 0xffffffff);
		secondaryColor = resolveColor(context, android.R.attr.textColorSecondary, 0xffa0a0a0);
		touchSlop = Math.max(1, android.view.ViewConfiguration.get(context).getScaledTouchSlop());
		setFocusable(true);
		setClickable(true);
		setMinimumWidth(dp(automotive ? AUTO_TOUCH_WIDTH_DP : PHONE_TOUCH_WIDTH_DP));
		setMinimumHeight(dp(MIN_HEIGHT_DP));
		setContentDescription(accessibilityText());
	}

	int getFrequencyHz() {
		return frequencyHz;
	}

	int getValueDb() {
		return store.getIntPref(pref);
	}

	void setValueDb(int valueDb) {
		store.applyIntPref(pref, clamp(valueDb));
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
	public void onPreferenceChanged(PreferenceStore changedStore,
			java.util.List<PreferenceStore.Pref<?>> changed) {
		if (changed.contains(pref)) {
			setContentDescription(accessibilityText());
			invalidate();
		}
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		setContentDescription(accessibilityText());
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float top = dp(VALUE_HEIGHT_DP);
		float bottom = Math.max(top + dp(80), getHeight() - dp(FREQUENCY_HEIGHT_DP));
		float center = (top + bottom) / 2f;
		float x = getWidth() / 2f;
		float position = EqualizerCurveGeometry.gainPosition(getValueDb());
		float y = top + ((bottom - top) * position);
		int color = isEnabled() ? accentColor : secondaryColor;

		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeWidth(dp(4));
		paint.setColor(secondaryColor);
		paint.setAlpha(isEnabled() ? 220 : 90);
		canvas.drawLine(x, top, x, bottom, paint);
		paint.setColor(color);
		paint.setAlpha(isEnabled() ? 255 : 120);
		canvas.drawLine(x, center, x, y, paint);
		paint.setStrokeWidth(dp(1));
		paint.setColor(secondaryColor);
		paint.setAlpha(isEnabled() ? 180 : 75);
		canvas.drawLine(0, center, getWidth(), center, paint);

		paint.setStyle(Paint.Style.FILL);
		paint.setColor(color);
		paint.setAlpha(isEnabled() ? 255 : 130);
		canvas.drawCircle(x, y, dp(10), paint);
		paint.setColor(resolveColor(getContext(), android.R.attr.colorBackground, 0xff202124));
		paint.setAlpha(255);
		canvas.drawCircle(x, y, dp(7), paint);

		paint.setTextAlign(Paint.Align.CENTER);
		paint.setTypeface(android.graphics.Typeface.DEFAULT);
		paint.setTextSize(sp(16));
		paint.setColor(isEnabled() ? primaryColor : secondaryColor);
		canvas.drawText(formatDb(getValueDb()), x, dp(25), paint);
		paint.setTextSize(sp(14));
		canvas.drawText(frequencyLabel(frequencyHz), x,
				getHeight() - dp(13), paint);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled()) return true;
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				downX = event.getX();
				downY = event.getY();
				gestureAxis = AudioEffectsScreenLayoutPolicy.GestureAxis.UNDECIDED;
				disallowParentIntercept(true);
				return true;
			}
			case MotionEvent.ACTION_MOVE -> {
				if (gestureAxis == AudioEffectsScreenLayoutPolicy.GestureAxis.UNDECIDED) {
					gestureAxis = AudioEffectsScreenLayoutPolicy.resolveBandGestureAxis(
							event.getX() - downX, event.getY() - downY, touchSlop);
					if (gestureAxis == AudioEffectsScreenLayoutPolicy.GestureAxis.HORIZONTAL) {
						disallowParentIntercept(false);
						return true;
					}
				}
				if (gestureAxis == AudioEffectsScreenLayoutPolicy.GestureAxis.VERTICAL) {
					setValueFromY(event.getY());
				}
				return true;
			}
			case MotionEvent.ACTION_UP -> {
				if (gestureAxis == AudioEffectsScreenLayoutPolicy.GestureAxis.VERTICAL) {
					setValueFromY(event.getY());
				} else if ((gestureAxis == AudioEffectsScreenLayoutPolicy.GestureAxis.UNDECIDED) &&
						event.getY() <= dp(VALUE_HEIGHT_DP)) {
					performClick();
				}
				disallowParentIntercept(false);
				return true;
			}
			case MotionEvent.ACTION_CANCEL -> {
				gestureAxis = AudioEffectsScreenLayoutPolicy.GestureAxis.UNDECIDED;
				disallowParentIntercept(false);
				return true;
			}
			default -> {
				return true;
			}
		}
	}

	@Override
	public boolean performClick() {
		super.performClick();
		valueClickListener.onValueClick(this);
		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (!isEnabled()) return super.onKeyDown(keyCode, event);
		if ((keyCode == KeyEvent.KEYCODE_DPAD_UP) || (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
			setValueDb(getValueDb() + ((keyCode == KeyEvent.KEYCODE_DPAD_UP) ? 1 : -1));
			return true;
		}
		if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER) || (keyCode == KeyEvent.KEYCODE_ENTER)) {
			performClick();
			return true;
		}
		if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT) || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
			View next = focusSearch(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? FOCUS_LEFT : FOCUS_RIGHT);
			return (next != null) && next.requestFocus();
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (isEnabled() && event.getActionMasked() == MotionEvent.ACTION_SCROLL &&
				(event.getSource() & InputDevice.SOURCE_ROTARY_ENCODER) != 0) {
			float delta = event.getAxisValue(MotionEvent.AXIS_SCROLL);
			if (delta != 0f) {
				setValueDb(getValueDb() + ((delta > 0f) ? 1 : -1));
				return true;
			}
		}
		return super.onGenericMotionEvent(event);
	}

	private void setValueFromY(float y) {
		float top = dp(VALUE_HEIGHT_DP);
		float bottom = Math.max(top + dp(80), getHeight() - dp(FREQUENCY_HEIGHT_DP));
		float position = (y - top) / Math.max(1f, bottom - top);
		setValueDb(EqualizerCurveGeometry.gainDb(position));
	}

	private void disallowParentIntercept(boolean disallow) {
		ViewParent parent = getParent();
		if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
	}

	private String accessibilityText() {
		String value = getContext().getString(R.string.audio_effects_band_format,
				frequencyLabel(frequencyHz), formatDb(getValueDb()));
		return isEnabled() ? value : value + ", " + getContext().getString(R.string.audio_effects_disabled);
	}

	private static String formatDb(int value) {
		return (value > 0 ? "+" : "") + value;
	}

	static String frequencyLabel(int frequency) {
		return (frequency >= 1_000) ? (frequency / 1_000) + "k" : String.valueOf(frequency);
	}

	private int clamp(int value) {
		return Math.max(AudioEffectsProfile.MIN_CANONICAL_DB,
				Math.min(AudioEffectsProfile.MAX_CANONICAL_DB, value));
	}

	private int dp(float value) {
		return Math.round(value * density);
	}

	private float sp(float value) {
		return value * scaledDensity;
	}

	private static int resolveColor(Context context, int attribute, int fallback) {
		TypedValue value = new TypedValue();
		if (!context.getTheme().resolveAttribute(attribute, value, true)) return fallback;
		return (value.resourceId == 0) ? value.data : ContextCompat.getColor(context, value.resourceId);
	}
}
