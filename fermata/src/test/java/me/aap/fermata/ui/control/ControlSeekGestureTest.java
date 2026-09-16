package me.aap.fermata.ui.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControlSeekGestureTest {
	@Test
	public void finishConsumesPreviewExactlyOnce() {
		ControlSeekGesture gesture = new ControlSeekGesture();
		gesture.begin();
		gesture.preview(12_000L);

		assertTrue(gesture.isDragging());
		assertEquals(12_000L, gesture.finish(true));
		assertFalse(gesture.isDragging());
		assertEquals(-1L, gesture.finish(true));
	}

	@Test
	public void cancelAndInvalidOwnerNeverCommit() {
		ControlSeekGesture gesture = new ControlSeekGesture();
		gesture.begin();
		gesture.preview(12_000L);
		gesture.cancel();
		assertEquals(-1L, gesture.finish(true));

		gesture.begin();
		gesture.preview(12_000L);
		assertEquals(-1L, gesture.finish(false));
	}

	@Test
	public void previewClampsNegativePosition() {
		ControlSeekGesture gesture = new ControlSeekGesture();
		gesture.begin();
		gesture.preview(-5L);
		assertEquals(0L, gesture.finish(true));
	}
}
