package me.aap.fermata.ui.smarttop;

public enum SmartTopLayoutMode {
	COMPACT(176, 66, 64),
	STANDARD(144, 80, 76),
	EXPANDED(152, 88, 76);

	private final int cardHeightDp;
	private final int artworkSizeDp;
	private final int automotiveTouchTargetDp;

	SmartTopLayoutMode(int cardHeightDp, int artworkSizeDp, int automotiveTouchTargetDp) {
		this.cardHeightDp = cardHeightDp;
		this.artworkSizeDp = artworkSizeDp;
		this.automotiveTouchTargetDp = automotiveTouchTargetDp;
	}

	public int cardHeightDp() {
		return cardHeightDp;
	}

	public int artworkSizeDp() {
		return artworkSizeDp;
	}

	public int automotiveTouchTargetDp() {
		return automotiveTouchTargetDp;
	}
}
