package me.app.fermatax.auto;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
import static android.text.InputType.TYPE_TEXT_VARIATION_URI;
import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static android.view.inputmethod.EditorInfo.IME_ACTION_GO;
import static android.view.inputmethod.EditorInfo.IME_ACTION_NEXT;
import static android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;
import static android.view.inputmethod.EditorInfo.IME_ACTION_SEND;
import static android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CarKeyboardModelTest {
	@Test
	public void alphaAndSymbolLayoutsHavePermanentNumberRow() {
		assertEquals("1234567890", CarKeyboardModel.NUMBER_ROW);
		assertArrayEquals(new String[] {"qwertyuiop", "asdfghjkl", "zxcvbnm"},
				CarKeyboardModel.ALPHA_ROWS);
		assertEquals(3, CarKeyboardModel.SYMBOL_ROWS.length);
		for (String row : CarKeyboardModel.SYMBOL_ROWS) assertEquals(10, row.length());

		CarKeyboardModel model = new CarKeyboardModel();
		assertEquals(CarKeyboardModel.Mode.ALPHA, model.getMode());
		model.toggleMode();
		assertEquals(CarKeyboardModel.Mode.SYMBOLS, model.getMode());
		model.toggleMode();
		assertEquals(CarKeyboardModel.Mode.ALPHA, model.getMode());
	}

	@Test
	public void shiftSupportsOneShotAndCapsLock() {
		CarKeyboardModel model = new CarKeyboardModel();
		model.tapShift(1_000);
		assertEquals(CarKeyboardModel.ShiftState.ONCE, model.getShiftState());
		assertEquals("A", model.applyShift('a'));
		model.characterTyped('a');
		assertEquals(CarKeyboardModel.ShiftState.OFF, model.getShiftState());

		model.tapShift(2_000);
		model.tapShift(2_250);
		assertEquals(CarKeyboardModel.ShiftState.CAPS_LOCK, model.getShiftState());
		model.characterTyped('b');
		assertEquals(CarKeyboardModel.ShiftState.CAPS_LOCK, model.getShiftState());
		assertEquals("B", model.applyShift('b'));
		model.tapShift(3_000);
		assertEquals(CarKeyboardModel.ShiftState.OFF, model.getShiftState());
	}

	@Test
	public void actionMappingHonorsImeOptionsAndInputSemantics() {
		assertAction(CarKeyboardModel.Action.SEARCH, IME_ACTION_SEARCH);
		assertAction(CarKeyboardModel.Action.GO, IME_ACTION_GO);
		assertAction(CarKeyboardModel.Action.DONE, IME_ACTION_DONE);
		assertAction(CarKeyboardModel.Action.NEXT, IME_ACTION_NEXT);
		assertAction(CarKeyboardModel.Action.SEND, IME_ACTION_SEND);
		assertEquals(CarKeyboardModel.Action.GO, CarKeyboardModel.resolveAction(
				IME_ACTION_UNSPECIFIED, TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_URI, true, false));
		assertEquals(CarKeyboardModel.Action.ENTER, CarKeyboardModel.resolveAction(
				IME_ACTION_UNSPECIFIED, TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_MULTI_LINE, false, false));
		assertEquals(CarKeyboardModel.Action.DONE, CarKeyboardModel.resolveAction(
				IME_ACTION_UNSPECIFIED, TYPE_CLASS_TEXT, true, true));
	}

	@Test
	public void unicodeBackspaceBoundaryDoesNotSplitSurrogatePair() {
		String value = "A\uD83D\uDE80B";
		assertEquals(1, CarKeyboardModel.previousCodePointStart(value, 3));
		assertEquals(3, CarKeyboardModel.previousCodePointStart(value, 4));
		assertEquals(0, CarKeyboardModel.previousCodePointStart(value, 0));
	}

	private static void assertAction(CarKeyboardModel.Action expected, int imeAction) {
		assertEquals(expected, CarKeyboardModel.resolveAction(
				imeAction, TYPE_CLASS_TEXT, true, false));
		assertEquals(imeAction, CarKeyboardModel.editorAction(expected));
	}
}
