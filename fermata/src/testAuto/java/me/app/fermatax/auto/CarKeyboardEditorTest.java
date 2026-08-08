package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;

import android.text.SpannableStringBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CarKeyboardEditorTest {
	@Test
	public void insertsAtCursorAndReplacesSelection() {
		SpannableStringBuilder text = new SpannableStringBuilder("abcd");
		int cursor = CarKeyboardEditor.insert(text, 2, 2, "XY");
		assertEquals("abXYcd", text.toString());
		assertEquals(4, cursor);

		cursor = CarKeyboardEditor.insert(text, 1, 5, "é");
		assertEquals("aéd", text.toString());
		assertEquals(2, cursor);
	}

	@Test
	public void unicodePastePreservesText() {
		SpannableStringBuilder text = new SpannableStringBuilder("Start ");
		String pasted = "Tiếng Việt · Español · Français";
		int cursor = CarKeyboardEditor.insert(text, text.length(), text.length(), pasted);
		assertEquals("Start " + pasted, text.toString());
		assertEquals(text.length(), cursor);
	}

	@Test
	public void backspaceDeletesSelectionOrOneUnicodeCodePoint() {
		SpannableStringBuilder selected = new SpannableStringBuilder("abcdef");
		int cursor = CarKeyboardEditor.deleteBackward(selected, 2, 5);
		assertEquals("abf", selected.toString());
		assertEquals(2, cursor);

		SpannableStringBuilder emoji = new SpannableStringBuilder("A\uD83D\uDE80B");
		cursor = CarKeyboardEditor.deleteBackward(emoji, 3, 3);
		assertEquals("AB", emoji.toString());
		assertEquals(1, cursor);
	}
}
