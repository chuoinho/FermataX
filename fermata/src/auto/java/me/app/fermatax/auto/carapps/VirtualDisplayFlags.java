package me.app.fermatax.auto.carapps;

/**
 * Flags for VirtualDisplay creation across Android SDK versions. Values are the AOSP
 * DisplayManager.VIRTUAL_DISPLAY_FLAG_* bits (hidden ones included).
 *
 * SDK 33 (Android 13 Tiramisu): 0x3D4B
 * SDK 34+ (Android 14+): 0x7D4B (adds 0x4000 OWN_FOCUS)
 *
 * Bit 0x80 (ROTATES_WITH_CONTENT) must NEVER be set on any SDK to avoid
 * display rotation races on car displays.
 */
public final class VirtualDisplayFlags {

	public static final int FLAG_PUBLIC = 0x0001;
	public static final int FLAG_PRESENTATION = 0x0002;
	public static final int FLAG_OWN_CONTENT_ONLY = 0x0008;
	public static final int FLAG_SUPPORTS_TOUCH = 0x0040;
	public static final int FLAG_ROTATES_WITH_CONTENT = 0x0080;
	public static final int FLAG_DESTROY_CONTENT_ON_REMOVAL = 0x0100;
	/** Needs ADD_TRUSTED_DISPLAY: root has it, a shell (phase 2) may not and must retry without it. */
	public static final int FLAG_TRUSTED = 0x0400;
	public static final int FLAG_OWN_DISPLAY_GROUP = 0x0800;
	public static final int FLAG_ALWAYS_UNLOCKED = 0x1000;
	public static final int FLAG_TOUCH_FEEDBACK_DISABLED = 0x2000;
	public static final int FLAG_OWN_FOCUS = 0x4000;

	private static final int BASE = FLAG_PUBLIC | FLAG_PRESENTATION | FLAG_OWN_CONTENT_ONLY | FLAG_SUPPORTS_TOUCH
			| FLAG_DESTROY_CONTENT_ON_REMOVAL | FLAG_TRUSTED | FLAG_OWN_DISPLAY_GROUP | FLAG_ALWAYS_UNLOCKED
			| FLAG_TOUCH_FEEDBACK_DISABLED;

	public static final int FLAGS_SDK_33 = BASE;
	public static final int FLAGS_SDK_34_PLUS = BASE | FLAG_OWN_FOCUS;

	private VirtualDisplayFlags() {
	}

	public static int forSdk(int sdk) {
		if (sdk >= 34) {
			return FLAGS_SDK_34_PLUS;
		}
		return FLAGS_SDK_33;
	}
}
