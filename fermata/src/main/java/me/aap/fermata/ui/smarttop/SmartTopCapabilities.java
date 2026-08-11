package me.aap.fermata.ui.smarttop;

/** Addon-neutral capabilities used to derive a bounded SmartTop action set. */
public record SmartTopCapabilities(
		boolean canSkipPrevious,
		boolean canSkipNext,
		boolean canFavorite,
		boolean canOpenContext) {
	public static final SmartTopCapabilities NONE =
			new SmartTopCapabilities(false, false, false, false);

	public static SmartTopCapabilities current(boolean favorite, boolean openContext) {
		return new SmartTopCapabilities(true, true, favorite, openContext);
	}

	public static SmartTopCapabilities suggestion(boolean favorite, boolean openContext) {
		return new SmartTopCapabilities(false, false, favorite, openContext);
	}
}
