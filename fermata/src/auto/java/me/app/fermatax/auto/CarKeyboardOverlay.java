package me.app.fermatax.auto;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.END;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils.TruncateAt;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import me.aap.fermata.R;

final class CarKeyboardOverlay {
	private static final String[] LETTER_ROWS = {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
	private static final String[] SYMBOL_ROWS = {":/.?=&", "-_@+#%", "~;,'\"()", "[]{}!$*"};
	private final MainCarActivity activity;
	private final FrameLayout view;
	private final TextView title;
	private final TextView value;
	private final LinearLayout keys;
	private EditText target;
	private boolean submitOnEnter;
	private boolean shift;
	private boolean symbols;

	CarKeyboardOverlay(MainCarActivity activity) {
		this.activity = activity;
		view = new FrameLayout(activity);
		view.setBackgroundColor(0xCC07131F);
		view.setFocusable(true);
		view.setFocusableInTouchMode(true);
		view.setOnClickListener(v -> {
		});

		LinearLayout panel = new LinearLayout(activity);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setPadding(dp(8), dp(8), dp(8), dp(10));
		panel.setBackground(panelBg());
		FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, BOTTOM);
		view.addView(panel, panelLp);

		LinearLayout top = new LinearLayout(activity);
		top.setGravity(CENTER_VERTICAL);
		top.setPadding(0, 0, 0, dp(6));
		panel.addView(top, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

		title = new TextView(activity);
		title.setTextColor(0xFFE2E8F0);
		title.setTextSize(14);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setSingleLine(true);
		title.setEllipsize(TruncateAt.END);
		top.addView(title, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));

		Button cancel = action("Cancel");
		cancel.setOnClickListener(v -> activity.stopInput());
		top.addView(cancel, new LinearLayout.LayoutParams(dp(82), dp(42)));

		value = new TextView(activity);
		value.setGravity(CENTER_VERTICAL | END);
		value.setSingleLine(true);
		value.setEllipsize(TruncateAt.START);
		value.setTextColor(Color.WHITE);
		value.setTextSize(18);
		value.setPadding(dp(10), 0, dp(10), 0);
		value.setBackground(inputBg());
		panel.addView(value, new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));

		keys = new LinearLayout(activity);
		keys.setOrientation(LinearLayout.VERTICAL);
		keys.setPadding(0, dp(8), 0, 0);
		panel.addView(keys, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
	}

	void show(EditText target, boolean submitOnEnter) {
		this.target = target;
		this.submitOnEnter = submitOnEnter;
		shift = false;
		symbols = false;
		CharSequence hint = target.getHint();
		title.setText((hint == null) || (hint.length() == 0) ? "Input" : hint);
		value.setText(displayValue());
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
				ConstraintLayout.LayoutParams clp = new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
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
		if (view.getParent() instanceof ViewGroup parent) parent.removeView(view);
		target = null;
	}

