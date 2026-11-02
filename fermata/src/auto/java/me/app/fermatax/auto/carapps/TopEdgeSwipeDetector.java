package me.app.fermatax.auto.carapps;

import android.view.MotionEvent;

/** Detects a downward swipe that starts inside the top edge strip. */
final class TopEdgeSwipeDetector {
	private final int edgePx;
	private final int thresholdPx;
	private boolean tracking;
	private boolean triggered;

	TopEdgeSwipeDetector(int edgePx, int thresholdPx) {
		this.edgePx = edgePx;
		this.thresholdPx = thresholdPx;
	}

	/** Returns true while the gesture belongs to the top edge strip. */
	boolean onTouch(int action, float x, float y) {
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				tracking = y <= edgePx;
				triggered = false;
				return tracking;
			case MotionEvent.ACTION_MOVE:
				if (tracking && y >= thresholdPx) {
					triggered = true;
					tracking = false;
					return true;
				}
				return tracking;
			default:
				boolean was = tracking;
				tracking = false;
				return was;
		}
	}

	/** True once per gesture, after the swipe passed the threshold. */
	boolean consumeTriggered() {
		boolean t = triggered;
		triggered = false;
		return t;
	}
}
