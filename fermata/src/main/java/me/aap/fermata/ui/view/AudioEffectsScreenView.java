package me.aap.fermata.ui.view;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;

import me.aap.fermata.R;
import me.aap.fermata.media.audio.AudioEffectCapability;
import me.aap.fermata.media.audio.AudioEffectsDraft;
import me.aap.fermata.media.audio.AudioEffectsPreset;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.utils.pref.PreferenceStore;

/** Shared native EQ editor used by the phone and projected settings surfaces. */
public final class AudioEffectsScreenView extends FrameLayout
		implements AudioEffectsDraft.Listener, PreferenceStore.Listener {
	private static final int BAND_HEIGHT_DP = 168;
	private static final int ACTION_MIN_HEIGHT_DP = 56;
	private static final int CONTENT_PADDING_DP = 8;
	private final AudioEffectsDraft draft;
	private final PreferenceStore store;
	private final LinearLayout content;
	private final LinearLayout body;
	private final LinearLayout equalizerColumn;
	private final LinearLayout effectsColumn;
	private final EqualizerScaleView equalizerScale;
	private final LinearLayout bankTabs;
	private final ScrollView verticalScroll;
	private final AudioEffectsApplyView actions;
	private final HorizontalScrollView bandScroll;
	private final LinearLayout bandStrip;
	private final Spinner preset;
	private final Button[] bankButtons;
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
	private final int surfaceColor;
	private final boolean automotive;
	private boolean updating;
	private int selectedBank;

	public AudioEffectsScreenView(Context context, AudioEffectsDraft draft,
			MediaSessionCallback callback) {
		super(context);
		this.draft = draft;
		this.store = draft.getStore();
		density = getResources().getDisplayMetrics().density;
		primaryColor = resolveColor(context, android.R.attr.textColorPrimary, Color.WHITE);
		secondaryColor = resolveColor(context, android.R.attr.textColorSecondary, 0xffa0a0a0);
		surfaceColor = resolveColor(context, com.google.android.material.R.attr.colorSurface,
				resolveColor(context, android.R.attr.colorBackground, 0xff202124));
		setFocusable(false);
		setBackgroundColor(Color.TRANSPARENT);
		automotive = isAutomotiveContext(context);

		verticalScroll = new BandPageScrollView(context);
		verticalScroll.setFillViewport(true);
		verticalScroll.setClipToPadding(false);
		content = new LinearLayout(context);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(dp(CONTENT_PADDING_DP), dp(CONTENT_PADDING_DP),
				dp(CONTENT_PADDING_DP), dp(CONTENT_PADDING_DP));
		verticalScroll.addView(content, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		addView(verticalScroll, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

		actions = new AudioEffectsApplyView(context, draft, callback);
		FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(MATCH_PARENT,
				WRAP_CONTENT, Gravity.BOTTOM);
		addView(actions, actionParams);

		LinearLayout topRow = createRow();
		TextView title = textView(16);
		title.setText(R.string.audio_effects);
		topRow.addView(title, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
		masterSwitch = new SwitchCompat(context);
		masterSwitch.setText(null);
		masterSwitch.setMinWidth(dp(64));
		masterSwitch.setMinHeight(dp(48));
		masterSwitch.setContentDescription(context.getString(R.string.audio_effects));
		topRow.addView(masterSwitch, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
		masterSwitch.setOnCheckedChangeListener((button, checked) -> {
			if (!updating) store.applyBooleanPref(AudioEffectsProfileRepository.ENABLED, checked);
		});
		content.addView(topRow);

		LinearLayout presetRow = createRow();
		TextView presetLabel = textView(16);
		presetLabel.setText(R.string.preset_name);
		presetRow.addView(presetLabel, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
		preset = new Spinner(context);
		preset.setContentDescription(context.getString(R.string.preset_name));
		preset.setPopupBackgroundDrawable(new ColorDrawable(surfaceColor));
		preset.setAdapter(new PresetAdapter(context, presetLabels()));
		presetRow.addView(preset, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
		content.addView(presetRow);
		preset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				AudioEffectsPreset selected = AudioEffectsPreset.values()[position];
				if (!updating && selected.hasCurve()) draft.applyPreset(selected);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		equalizerColumn = new LinearLayout(context);
		equalizerColumn.setOrientation(LinearLayout.VERTICAL);
		effectsColumn = new LinearLayout(context);
		effectsColumn.setOrientation(LinearLayout.VERTICAL);
		body = new LinearLayout(context);
		body.setOrientation(LinearLayout.VERTICAL);
		body.setBaselineAligned(false);
		equalizerScale = new EqualizerScaleView(context);

		bankTabs = new LinearLayout(context);
		bankTabs.setOrientation(LinearLayout.HORIZONTAL);
		bankTabs.setVisibility(GONE);
		bankButtons = new Button[2];
		String[] bankLabels = getResources().getStringArray(R.array.audio_effects_band_banks);
		for (int i = 0; i < bankButtons.length; i++) {
			final int bank = i;
			bankButtons[i] = buttonText(bankLabels[i]);
			bankButtons[i].setOnClickListener(v -> showBank(bank));
			bankTabs.addView(bankButtons[i], new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
		}

		equalizerColumn.addView(bankTabs, new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));

		bandScroll = new BandScrollView(context);
		bandScroll.setHorizontalScrollBarEnabled(true);
		bandScroll.setScrollbarFadingEnabled(false);
		bandScroll.setFillViewport(false);
		bandScroll.setContentDescription(context.getString(R.string.audio_effects_band_scroll));
		bandStrip = new LinearLayout(context);
		bandStrip.setOrientation(LinearLayout.HORIZONTAL);
		bandStrip.setGravity(Gravity.CENTER_VERTICAL);
		bandStrip.setPadding(0, 0, 0, 0);
		bands = new AudioEffectsBandView[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
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
		LinearLayout bandArea = new LinearLayout(context);
		bandArea.setOrientation(LinearLayout.HORIZONTAL);
		bandArea.addView(equalizerScale, new LinearLayout.LayoutParams(dp(
				AudioEffectsScreenLayoutPolicy.EQ_SCALE_WIDTH_DP), dp(BAND_HEIGHT_DP)));
		bandArea.addView(bandScroll, new LinearLayout.LayoutParams(0, dp(BAND_HEIGHT_DP), 1f));
		equalizerColumn.addView(bandArea, new LinearLayout.LayoutParams(MATCH_PARENT,
				dp(BAND_HEIGHT_DP)));

		addGainControl(effectsColumn, R.string.preamp, AudioEffectsProfileRepository.PREAMP_DB,
				AudioEffectsProfile.MIN_CANONICAL_DB, 0);
		bassBoostSwitch = addGainControl(effectsColumn, R.string.bass_boost,
				AudioEffectsProfileRepository.BASS_BOOST_STRENGTH, 0, 1_000,
				AudioEffectsProfileRepository.BASS_BOOST_ENABLED, true);
		loudnessSwitch = addGainControl(effectsColumn, R.string.vol_boost,
				AudioEffectsProfileRepository.LOUDNESS_GAIN, 0, 1_000,
				AudioEffectsProfileRepository.LOUDNESS_ENABLED, true);
		if (callback.getAudioEffectsCapabilities().contains(AudioEffectCapability.VIRTUALIZER)) {
			virtualizerSwitch = addGainControl(effectsColumn, R.string.virtualizer,
					AudioEffectsProfileRepository.VIRTUALIZER_STRENGTH, 0, 1_000,
					AudioEffectsProfileRepository.VIRTUALIZER_ENABLED, true);
			addVirtualizerMode(effectsColumn, AudioEffectsProfileRepository.VIRTUALIZER_ENABLED);
		} else {
			virtualizerSwitch = null;
		}
		body.addView(equalizerColumn, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		body.addView(effectsColumn, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		content.addView(body, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

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
		preset.setEnabled(!draft.isApplying());
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
		int[] curve = new int[bands.length];
		for (int i = 0; i < bands.length; i++) curve[i] = bands[i].getValueDb();
		preset.setSelection(AudioEffectsPreset.match(curve).ordinal(), false);
		preset.setEnabled(!draft.isApplying());
		updating = false;
	}

	private String[] presetLabels() {
		return getResources().getStringArray(R.array.audio_effects_presets);
	}

	private Button buttonText(String text) {
		AppCompatButton button = (AppCompatButton) button(R.string.audio_effects);
		button.setText(text);
		button.setSingleLine(false);
		button.setEllipsize(null);
		return button;
	}

	private void showBank(int bank) {
		selectedBank = bank;
		for (int i = 0; i < bands.length; i++) {
			bands[i].setVisibility((bankTabs.getVisibility() == VISIBLE) &&
					i / AudioEffectsScreenLayoutPolicy.BANK_SIZE != selectedBank ? GONE : VISIBLE);
		}
		for (int i = 0; i < bankButtons.length; i++) bankButtons[i].setSelected(i == selectedBank);
		bandScroll.scrollTo(0, 0);
		bandStrip.requestLayout();
	}

	private void updateLayoutForSize(int widthPx, int heightPx) {
		int widthDp = Math.max(0, Math.round(widthPx / density));
		body.setOrientation(LinearLayout.VERTICAL);
		int contentWidthDp = Math.max(0, widthDp - (CONTENT_PADDING_DP * 2) -
				AudioEffectsScreenLayoutPolicy.EQ_SCALE_WIDTH_DP);
		int count = AudioEffectsScreenLayoutPolicy.needsBandBanks(contentWidthDp, automotive) ?
				AudioEffectsScreenLayoutPolicy.BANK_SIZE : bands.length;
		int width = AudioEffectsScreenLayoutPolicy.bandWidthDp(contentWidthDp, automotive, count);
		for (int i = 0; i < bands.length; i++) {
			ViewGroup.LayoutParams raw = bands[i].getLayoutParams();
			LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) raw;
			params.width = dp(width);
			params.height = dp(BAND_HEIGHT_DP);
			params.leftMargin = (i % AudioEffectsScreenLayoutPolicy.BANK_SIZE == 0) ? 0 :
					dp(4);
			bands[i].setLayoutParams(params);
		}
		bankTabs.setVisibility(count == AudioEffectsScreenLayoutPolicy.BANK_SIZE ? VISIBLE : GONE);
		if (count == bands.length) selectedBank = 0;
		showBank(selectedBank);
	}

	private void refreshSwitch(SwitchCompat toggle,
			PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> pref, boolean master) {
		toggle.setEnabled(master);
		toggle.setChecked(store.getBooleanPref(pref));
	}

	private void addGainControl(LinearLayout parent, int title,
			PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref, int min, int max) {
		addGainControl(parent, title, pref, min, max, null, false);
	}

	@androidx.annotation.Nullable
	private SwitchCompat addGainControl(LinearLayout parent, int title,
			PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref, int min, int max,
			PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref,
			boolean showSwitch) {
		GainControl control = new GainControl(parent, title, pref, min, max, enabledPref,
				showSwitch);
		gainControls.add(control);
		return control.toggle;
	}

	private void addVirtualizerMode(LinearLayout parent,
			PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref) {
		LinearLayout row = createRow();
		TextView label = textView(16);
		label.setText(R.string.audio_effects_mode);
		label.setSingleLine(false);
		label.setEllipsize(null);
		row.addView(label, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
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
		showNumericEditor(title, String.valueOf(current), InputType.TYPE_CLASS_NUMBER |
				((min < 0) ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0), min, max,
				getContext().getString(R.string.audio_effects_range_error, min, max),
				text -> Integer.parseInt(text.trim()), setter);
	}

	private void showNumericEditor(String title, String current, int inputType,
			double min, double max, String rangeError, Function<String, Integer> parser,
			IntConsumer setter) {
		EditText input = new EditText(getContext());
		input.setInputType(inputType);
		input.setSingleLine(true);
		input.setSelectAllOnFocus(true);
		input.setText(current);
		input.setSelection(input.length());
		int padding = dp(8);
		input.setPadding(padding, 0, padding, 0);
		AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle(title)
				.setView(input).setNegativeButton(R.string.cancel, null)
				.setPositiveButton(android.R.string.ok, null).create();
		dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
				.setOnClickListener(v -> {
					try {
						String text = input.getText().toString().trim();
						double displayed = Double.parseDouble(text.replace(',', '.'));
						if (!Double.isFinite(displayed) || (displayed < min) ||
								(displayed > max)) throw new NumberFormatException();
						int value = parser.apply(text);
						setter.accept(value);
						dialog.dismiss();
					} catch (NumberFormatException error) {
						input.setError(rangeError);
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
		styleButton(getContext(), button);
		return button;
	}

	static void styleButton(Context context, Button button) {
		int surface = resolveColor(context, com.google.android.material.R.attr.colorSurface,
				resolveColor(context, android.R.attr.colorBackground, 0xff202124));
		int selectedSurface = stateSurface(surface);
		int disabledSurface = resolveColor(context, com.google.android.material.R.attr.colorSecondary,
				surface);
		int[][] states = new int[][]{
				new int[]{-android.R.attr.state_enabled},
				new int[]{android.R.attr.state_pressed},
				new int[]{android.R.attr.state_selected},
				new int[]{android.R.attr.state_focused},
				new int[]{}
		};
		button.setTextColor(new ColorStateList(states, new int[]{
				readableText(disabledSurface), readableText(selectedSurface),
				readableText(selectedSurface), readableText(selectedSurface), readableText(surface)}));
		button.setBackgroundTintList(new ColorStateList(states, new int[]{
				disabledSurface, selectedSurface, selectedSurface, selectedSurface, surface}));
	}

	private final class PresetAdapter extends ArrayAdapter<String> {
		PresetAdapter(Context context, String[] labels) {
			super(context, android.R.layout.simple_spinner_item, labels);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			return createView(position, false);
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {
			View view = createView(position, true);
			view.setSelected(position == preset.getSelectedItemPosition());
			return view;
		}

		private TextView createView(int position, boolean dropDown) {
			TextView view = new TextView(AudioEffectsScreenView.this.getContext());
			view.setText(getItem(position));
			view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
			int normalText = readableText(surfaceColor);
			int selectedText = readableText(stateSurface(surfaceColor));
			view.setTextColor(new ColorStateList(new int[][]{
					new int[]{-android.R.attr.state_enabled},
					new int[]{android.R.attr.state_selected},
					new int[]{android.R.attr.state_pressed},
					new int[]{android.R.attr.state_focused},
					new int[]{}
			}, new int[]{normalText, selectedText, selectedText, selectedText, normalText}));
			view.setGravity(Gravity.CENTER_VERTICAL);
			view.setMinHeight(dp(48));
			view.setPadding(dp(12), 0, dp(12), 0);
			GradientDrawable background = new GradientDrawable();
			background.setColor(Color.WHITE);
			view.setBackground(background);
			view.setBackgroundTintList(new ColorStateList(new int[][]{
					new int[]{-android.R.attr.state_enabled},
					new int[]{android.R.attr.state_selected},
					new int[]{android.R.attr.state_pressed},
					new int[]{android.R.attr.state_focused},
					new int[]{}
			}, new int[]{surfaceColor, stateSurface(surfaceColor), stateSurface(surfaceColor),
					stateSurface(surfaceColor), surfaceColor}));
			if (!dropDown) view.setSelected(false);
			return view;
		}
	}

	private static final class EqualizerScaleView extends View {
		private final float density;
		private final float scaledDensity;
		private final int color;
		private final android.graphics.Paint paint = new android.graphics.Paint(
				android.graphics.Paint.ANTI_ALIAS_FLAG);

		EqualizerScaleView(Context context) {
			super(context);
			density = getResources().getDisplayMetrics().density;
			scaledDensity = getResources().getDisplayMetrics().scaledDensity;
			color = resolveColor(context, android.R.attr.textColorSecondary, 0xffa0a0a0);
			setContentDescription(context.getString(R.string.audio_effects_scale));
		}

		@Override
		protected void onDraw(android.graphics.Canvas canvas) {
			paint.setColor(color);
			paint.setTextSize(10 * scaledDensity);
			paint.setTextAlign(android.graphics.Paint.Align.RIGHT);
			canvas.drawText("+15", getWidth() - dp(2), dp(25), paint);
			float center = (dp(30) + Math.max(dp(110), getHeight() - dp(30))) / 2F;
			canvas.drawText("0", getWidth() - dp(2), center + dp(4), paint);
			canvas.drawText("-15", getWidth() - dp(2), getHeight() - dp(13), paint);
		}

		private int dp(float value) {
			return Math.round(value * density);
		}
	}

	private static boolean isAutomotiveContext(Context context) {
		Context current = context;
		while (true) {
			if (current instanceof FermataActivity activity) return activity.isCarActivity();
			if (!(current instanceof ContextWrapper wrapper)) return false;
			Context base = wrapper.getBaseContext();
			if (base == current) return false;
			current = base;
		}
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
		int viewport = findViewportHeight();
		if (viewport <= 0) viewport = MeasureSpec.getSize(heightMeasureSpec);
		updateLayoutForSize(width, viewport);
		actions.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		int actionHeight = Math.max(dp(ACTION_MIN_HEIGHT_DP), actions.getMeasuredHeight());
		content.setPadding(dp(CONTENT_PADDING_DP), dp(CONTENT_PADDING_DP),
				dp(CONTENT_PADDING_DP), actionHeight + dp(CONTENT_PADDING_DP));
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

	private static int stateSurface(int color) {
		int amount = luminance(color) > 0.5f ? -24 : 24;
		return Color.rgb(adjust(Color.red(color), amount), adjust(Color.green(color), amount),
				adjust(Color.blue(color), amount));
	}

	private static int adjust(int value, int amount) {
		return Math.max(0, Math.min(255, value + amount));
	}

	private static int readableText(int background) {
		return contrast(background, Color.WHITE) >= contrast(background, Color.BLACK) ?
				Color.WHITE : Color.BLACK;
	}

	private static float luminance(int color) {
		return 0.2126f * linear(Color.red(color)) + 0.7152f * linear(Color.green(color)) +
				0.0722f * linear(Color.blue(color));
	}

	private static float linear(int channel) {
		float value = channel / 255f;
		return (value <= 0.03928f) ? value / 12.92f : (float) Math.pow(
				(value + 0.055f) / 1.055f, 2.4f);
	}

	private static float contrast(int background, int foreground) {
		float backgroundLuminance = luminance(background);
		float foregroundLuminance = luminance(foreground);
		float lighter = Math.max(backgroundLuminance, foregroundLuminance);
		float darker = Math.min(backgroundLuminance, foregroundLuminance);
		return (lighter + 0.05f) / (darker + 0.05f);
	}

	private final class GainControl {
		private final PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref;
		private final int title;
		private final int min;
		private final int max;
		private final TextView value;
		private final SeekBar seek;
		private final Button mode;
		@androidx.annotation.Nullable
		private final SwitchCompat toggle;
		private final PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref;

		GainControl(LinearLayout parent, int title,
				PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref, int min, int max,
				PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref,
				boolean showSwitch) {
			this.pref = pref;
			this.title = title;
			this.min = min;
			this.max = max;
			this.mode = null;
			this.enabledPref = enabledPref;
			LinearLayout control = new LinearLayout(getContext());
			control.setOrientation(LinearLayout.VERTICAL);
			LinearLayout header = createRow();
			TextView label = textView(15);
			label.setText(title);
			label.setSingleLine(false);
			label.setEllipsize(null);
			header.addView(label, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
			value = textView(15);
			value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
			value.setSingleLine(true);
			value.setMinHeight(dp(48));
			value.setPadding(dp(8), 0, dp(4), 0);
			header.addView(value, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
			toggle = showSwitch ? new SwitchCompat(getContext()) : null;
			if (toggle != null) {
				toggle.setText(null);
				toggle.setMinWidth(dp(64));
				toggle.setMinHeight(dp(48));
				toggle.setContentDescription(getContext().getString(title));
				header.addView(toggle, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
				toggle.setOnCheckedChangeListener((button, checked) -> {
					if (!updating) store.applyBooleanPref(enabledPref, checked);
				});
			}
			control.addView(header, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
			seek = new SeekBar(getContext());
			seek.setPadding(0, 0, 0, 0);
			control.addView(seek, new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));
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
			value.setOnClickListener(v -> showGainEditor());
			parent.addView(control, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
			refresh();
		}

		GainControl(Button mode, PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> enabledPref) {
			this.pref = null;
			this.title = 0;
			this.min = 0;
			this.max = 0;
			this.value = null;
			this.seek = null;
			this.mode = mode;
			this.toggle = null;
			this.enabledPref = enabledPref;
		}

		private void showGainEditor() {
			int current = store.getIntPref(pref);
			String titleText = getResources().getString(title);
			if (pref == AudioEffectsProfileRepository.LOUDNESS_GAIN) {
				showNumericEditor(titleText, AudioEffectsDisplayUnits.loudnessInput(current),
						InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
						0D, 10D, getResources().getString(R.string.audio_effects_range_error, 0, 10),
						AudioEffectsDisplayUnits::parseLoudnessGain,
						next -> store.applyIntPref(pref, next));
				return;
			}
			if ((pref == AudioEffectsProfileRepository.BASS_BOOST_STRENGTH) ||
					(pref == AudioEffectsProfileRepository.VIRTUALIZER_STRENGTH)) {
				showNumericEditor(titleText, AudioEffectsDisplayUnits.bassInput(current),
						InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
						0D, 100D, getResources().getString(R.string.audio_effects_range_error, 0, 100),
						AudioEffectsDisplayUnits::parseBassStrength,
						next -> store.applyIntPref(pref, next));
				return;
			}
			showNumericEditor(titleText, current, min, max, next -> store.applyIntPref(pref, next));
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
			value.setText(formatGain(current));
			seek.setProgress(Math.max(0, Math.min(max - min, current - min)));
			boolean enabled = isEnabled();
			value.setEnabled(enabled);
			seek.setEnabled(enabled);
			if (toggle != null) {
				toggle.setEnabled(store.getBooleanPref(AudioEffectsProfileRepository.ENABLED));
				toggle.setChecked(store.getBooleanPref(enabledPref));
			}
		}

		private String formatGain(int current) {
			if (pref == AudioEffectsProfileRepository.LOUDNESS_GAIN) {
				return AudioEffectsDisplayUnits.formatLoudnessGain(current);
			}
			if ((pref == AudioEffectsProfileRepository.BASS_BOOST_STRENGTH) ||
					(pref == AudioEffectsProfileRepository.VIRTUALIZER_STRENGTH)) {
				return AudioEffectsDisplayUnits.formatBassStrength(current);
			}
			return String.valueOf(current) + ((min < 0) ? " dB" : "%");
		}

		private boolean isEnabled() {
			return store.getBooleanPref(AudioEffectsProfileRepository.ENABLED) &&
					((enabledPref == null) || store.getBooleanPref(enabledPref));
		}
	}
}
