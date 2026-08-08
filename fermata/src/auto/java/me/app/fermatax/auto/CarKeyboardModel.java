package me.app.fermatax.auto;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

final class CarKeyboardModel {
	static final String NUMBER_ROW = "1234567890";
	static final String[] ALPHA_ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
	static final String[] SYMBOL_ROWS = {"@#$%&-+()/", "*\"':;!?_=\\", "<>[]{}~`^|"};
	private static final long CAPS_LOCK_INTERVAL_MS = 350;

	enum Mode {ALPHA, SYMBOLS}
	enum ShiftState {OFF, ONCE, CAPS_LOCK}
	enum Action {SEARCH, GO, DONE, NEXT, SEND, ENTER}

	private Mode mode = Mode.ALPHA;
	private ShiftState shiftState = ShiftState.OFF;
	private long lastShiftTap;

	Mode getMode() {
		return mode;
	}

	ShiftState getShiftState() {
		return shiftState;
	}

	void reset() {
		mode = Mode.ALPHA;
		shiftState = ShiftState.OFF;
		lastShiftTap = 0;
	}

	void toggleMode() {
		mode = (mode == Mode.ALPHA) ? Mode.SYMBOLS : Mode.ALPHA;
		shiftState = ShiftState.OFF;
	}

	void tapShift(long eventTime) {
		if (shiftState == ShiftState.CAPS_LOCK) {
			shiftState = ShiftState.OFF;
		} else if ((shiftState == ShiftState.ONCE) &&
				(eventTime - lastShiftTap <= CAPS_LOCK_INTERVAL_MS)) {
			shiftState = ShiftState.CAPS_LOCK;
		} else {
			shiftState = (shiftState == ShiftState.OFF) ? ShiftState.ONCE : ShiftState.OFF;
		}
		lastShiftTap = eventTime;
	}

	String applyShift(char value) {
		if (!Character.isLetter(value) || (shiftState == ShiftState.OFF)) {
			return String.valueOf(value);
		}
		return String.valueOf(Character.toUpperCase(value));
	}

	void characterTyped(char value) {
		if (Character.isLetter(value) && (shiftState == ShiftState.ONCE)) {
			shiftState = ShiftState.OFF;
		}
	}

	static Action resolveAction(int imeOptions, int inputType, boolean singleLine,
			boolean submitOnEnter) {
		switch (imeOptions & EditorInfo.IME_MASK_ACTION) {
			case EditorInfo.IME_ACTION_SEARCH: return Action.SEARCH;
			case EditorInfo.IME_ACTION_GO: return Action.GO;
			case EditorInfo.IME_ACTION_DONE: return Action.DONE;
			case EditorInfo.IME_ACTION_NEXT: return Action.NEXT;
			case EditorInfo.IME_ACTION_SEND: return Action.SEND;
		}
		boolean multiline = !singleLine ||
				((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0);
		if (multiline && !submitOnEnter) return Action.ENTER;
		if ((inputType & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_URI) {
			return Action.GO;
		}
		return Action.DONE;
	}

	static int editorAction(Action action) {
		return switch (action) {
			case SEARCH -> EditorInfo.IME_ACTION_SEARCH;
			case GO -> EditorInfo.IME_ACTION_GO;
			case DONE -> EditorInfo.IME_ACTION_DONE;
			case NEXT -> EditorInfo.IME_ACTION_NEXT;
			case SEND -> EditorInfo.IME_ACTION_SEND;
			case ENTER -> EditorInfo.IME_ACTION_NONE;
		};
	}

	static int previousCodePointStart(CharSequence text, int cursor) {
		if ((text == null) || (cursor <= 0)) return 0;
		int previous = cursor - 1;
		if ((previous > 0) && Character.isLowSurrogate(text.charAt(previous)) &&
				Character.isHighSurrogate(text.charAt(previous - 1))) {
			previous--;
		}
		return previous;
	}
}
