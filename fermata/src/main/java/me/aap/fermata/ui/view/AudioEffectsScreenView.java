package me.aap.fermata.ui.view;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.audio.AudioEffectCapability;
import me.aap.fermata.media.audio.AudioEffectsDraft;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.utils.pref.PreferenceStore;

/** Shared native EQ editor used by the phone and projected settings surfaces. */
public final class AudioEffectsScreenView extends FrameLayout
		implements AudioEffectsDraft.Listener, PreferenceStore.Listener {
	private static final int BAND_HEIGHT_DP = 238;
	private static final int ACTION_MIN_HEIGHT_DP = 64;
	private final AudioEffectsDraft draft;
	private final PreferenceStore store;
	private final LinearLayout content;
	private final ScrollView verticalScroll;
	private final AudioEffectsApplyView actions;
	private final Button setFlat;
	private final SwitchCompat masterSwitch;
	private final SwitchCompat bassBoostSwitch;
	private final SwitchCompat loudnessSwitch;
	@androidx.annotation.Nullable
	private final SwitchCompat virtualizerSwitch;
	private final List<GainControl> gainControls = new ArrayList<>();
	private final AudioEffectsBandView[] bands;
	private final float density;
	private final int primaryColor;
	private final int secondaryColor;
	private boolean updating;

	public AudioEffectsScreenView(Context context, AudioEffectsDraft draft,
			MediaSessionCallback callback) {
		super(context);
		this.draft = draft;
		this.store = draft.getStore();
		density = getResources().getDisplayMetrics().density;
		primaryColor = resolveColor(context, android.R.attr.textColorPrimary, Color.WHITE);
		secondaryColor = resolveColor(context, android.R.attr.textColorSecondary, 0xffa0a0a0);
		setFocusable(false);
		setBackgroundColor(Color.TRANSPARENT);

		verticalScroll = new BandPageScrollView(context);
		verticalScroll.setFillViewport(true);
		verticalScroll.setClipToPadding(false);
		content = new LinearLayout(context);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(dp(8), dp(8), dp(8), dp(8));
		verticalScroll.addView(content, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		addView(verticalScroll, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

		actions = new AudioEffectsApplyView(context, draft, callback);
		FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(MATCH_PARENT,
				WRAP_CONTENT, Gravity.BOTTOM);
		addView(actions, actionParams);

		masterSwitch = addSwitchRow(content, R.string.audio_effects, AudioEffectsProfileRepository.ENABLED);

		LinearLayout modeRow = createRow();
		modeRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
		setFlat = button(R.string.audio_effects_set_flat);
		modeRow.addView(setFlat, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
		content.addView(modeRow);
		setFlat.setOnClickListener(v -> draft.setFlat());

		TextView scaleLabel = textView(13);
		scaleLabel.setText(R.string.audio_effects_scale);
		scaleLabel.setTextColor(secondaryColor);
		scaleLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
		content.addView(scaleLabel, new LinearLayout.LayoutParams(MATCH_PARENT, dp(32)));

		HorizontalScrollView bandScroll = new BandScrollView(context);
		bandScroll.setHorizontalScrollBarEnabled(true);
		bandScroll.setScrollbarFadingEnabled(false);
		bandScroll.setFillViewport(false);
		bandScroll.setContentDescription(context.getString(R.string.audio_effects_band_scroll));
		LinearLayout bandStrip = new LinearLayout(context);
		bandStrip.setOrientation(LinearLayout.HORIZONTAL);
		bandStrip.setGravity(Gravity.CENTER_VERTICAL);
		bandStrip.setPadding(0, 0, dp(8), 0);
		bands = new AudioEffectsBandView[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
		boolean automotive = BuildConfig.AUTO;
		int bandWidth = dp(AudioEffectsScreenLayoutPolicy.bandWidthDp(automotive));
		for (int i = 0; i < bands.length; i++) {
			bands[i] = new AudioEffectsBandView(context, store,
					AudioEffectsProfileRepository.CANONICAL_CURVE_DB[i],
					AudioEffectsProfile.CANONICAL_FREQ_HZ[i], automotive, this::showBandEditor);
			bands[i].setId(View.generateViewId());
			if (i > 0) {
				bands[i].setNextFocusLeftId(bands[i - 1].getId());
				bands[i - 1].setNextFocusRightId(bands[i].getId());
			}
			LinearLayout.LayoutParams bandParams = new LinearLayout.LayoutParams(bandWidth,
					dp(BAND_HEIGHT_DP));
			bandParams.setMargins(i == 0 ? 0 : dp(8), 0, 0, 0);
			bandStrip.addView(bands[i], bandParams);
		}
		bandScroll.addView(bandStrip, new ViewGroup.LayoutParams(WRAP_CONTENT, dp(BAND_HEIGHT_DP)));
		content.addView(bandScroll, new LinearLayout.LayoutParams(MATCH_PARENT,
				dp(BAND_HEIGHT_DP)));

		addGainControl(content, R.string.preamp, AudioEffectsProfileRepository.PREAMP_DB,
				AudioEffectsProfile.MIN_CANONICAL_DB, 0);
		bassBoostSwitch = addSwitchRow(content, R.string.bass_boost,
				AudioEffectsProfileRepository.BASS_BOOST_ENABLED);
		addGainControl(content, R.string.strength,
				AudioEffectsProfileRepository.BASS_BOOST_STRENGTH, 0, 1_000,
				AudioEffectsProfileRepository.BASS_BOOST_ENABLED);
		loudnessSwitch = addSwitchRow(content, R.string.vol_boost,
				AudioEffectsProfileRepository.LOUDNESS_ENABLED);
		addGainControl(content, R.string.strength,
				AudioEffectsProfileRepository.LOUDNESS_GAIN, 0, 1_000,
				AudioEffectsProfileRepository.LOUDNESS_ENABLED);
		if (callback.getAudioEffectsCapabilities().contains(AudioEffectCapability.VIRTUALIZER)) {
			virtualizerSwitch = addSwitchRow(content, R.string.virtualizer,
					AudioEffectsProfileRepository.VIRTUALIZER_ENABLED);
			addGainControl(content, R.string.strength,
					AudioEffectsProfileRepository.VIRTUALIZER_STRENGTH, 0, 1_000,
					AudioEffectsProfileRepository.VIRTUALIZER_ENABLED);
			addVirtualizerMode(content, AudioEffectsProfileRepository.VIRTUALIZER_ENABLED);
		} else {
			virtualizerSwitch = null;
		}

		refreshFromStore();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		store.addBroadcastListener(this);
		draft.addListener(this);
		post(this::requestLayout);
	}

	@Override
	protected void onDetachedFromWindow() {
		store.removeBroadcastListener(this);
		draft.removeListener(this);
		super.onDetachedFromWindow();
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		post(this::requestLayout);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore changedStore,
			List<PreferenceStore.Pref<?>> changed) {
		if (AudioEffectsProfileRepository.containsProfilePreference(changed)) refreshFromStore();
	}

	@Override
	public void onStateChanged(AudioEffectsDraft.State state) {
		setFlat.setEnabled(!draft.isApplying());
	}

	private void refreshFromStore() {
		if (updating) return;
		updating = true;
		boolean master = store.getBooleanPref(AudioEffectsProfileRepository.ENABLED);
		masterSwitch.setChecked(master);
		refreshSwitch(bassBoostSwitch, AudioEffectsProfileRepository.BASS_BOOST_ENABLED, master);
		refreshSwitch(loudnessSwitch, AudioEffectsProfileRepository.LOUDNESS_ENABLED, master);
		if (virtualizerSwitch != null) {
			refreshSwitch(virtualizerSwitch, AudioEffectsProfileRepository.VIRTUALIZER_ENABLED, master);
		}
		for (AudioEffectsBandView band : bands) band.setEnabled(master);
		for (GainControl control : gainControls) control.refresh();
		setFlat.setEnabled(!draft.isApplying());
		updating = false;
	}

	private SwitchCompat addSwitchRow(LinearLayout parent, int title,
			PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> pref) {
		LinearLayout row = createRow();
		TextView label = textView(16);
		label.setText(title);
		row.addView(label, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
		SwitchCompat toggle = new SwitchCompat(getContext());
		toggle.setText(null);
		toggle.setMinWidth(dp(64));
		toggle.setMinHeight(dp(48));
		toggle.setContentDescription(getContext().getString(title));
		row.addView(toggle, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
		toggle.setOnCheckedChangeListener((button, checked) -> {
			if (!updating) store.applyBooleanPref(pref, checked);
		});
		parent.addView(row);
		return toggle;
	}

	private void refreshSwitch(SwitchCompat toggle,
			PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> pref, boolean master) {
		toggle.setEnabled(master);
		toggle.setChecked(store.getBooleanPref(pref));
	}

	private void addGainControl(LinearLayout parent, int title,
			PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref, int min, int max) {
		addGainControl(parent, title, pref, min, max, null);
	}

	private void addGainControl(LinearLayout parent, int title,
			PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref, int min, int max,
			PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref) {
		GainControl control = new GainControl(parent, title, pref, min, max, enabledPref);
		gainControls.add(control);
	}

	private void addVirtualizerMode(LinearLayout parent,
			PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref) {
		LinearLayout row = createRow();
		TextView label = textView(16);
		label.setText(R.string.string_format);
		row.addView(label, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
		Button value = button(R.string.auto);
		row.addView(value, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
		value.setOnClickListener(v -> showVirtualizerMode(value));
		parent.addView(row);
		value.setTag(AudioEffectsProfileRepository.VIRTUALIZER_MODE);
		value.setContentDescription(getContext().getString(R.string.virtualizer));
		gainControls.add(new GainControl(value, enabledPref));
	}

	private void showVirtualizerMode(Button value) {
		String[] labels = {getResources().getString(R.string.auto),
				getResources().getString(R.string.binaural),
				getResources().getString(R.string.transaural)};
		int[] modes = {android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO,
				android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_BINAURAL,
				android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL};
		int current = store.getIntPref(AudioEffectsProfileRepository.VIRTUALIZER_MODE);
		int selected = 0;
		for (int i = 0; i < modes.length; i++) if (modes[i] == current) selected = i;
		new AlertDialog.Builder(getContext()).setTitle(R.string.virtualizer)
				.setSingleChoiceItems(labels, selected, (dialog, which) -> {
					store.applyIntPref(AudioEffectsProfileRepository.VIRTUALIZER_MODE, modes[which]);
					dialog.dismiss();
				}).show();
	}

	private void showBandEditor(AudioEffectsBandView band) {
		showNumericEditor(band.getContext().getString(R.string.equalizer) + " " +
				AudioEffectsBandView.frequencyLabel(band.getFrequencyHz()), band.getValueDb(),
				AudioEffectsProfile.MIN_CANONICAL_DB, AudioEffectsProfile.MAX_CANONICAL_DB,
				band::setValueDb);
	}

	private void showNumericEditor(String title, int current, int min, int max,
			IntConsumer setter) {
		EditText input = new EditText(getContext());
		input.setInputType(InputType.TYPE_CLASS_NUMBER |
				((min < 0) ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
		input.setSingleLine(true);
		input.setSelectAllOnFocus(true);
		input.setText(String.valueOf(current));
		input.setSelection(input.length());
		int padding = dp(8);
		input.setPadding(padding, 0, padding, 0);
		AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle(title)
				.setView(input).setNegativeButton(R.string.cancel, null)
				.setPositiveButton(android.R.string.ok, null).create();
		dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
				.setOnClickListener(v -> {
					try {
						int value = Integer.parseInt(input.getText().toString().trim());
						if ((value < min) || (value > max)) throw new NumberFormatException();
						setter.accept(value);
						dialog.dismiss();
					} catch (NumberFormatException error) {
						input.setError(getContext().getString(R.string.audio_effects_range_error,
								min, max));
					}
				}));
		dialog.show();
	}

	private LinearLayout createRow() {
		LinearLayout row = new LinearLayout(getContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setMinimumHeight(dp(56));
		return row;
	}

	private TextView textView(float textSizeSp) {
		TextView text = new TextView(getContext());
		text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
		text.setTextColor(primaryColor);
		text.setGravity(Gravity.CENTER_VERTICAL);
		return text;
	}

	private Button button(int text) {
		AppCompatButton button = new AppCompatButton(getContext(), null,
				androidx.appcompat.R.attr.buttonStyle);
		button.setText(text);
		button.setAllCaps(false);
		button.setMinHeight(dp(48));
		button.setMinWidth(dp(48));
		return button;
	}

	private int findViewportHeight() {
		for (ViewParent parent = getParent(); parent != null; parent = parent.getParent()) {
			if (parent instanceof RecyclerView recyclerView) return recyclerView.getHeight();
		}
		return 0;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = MeasureSpec.getSize(widthMeasureSpec);
		if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) width = dp(320);
		actions.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		int actionHeight = Math.max(dp(ACTION_MIN_HEIGHT_DP), actions.getMeasuredHeight());
		content.setPadding(dp(8), dp(8), dp(8), actionHeight + dp(8));
		content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		int naturalHeight = content.getMeasuredHeight() + actionHeight;
		int viewportHeight = findViewportHeight();
		int desiredHeight = (viewportHeight > 0) ? viewportHeight : naturalHeight;
		int height = resolveSize(desiredHeight, heightMeasureSpec);
		setMeasuredDimension(resolveSize(width, widthMeasureSpec), height);
		int scrollHeight = Math.max(0, height - actionHeight);
		verticalScroll.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
				MeasureSpec.makeMeasureSpec(scrollHeight, MeasureSpec.EXACTLY));
		actions.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
				MeasureSpec.makeMeasureSpec(actionHeight, MeasureSpec.EXACTLY));
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		int width = right - left;
		int actionTop = Math.max(0, bottom - top - actions.getMeasuredHeight());
		verticalScroll.layout(0, 0, width, actionTop);
		actions.layout(0, actionTop, width, bottom - top);
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	/** Lets the vertical page own drags in the viewport beside the narrower band strip. */
	private static final class BandScrollView extends HorizontalScrollView {
		BandScrollView(Context context) {
			super(context);
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
			if ((event.getActionMasked() == MotionEvent.ACTION_DOWN) && !isInChild(event)) {
				return false;
			}
			return super.onInterceptTouchEvent(event);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if ((event.getActionMasked() == MotionEvent.ACTION_DOWN) && !isInChild(event)) {
				return false;
			}
			return super.onTouchEvent(event);
		}

		private boolean isInChild(MotionEvent event) {
			View child = getChildAt(0);
			if (child == null) return false;
			float x = event.getX() + getScrollX();
			float y = event.getY();
			return (x >= child.getLeft()) && (x < child.getRight()) &&
					(y >= child.getTop()) && (y < child.getBottom());
		}
	}

	private static final class BandPageScrollView extends ScrollView {
		BandPageScrollView(Context context) {
			super(context);
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
				requestDisallowInterceptTouchEvent(true);
			} else if ((event.getActionMasked() == MotionEvent.ACTION_UP) ||
					(event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
				requestDisallowInterceptTouchEvent(false);
			}
			return super.onInterceptTouchEvent(event);
		}
	}

	private static int resolveColor(Context context, int attribute, int fallback) {
		TypedValue value = new TypedValue();
		if (!context.getTheme().resolveAttribute(attribute, value, true)) return fallback;
		return (value.resourceId == 0) ? value.data : ContextCompat.getColor(context, value.resourceId);
	}

	private final class GainControl {
		private final PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref;
		private final int min;
		private final int max;
		private final TextView value;
		private final SeekBar seek;
		private final Button mode;
		private final PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref;

		GainControl(LinearLayout parent, int title,
				PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref, int min, int max,
				PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref) {
			this.pref = pref;
			this.min = min;
			this.max = max;
			this.mode = null;
			this.enabledPref = enabledPref;
			LinearLayout row = new LinearLayout(getContext());
			row.setOrientation(LinearLayout.VERTICAL);
			LinearLayout header = createRow();
			TextView label = textView(15);
			label.setText(title);
			header.addView(label, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
			value = textView(15);
			value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
			header.addView(value, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
			row.addView(header, new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));
			seek = new SeekBar(getContext());
			seek.setMax(max - min);
			seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
					if (fromUser && !updating) store.applyIntPref(pref, min + progress);
				}

				@Override
				public void onStartTrackingTouch(SeekBar bar) {
				}

				@Override
				public void onStopTrackingTouch(SeekBar bar) {
				}
			});
			row.addView(seek, new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));
			value.setOnClickListener(v -> showNumericEditor(getResources().getString(title),
					store.getIntPref(pref), min, max, next -> store.applyIntPref(pref, next)));
			parent.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
			refresh();
		}

		GainControl(Button mode, PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref) {
			this.pref = null;
			this.min = 0;
			this.max = 0;
			this.value = null;
			this.seek = null;
			this.mode = mode;
			this.enabledPref = enabledPref;
		}

		void refresh() {
			if (mode != null) {
				int current = store.getIntPref(AudioEffectsProfileRepository.VIRTUALIZER_MODE);
				mode.setText(current == android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_BINAURAL ?
						R.string.binaural : (current == android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL ?
								R.string.transaural : R.string.auto));
				mode.setEnabled(isEnabled());
				return;
			}
			int current = store.getIntPref(pref);
			value.setText(String.valueOf(current));
			seek.setProgress(Math.max(0, Math.min(max - min, current - min)));
			boolean enabled = isEnabled();
			value.setEnabled(enabled);
			seek.setEnabled(enabled);
		}

		private boolean isEnabled() {
			return store.getBooleanPref(AudioEffectsProfileRepository.ENABLED) &&
					((enabledPref == null) || store.getBooleanPref(enabledPref));
		}
	}
}
