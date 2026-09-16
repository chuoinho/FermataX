package me.aap.fermata.ui.control;

/** Owns only the temporary position preview of one phone seek gesture. */
public final class ControlSeekGesture {
	private boolean dragging;
	private long preview = -1L;

	public void begin() {
		dragging = true;
		preview = -1L;
	}

	public void preview(long positionMillis) {
		if (dragging) preview = Math.max(0L, positionMillis);
	}

	public boolean isDragging() {
		return dragging;
	}

	public long finish(boolean valid) {
		long result = dragging && valid ? preview : -1L;
		cancel();
		return result;
	}

	public void cancel() {
		dragging = false;
		preview = -1L;
	}
}