	boolean isShowing() {
		return view.getParent() != null;
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
				done();
				return true;
			}
		}
		int c = event.getUnicodeChar();
		if (c > 0) {
			append(String.valueOf((char) c));
			return true;
		}
		return false;
	}

	private void renderKeys() {
		keys.removeAllViews();
		String[] rows = symbols ? SYMBOL_ROWS : LETTER_ROWS;
		for (String row : rows) addCharRow(row);

		LinearLayout tools = row();
		Button mode = key(symbols ? "ABC" : "#+=");
		mode.setOnClickListener(v -> {
			symbols = !symbols;
			renderKeys();
		});
		tools.addView(mode, new LinearLayout.LayoutParams(0, dp(42), 1.1f));

		Button shiftKey = key(shift ? "SHIFT" : "Shift");
		shiftKey.setOnClickListener(v -> {
			shift = !shift;
			renderKeys();
		});
		tools.addView(shiftKey, new LinearLayout.LayoutParams(0, dp(42), 1.2f));

		Button space = key("Space");
		space.setOnClickListener(v -> append(" "));
		tools.addView(space, new LinearLayout.LayoutParams(0, dp(42), 2.4f));

		Button paste = key("Paste");
		paste.setOnClickListener(v -> paste());
		tools.addView(paste, new LinearLayout.LayoutParams(0, dp(42), 1.2f));

		Button del = key("Del");
		del.setOnClickListener(v -> delete());
		tools.addView(del, new LinearLayout.LayoutParams(0, dp(42), 1.1f));
		keys.addView(tools);

		LinearLayout actions = row();
		Button clear = action("Clear");
		clear.setOnClickListener(v -> setText(""));
		actions.addView(clear, new LinearLayout.LayoutParams(0, dp(44), 1));

		Button ok = primary("OK");
		ok.setOnClickListener(v -> done());
		actions.addView(ok, new LinearLayout.LayoutParams(0, dp(44), 2));
		keys.addView(actions);
	}

	private void addCharRow(String chars) {
		LinearLayout row = row();
		for (int i = 0; i < chars.length(); i++) {
			char c = chars.charAt(i);
			String s = String.valueOf((shift && Character.isLetter(c)) ? Character.toUpperCase(c) : c);
			Button key = key(s);
			key.setOnClickListener(v -> {
				append(((Button) v).getText().toString());
				if (shift && !isPassword(target)) {
					shift = false;
					renderKeys();
				}
			});
			row.addView(key, new LinearLayout.LayoutParams(0, dp(42), 1));
		}
		keys.addView(row);
	}

	private LinearLayout row() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(CENTER);
		row.setPadding(0, 0, 0, dp(5));
		return row;
	}

	private Button key(String text) {
		Button b = new Button(activity);
		b.setAllCaps(false);
		b.setText(text);
		b.setTextSize(text.length() > 1 ? 12 : 16);
		b.setTextColor(0xFFE5EDF7);
		b.setGravity(CENTER);
		b.setPadding(0, 0, 0, 0);
		b.setMinWidth(0);
		b.setMinHeight(0);
		b.setMinimumWidth(0);
		b.setMinimumHeight(0);
		b.setBackground(keyBg(0xFF1F3348));
		return b;
	}

	private Button action(String text) {
		Button b = key(text);
		b.setBackground(keyBg(0xFF334155));
		return b;
	}

	private Button primary(String text) {
		Button b = key(text);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		b.setBackground(keyBg(0xFF0069A8));
		return b;
	}

	private void append(String text) {
		if (target == null) return;
		int len = target.getText().length();
		int start = target.getSelectionStart();
		int end = target.getSelectionEnd();
		if (start < 0) start = len;
		if (end < 0) end = len;
		if (start > end) {
			int tmp = start;
			start = end;
			end = tmp;
		}
		target.getText().replace(start, end, text);
		target.setSelection(start + text.length());
		value.setText(displayValue());
	}

	private void delete() {
		if (target == null) return;
		int len = target.getText().length();
		int start = target.getSelectionStart();
		int end = target.getSelectionEnd();
		if (start < 0) start = len;
		if (end < 0) end = len;
		if (start > end) {
			int tmp = start;
			start = end;
			end = tmp;
		}
		if (start != end) {
			target.getText().delete(start, end);
			target.setSelection(start);
		} else if (start > 0) {
			target.getText().delete(start - 1, start);
			target.setSelection(start - 1);
		}
		value.setText(displayValue());
	}

	private void paste() {
		Object svc = activity.getSystemService(Context.CLIPBOARD_SERVICE);
		if (!(svc instanceof ClipboardManager cm) || !cm.hasPrimaryClip()) return;
		ClipData clip = cm.getPrimaryClip();
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

	private void done() {
		if (target == null) return;
		EditText t = target;
		if (submitOnEnter) t.onEditorAction(EditorInfo.IME_ACTION_DONE);
		if (target == t) activity.stopInput();
	}

	private boolean isPassword(EditText t) {
		return (t != null) && isPasswordInputType(t.getInputType());
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
		return isPassword(target) ? "*".repeat(text.length()) : text;
	}

	private GradientDrawable panelBg() {
		GradientDrawable d = new GradientDrawable();
		d.setColor(0xFF0F1D2B);
		d.setCornerRadii(new float[] {dp(8), dp(8), dp(8), dp(8), 0, 0, 0, 0});
		return d;
	}

	private GradientDrawable inputBg() {
		GradientDrawable d = new GradientDrawable();
		d.setColor(0xFF14263A);
		d.setStroke(dp(1), 0xFF365579);
		d.setCornerRadius(dp(6));
		return d;
	}

	private GradientDrawable keyBg(int color) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(color);
		d.setStroke(dp(1), 0xFF42617E);
		d.setCornerRadius(dp(6));
		return d;
	}

	private int dp(int value) {
		float density = activity.getResources().getDisplayMetrics().density;
		return Math.round(value * density);
	}
}
