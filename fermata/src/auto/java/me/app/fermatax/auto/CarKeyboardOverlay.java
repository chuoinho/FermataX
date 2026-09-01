package me.app.fermatax.auto;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.START;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils.TruncateAt;
import android.util.StateSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;

final class CarKeyboardOverlay {
	private static final int ROW_GAP_DP = 5;
	private static final long DELETE_REPEAT_DELAY_MS = 420;
	private static final long DELETE_REPEAT_MS = 88;
	private static final long DELETE_REPEAT_FAST_MS = 62;
	private final MainCarActivity activity;
	private final FrameLayout view;
	private final LinearLayout panel;
	private final TextView title;
	private final TextView value;
	private final LinearLayout keys;
	private final AppCompatImageButton voice;
	private final AppCompatImageButton paste;
	private final AppCompatImageButton clear;
	private final AppCompatImageButton cancel;
	private final CarKeyboardModel model = new CarKeyboardModel();
	private final List<FocusRow> focusRows = new ArrayList<>();
	private EditText target;
	private boolean submitOnEnter;
	private CarKeyboardModel.Action inputAction;
	private Runnable deleteRepeater;
	private int deleteRepeatCount;
	private int keyHeight;

	CarKeyboardOverlay(MainCarActivity activity) {
		this.activity = activity;
		view = new FrameLayout(activity);
		view.setBackgroundColor(0xB807111D);
		view.setFocusable(true);
		view.setFocusableInTouchMode(true);
		view.setOnClickListener(v -> {
		});

		panel = new LinearLayout(activity);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setPadding(dp(8), dp(7), dp(8), dp(8));
		panel.setBackground(panelBg());
		view.addView(panel, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, BOTTOM));

		LinearLayout toolbar = new LinearLayout(activity);
		toolbar.setGravity(CENTER_VERTICAL);
		toolbar.setPadding(0, 0, 0, dp(5));
		panel.addView(toolbar, new LinearLayout.LayoutParams(MATCH_PARENT, dp(43)));

