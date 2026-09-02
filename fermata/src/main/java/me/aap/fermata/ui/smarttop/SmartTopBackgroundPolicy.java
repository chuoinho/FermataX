package me.aap.fermata.ui.smarttop;

/** Pure SmartTop background selection rules; performs no I/O or addon lookup. */
public final class SmartTopBackgroundPolicy {
	/** Real LibriVox cached covers are 180px square; the shallow card never shows them 1:1. */
	public static final int MIN_SHORT_EDGE_PX = 180;
	public static final double MIN_ASPECT = 0.8D;
	public static final double MAX_ASPECT = 1.25D;

	private SmartTopBackgroundPolicy() {
	}

	public static SmartTopBackground.Kind select(boolean empty,
			boolean eligibleArtwork, boolean provenAudioAddon) {
		if (empty) return SmartTopBackground.Kind.EMPTY;
		if (eligibleArtwork) return SmartTopBackground.Kind.ARTWORK;
		if (provenAudioAddon) return SmartTopBackground.Kind.AUDIO_SPECTRUM;
		return SmartTopBackground.Kind.SOURCE_FALLBACK;
	}

	public static boolean eligibleDimensions(int width, int height, boolean animated) {
		if (animated || (width <= 0) || (height <= 0)) return false;
		if (Math.min(width, height) < MIN_SHORT_EDGE_PX) return false;
		double aspect = (double) width / (double) height;
		return (aspect >= MIN_ASPECT) && (aspect <= MAX_ASPECT);
	}
}
