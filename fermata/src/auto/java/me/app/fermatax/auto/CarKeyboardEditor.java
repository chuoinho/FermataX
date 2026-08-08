package me.app.fermatax.auto;

import android.text.Editable;

final class CarKeyboardEditor {
	private CarKeyboardEditor() {
	}

	static int insert(Editable editable, int selectionStart, int selectionEnd,
			CharSequence value) {
		int length = editable.length();
		int start = normalize(selectionStart, length);
		int end = normalize(selectionEnd, length);
		if (start > end) {
			int swap = start;
			start = end;
			end = swap;
		}
		CharSequence insert = (value == null) ? "" : value;
		editable.replace(start, end, insert);
		return start + insert.length();
	}

	static int deleteBackward(Editable editable, int selectionStart, int selectionEnd) {
		int length = editable.length();
		int start = normalize(selectionStart, length);
		int end = normalize(selectionEnd, length);
		if (start > end) {
			int swap = start;
			start = end;
			end = swap;
		}
		if (start != end) {
			editable.delete(start, end);
			return start;
		}
		if (start == 0) return 0;
		int previous = CarKeyboardModel.previousCodePointStart(editable, start);
		editable.delete(previous, start);
		return previous;
	}

	private static int normalize(int selection, int length) {
		return (selection < 0) ? length : Math.min(selection, length);
	}
}