		title = new TextView(activity);
		title.setTextColor(0xFFE2E8F0);
		title.setTextSize(14);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setSingleLine(true);
		title.setEllipsize(TruncateAt.END);
		toolbar.addView(title, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));

		voice = toolbarButton(R.drawable.record_voice, R.string.voice_search);
		voice.setOnClickListener(v -> {
			EditText input = target;
			if (input != null) activity.requestVoiceInput(input);
		});
		toolbar.addView(voice, toolbarButtonParams());

		paste = toolbarButton(R.drawable.keyboard_paste, R.string.car_keyboard_paste);
		paste.setOnClickListener(v -> paste());
		toolbar.addView(paste, toolbarButtonParams());

		clear = toolbarButton(R.drawable.delete, R.string.car_keyboard_clear);
		clear.setOnClickListener(v -> setText(""));
		toolbar.addView(clear, toolbarButtonParams());

		cancel = toolbarButton(R.drawable.keyboard_close, R.string.car_keyboard_cancel);
		cancel.setOnClickListener(v -> activity.stopInput());
		toolbar.addView(cancel, toolbarButtonParams());

		value = new TextView(activity);
		value.setGravity(CENTER_VERTICAL | START);
		value.setSingleLine(true);
		value.setEllipsize(TruncateAt.START);
		value.setTextColor(Color.WHITE);
		value.setTextSize(18);
		value.setPadding(dp(12), 0, dp(12), 0);
		value.setBackground(inputBg());
		panel.addView(value, new LinearLayout.LayoutParams(MATCH_PARENT, dp(44)));

		keys = new LinearLayout(activity);
		keys.setOrientation(LinearLayout.VERTICAL);
		keys.setPadding(0, dp(7), 0, 0);
		panel.addView(keys, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
	}

	void show(EditText target, boolean submitOnEnter) {
		stopDeleteRepeat();
		this.target = target;
		this.submitOnEnter = submitOnEnter;
		model.reset();
		inputAction = CarKeyboardModel.resolveAction(target.getImeOptions(), target.getInputType(),
				target.getMaxLines() == 1, submitOnEnter);
		CharSequence hint = target.getHint();
		title.setText((hint == null) || (hint.length() == 0) ?
				activity.getString(R.string.car_keyboard_input) : hint);
		voice.setVisibility(activity.isVoiceInputEnabled() && !isPassword(target) ?
				View.VISIBLE : View.GONE);
		value.setText(displayValue());
		keyHeight = resolveKeyHeight();
		renderKeys();

		View main = activity.findViewById(R.id.main_activity);
		ViewGroup parent = (main instanceof ViewGroup g) ? g : null;
		if (parent == null) {
			View decor = activity.getWindow().getDecorView();
			if (decor instanceof ViewGroup g) parent = g;
		}
		if (parent == null) return;
		if (view.getParent() != parent) {
			if (view.getParent() instanceof ViewGroup old) old.removeView(view);
			ViewGroup.LayoutParams lp;
			if (parent instanceof ConstraintLayout) {
				ConstraintLayout.LayoutParams clp =
						new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				clp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
				clp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
				clp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
				clp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
				lp = clp;
			} else {
				lp = new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT);
			}
			parent.addView(view, lp);
		}
		view.bringToFront();
		view.requestFocus();
	}

	void dismiss() {
		stopDeleteRepeat();
		if (view.getParent() instanceof ViewGroup parent) parent.removeView(view);
		target = null;
	}

	boolean isShowing() {
		return view.getParent() != null;
	}

	void refreshValue() {
		if (target != null) value.setText(displayValue());
	}

	boolean dispatchTap(float x, float y) {
		if (!isShowing()) return false;
		EditText input = target;
		long time = SystemClock.uptimeMillis();
		MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
		view.dispatchTouchEvent(down);
		down.recycle();
		activity.getWindow().getDecorView().postDelayed(() -> {
			if (!isShowing() || (target != input)) return;
			MotionEvent up = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, x, y, 0);
			view.dispatchTouchEvent(up);
			up.recycle();
		}, 100);
		return true;
	}

	boolean onKeyDown(int keyCode, KeyEvent event) {
		if (!isShowing() || (target == null)) return false;
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK -> {
				activity.stopInput();
				return true;
			}
			case KeyEvent.KEYCODE_DEL -> {
				delete();
				return true;
			}
			case KeyEvent.KEYCODE_ENTER -> {
				performAction();
				return true;
			}
		}
		int codePoint = event.getUnicodeChar();
		if (codePoint > 0) {
			append(new String(Character.toChars(codePoint)));
			return true;
		}
		return false;
	}

	private void renderKeys() {
		stopDeleteRepeat();
		keys.removeAllViews();
		focusRows.clear();
		addCharacterRow(CarKeyboardModel.NUMBER_ROW, 0);
		if (model.getMode() == CarKeyboardModel.Mode.ALPHA) {
			addCharacterRow(CarKeyboardModel.ALPHA_ROWS[0], 0);
			addCharacterRow(CarKeyboardModel.ALPHA_ROWS[1], 0.5f);
			addAlphaBottomRow();
		} else {
			for (String row : CarKeyboardModel.SYMBOL_ROWS) addCharacterRow(row, 0);
		}
		addControlRow();
		wireFocusNavigation();
	}

	private void addCharacterRow(String characters, float sideWeight) {
		LinearLayout row = row();
		FocusRow focus = new FocusRow(characters.length() + (2 * sideWeight));
		if (sideWeight > 0) addSpacer(row, sideWeight);
		float cursor = sideWeight;
		for (int i = 0; i < characters.length(); i++) {
			char character = characters.charAt(i);
			Button button = key(model.applyShift(character));
			button.setOnClickListener(v -> {
				CarKeyboardModel.ShiftState before = model.getShiftState();
				append(model.applyShift(character));
				model.characterTyped(character);
				if (before != model.getShiftState()) renderKeys();
			});
			row.addView(button, keyParams(1));
			focus.add(button, cursor + 0.5f);
			cursor += 1;
		}
		if (sideWeight > 0) addSpacer(row, sideWeight);
		addRow(row, focus);
	}

	private void addAlphaBottomRow() {
		LinearLayout row = row();
		float total = 1.35f + CarKeyboardModel.ALPHA_ROWS[2].length() + 1.35f;
		FocusRow focus = new FocusRow(total);
		float cursor = 0;

		AppCompatImageButton shift = iconKey(
				model.getShiftState() == CarKeyboardModel.ShiftState.CAPS_LOCK ?
						R.drawable.keyboard_caps_lock : R.drawable.keyboard_shift,
				R.string.car_keyboard_shift, model.getShiftState() != CarKeyboardModel.ShiftState.OFF);
		shift.setOnClickListener(v -> {
			model.tapShift(SystemClock.uptimeMillis());
			renderKeys();
		});
		row.addView(shift, keyParams(1.35f));
		focus.add(shift, cursor + 0.675f);
		cursor += 1.35f;

		for (char character : CarKeyboardModel.ALPHA_ROWS[2].toCharArray()) {
			Button button = key(model.applyShift(character));
			button.setOnClickListener(v -> {
				CarKeyboardModel.ShiftState before = model.getShiftState();
				append(model.applyShift(character));
				model.characterTyped(character);
				if (before != model.getShiftState()) renderKeys();
			});
			row.addView(button, keyParams(1));
			focus.add(button, cursor + 0.5f);
			cursor += 1;
		}

		AppCompatImageButton backspace = iconKey(R.drawable.keyboard_backspace,
				R.string.car_keyboard_delete, false);
		backspace.setOnClickListener(v -> delete());
		backspace.setOnTouchListener((v, event) -> {
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN -> {
					v.performClick();
					startDeleteRepeat();
					return true;
				}
				case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					stopDeleteRepeat();
					return true;
				}
			}
			return true;
		});
		row.addView(backspace, keyParams(1.35f));
		focus.add(backspace, cursor + 0.675f);
		addRow(row, focus);
	}

	private void addControlRow() {
		LinearLayout row = row();
		float[] weights = {1.35f, 0.8f, 4.2f, 0.8f, 1.85f};
		FocusRow focus = new FocusRow(9f);
		float cursor = 0;

		Button mode = modifier(model.getMode() == CarKeyboardModel.Mode.ALPHA ? "?123" : "ABC");
		mode.setOnClickListener(v -> {
			model.toggleMode();
			renderKeys();
		});
		row.addView(mode, keyParams(weights[0]));
		focus.add(mode, cursor + (weights[0] / 2));
		cursor += weights[0];

		Button comma = key(",");
		comma.setOnClickListener(v -> append(","));
		row.addView(comma, keyParams(weights[1]));
		focus.add(comma, cursor + (weights[1] / 2));
		cursor += weights[1];

		Button space = key(activity.getString(R.string.car_keyboard_space));
		space.setTextSize(13);
		space.setOnClickListener(v -> append(" "));
		row.addView(space, keyParams(weights[2]));
		focus.add(space, cursor + (weights[2] / 2));
		cursor += weights[2];

		Button period = key(".");
		period.setOnClickListener(v -> append("."));
		row.addView(period, keyParams(weights[3]));
		focus.add(period, cursor + (weights[3] / 2));
		cursor += weights[3];

		Button action = primary(actionLabel(inputAction));
		action.setOnClickListener(v -> performAction());
		// Some projected touch transports can lose the final UP event while a submit
		// changes the input surface. Submit on DOWN in that path; regular click and
		// accessibility activation retain the listener above.
		action.setOnTouchListener((v, event) -> {
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
				performAction();
				return true;
			}
			return true;
		});
		row.addView(action, keyParams(weights[4]));
		focus.add(action, cursor + (weights[4] / 2));
		addRow(row, focus);
	}

	private void startDeleteRepeat() {
		stopDeleteRepeat();
		deleteRepeatCount = 0;
		deleteRepeater = new Runnable() {
			@Override
			public void run() {
				if ((deleteRepeater != this) || !isShowing() || (target == null)) return;
				delete();
				deleteRepeatCount++;
				view.postDelayed(this, deleteRepeatCount > 10 ?
						DELETE_REPEAT_FAST_MS : DELETE_REPEAT_MS);
			}
		};
		view.postDelayed(deleteRepeater, DELETE_REPEAT_DELAY_MS);
	}

	private void stopDeleteRepeat() {
		if (deleteRepeater != null) view.removeCallbacks(deleteRepeater);
		deleteRepeater = null;
		deleteRepeatCount = 0;
	}

	private void wireFocusNavigation() {
		for (int rowIndex = 0; rowIndex < focusRows.size(); rowIndex++) {
			FocusRow row = focusRows.get(rowIndex);
			for (int i = 0; i < row.keys.size(); i++) {
				FocusKey key = row.keys.get(i);
				if (i > 0) key.view.setNextFocusLeftId(row.keys.get(i - 1).view.getId());
				if (i + 1 < row.keys.size()) key.view.setNextFocusRightId(row.keys.get(i + 1).view.getId());
				if (rowIndex > 0) key.view.setNextFocusUpId(nearest(focusRows.get(rowIndex - 1), key.center));
				if (rowIndex + 1 < focusRows.size()) {
					key.view.setNextFocusDownId(nearest(focusRows.get(rowIndex + 1), key.center));
				}
			}
		}
	}

	private int nearest(FocusRow row, float center) {
		FocusKey nearest = row.keys.get(0);
		float distance = Math.abs(nearest.center - center);
		for (int i = 1; i < row.keys.size(); i++) {
			FocusKey candidate = row.keys.get(i);
			float candidateDistance = Math.abs(candidate.center - center);
			if (candidateDistance < distance) {
				nearest = candidate;
				distance = candidateDistance;
			}
		}
		return nearest.view.getId();
	}

	private void addRow(LinearLayout row, FocusRow focus) {
		focus.normalize();
		focusRows.add(focus);
		keys.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, keyHeight + dp(ROW_GAP_DP)));
	}

	private LinearLayout row() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(CENTER);
		row.setPadding(0, 0, 0, dp(ROW_GAP_DP));
		return row;
	}

	private void addSpacer(LinearLayout row, float weight) {
		row.addView(new View(activity), new LinearLayout.LayoutParams(0, keyHeight, weight));
	}

	private LinearLayout.LayoutParams keyParams(float weight) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, keyHeight, weight);
		int margin = dp(2);
		lp.setMargins(margin, 0, margin, 0);
		return lp;
	}

	private Button key(String text) {
		Button button = new Button(activity);
		button.setId(View.generateViewId());
		button.setAllCaps(false);
		button.setText(text);
		button.setTextSize(text.length() > 1 ? 14 : 19);
		button.setTextColor(0xFFF3F7FB);
		button.setGravity(CENTER);
		button.setPadding(0, 0, 0, 0);
		button.setMinWidth(0);
		button.setMinHeight(0);
		button.setMinimumWidth(0);
		button.setMinimumHeight(0);
		button.setFocusable(true);
		button.setBackground(keyBg(0xFF203348, 0xFF315673, 0xFF3E6888));
		return button;
	}

	private Button modifier(String text) {
		Button button = key(text);
		button.setTextSize(13);
		button.setTypeface(Typeface.DEFAULT_BOLD);
		button.setBackground(keyBg(0xFF2B3E51, 0xFF3C607B, 0xFF496E8B));
		return button;
	}

	private Button primary(String text) {
		Button button = key(text);
		button.setTextSize(14);
		button.setTypeface(Typeface.DEFAULT_BOLD);
		button.setBackground(keyBg(0xFF0076B8, 0xFF1596D3, 0xFF25A7E2));
		return button;
	}

	private AppCompatImageButton iconKey(int icon, int description, boolean selected) {
		AppCompatImageButton button = new AppCompatImageButton(activity);
		button.setId(View.generateViewId());
		button.setImageResource(icon);
		button.setColorFilter(0xFFF3F7FB);
		button.setContentDescription(activity.getString(description));
		button.setPadding(dp(12), dp(12), dp(12), dp(12));
		button.setFocusable(true);
		button.setBackground(selected ?
				keyBg(0xFF087FB9, 0xFF1596D3, 0xFF25A7E2) :
				keyBg(0xFF2B3E51, 0xFF3C607B, 0xFF496E8B));
		return button;
	}

	private AppCompatImageButton toolbarButton(int icon, int description) {
		AppCompatImageButton button = new AppCompatImageButton(activity);
		button.setId(View.generateViewId());
		button.setImageResource(icon);
		button.setColorFilter(0xFFE5EDF7);
		button.setContentDescription(activity.getString(description));
		button.setPadding(dp(9), dp(9), dp(9), dp(9));
		button.setFocusable(true);
		button.setBackground(keyBg(0xFF1C3044, 0xFF315673, 0xFF3E6888));
		return button;
	}

	private LinearLayout.LayoutParams toolbarButtonParams() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(38));
		lp.setMarginStart(dp(4));
		return lp;
	}

	private String actionLabel(CarKeyboardModel.Action action) {
		return switch (action) {
			case SEARCH -> activity.getString(R.string.search);
			case GO -> activity.getString(R.string.car_keyboard_go);
			case DONE -> activity.getString(R.string.done);
			case NEXT -> activity.getString(R.string.next);
			case SEND -> activity.getString(R.string.car_keyboard_send);
			case ENTER -> activity.getString(R.string.car_keyboard_enter);
		};
	}

	private void append(String text) {
		if (target == null) return;
		int selection = CarKeyboardEditor.insert(target.getText(), target.getSelectionStart(),
				target.getSelectionEnd(), text);
		target.setSelection(selection);
		value.setText(displayValue());
	}

	private void delete() {
		if (target == null) return;
		int selection = CarKeyboardEditor.deleteBackward(target.getText(),
				target.getSelectionStart(), target.getSelectionEnd());
		target.setSelection(selection);
		value.setText(displayValue());
	}

	private void paste() {
		Object service = activity.getSystemService(Context.CLIPBOARD_SERVICE);
		if (!(service instanceof ClipboardManager clipboard) || !clipboard.hasPrimaryClip()) return;
		ClipData clip = clipboard.getPrimaryClip();
		if ((clip == null) || (clip.getItemCount() == 0)) return;
		CharSequence text = clip.getItemAt(0).coerceToText(activity);
		if (text != null) append(text.toString());
	}

	private void setText(String text) {
		if (target == null) return;
		target.setText(text);
		target.setSelection(target.getText().length());
		value.setText(displayValue());
	}

	private void performAction() {
		if (target == null) return;
		if (inputAction == CarKeyboardModel.Action.ENTER) {
			append("\n");
			return;
		}
		EditText input = target;
		if (submitOnEnter && activity.submitTextInput(input)) return;
		input.onEditorAction(CarKeyboardModel.editorAction(inputAction));
		if (target == input) activity.stopInput();
	}

	private boolean isPassword(EditText input) {
		return (input != null) && isPasswordInputType(input.getInputType());
	}

	static boolean isPasswordInputType(int type) {
		int inputClass = type & InputType.TYPE_MASK_CLASS;
		int variation = type & InputType.TYPE_MASK_VARIATION;
		if (inputClass == InputType.TYPE_CLASS_TEXT) {
			return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
					variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
					variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
		}
		return (inputClass == InputType.TYPE_CLASS_NUMBER) &&
				(variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
	}

	private CharSequence displayValue() {
		if (target == null) return "";
		CharSequence text = target.getText();
		if (!isPassword(target)) return text;
		return "\u2022".repeat(Character.codePointCount(text, 0, text.length()));
	}

	private int resolveKeyHeight() {
		float density = activity.getResources().getDisplayMetrics().density;
		int screenHeightDp = Math.round(
				activity.getResources().getDisplayMetrics().heightPixels / density);
		return dp(clamp((screenHeightDp - 116) / 5, 46, 60));
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private GradientDrawable panelBg() {
		GradientDrawable drawable = shape(0xFF0E1A27, 0xFF243B52);
		drawable.setCornerRadii(new float[] {dp(8), dp(8), dp(8), dp(8), 0, 0, 0, 0});
		return drawable;
	}

	private GradientDrawable inputBg() {
		return shape(0xFF14263A, 0xFF436887);
	}

	private StateListDrawable keyBg(int normal, int focused, int pressed) {
		StateListDrawable drawable = new StateListDrawable();
		drawable.addState(new int[] {android.R.attr.state_pressed}, shape(pressed, 0xFF75A7C7));
		drawable.addState(new int[] {android.R.attr.state_focused}, shape(focused, 0xFF8CC7EA));
		drawable.addState(StateSet.WILD_CARD, shape(normal, 0xFF42617E));
		return drawable;
	}

	private GradientDrawable shape(int color, int stroke) {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(color);
		drawable.setStroke(dp(1), stroke);
		drawable.setCornerRadius(dp(7));
		return drawable;
	}

	private int dp(int value) {
		float density = activity.getResources().getDisplayMetrics().density;
		return Math.round(value * density);
	}

	private static final class FocusRow {
		final List<FocusKey> keys = new ArrayList<>();
		final float total;

		FocusRow(float total) {
			this.total = total;
		}

		void add(View view, float center) {
			keys.add(new FocusKey(view, center));
		}

		void normalize() {
			for (FocusKey key : keys) key.center /= total;
		}
	}

	private static final class FocusKey {
		final View view;
		float center;

		FocusKey(View view, float center) {
			this.view = view;
			this.center = center;
		}
	}
}
