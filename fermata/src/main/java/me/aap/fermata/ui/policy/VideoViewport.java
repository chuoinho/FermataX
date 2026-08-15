package me.aap.fermata.ui.policy;

/** Measured bounds available to one video output target. */
public record VideoViewport(int width, int height) {
	public boolean isMeasured() {
		return (width > 0) && (height > 0);
	}
}
