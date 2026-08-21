package me.aap.fermata.ui.smarttop;

/** Addon-neutral capabilities used to derive the remaining SmartTop controls. */
public record SmartTopCapabilities(boolean canFavorite) {
	public static final SmartTopCapabilities NONE = new SmartTopCapabilities(false);

	public static SmartTopCapabilities current(boolean favorite) {
		return new SmartTopCapabilities(favorite);
	}

	public static SmartTopCapabilities suggestion(boolean favorite) {
		return new SmartTopCapabilities(favorite);
	}
}
