package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;

public class TopEdgeSwipeDetectorTest {
	private final TopEdgeSwipeDetector d = new TopEdgeSwipeDetector(24, 48);

	@Test
	public void swipeDownFromTopEdgeTriggers() {
		assertTrue(d.onTouch(MotionEvent.ACTION_DOWN, 100, 5));
		assertTrue(d.onTouch(MotionEvent.ACTION_MOVE, 100, 30));
		assertTrue(d.onTouch(MotionEvent.ACTION_MOVE, 100, 60) && d.consumeTriggered());
	}

	@Test
	public void touchStartingBelowEdgeIsNotOurs() {
		assertFalse(d.onTouch(MotionEvent.ACTION_DOWN, 100, 200));
		assertFalse(d.onTouch(MotionEvent.ACTION_MOVE, 100, 400));
		assertFalse(d.consumeTriggered());
	}

	@Test
	public void shortDragDoesNotTrigger() {
		d.onTouch(MotionEvent.ACTION_DOWN, 100, 5);
		d.onTouch(MotionEvent.ACTION_MOVE, 100, 20);
		d.onTouch(MotionEvent.ACTION_UP, 100, 20);
		assertFalse(d.consumeTriggered());
	}

	@Test
	public void triggersOnlyOncePerGesture() {
		d.onTouch(MotionEvent.ACTION_DOWN, 100, 5);
		d.onTouch(MotionEvent.ACTION_MOVE, 100, 70);
		assertTrue(d.consumeTriggered());
		d.onTouch(MotionEvent.ACTION_MOVE, 100, 90);
		assertFalse(d.consumeTriggered());
	}
}
