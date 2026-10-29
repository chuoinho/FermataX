package me.app.fermatax.auto.carapps;

/**
 * Flags for VirtualDisplay creation across Android SDK versions.
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
	public static final int FLAG_AUTO_MIRROR = 0x0008;
	public static final int FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 0x0040;
	public static final int FLAG_ROTATES_WITH_CONTENT = 0x0080;
	public static final int FLAG_TRUSTED = 0x0100;
	public static final int FLAG_OWN_CONTENT_ONLY = 0x0200;
	public static final int FLAG_TOUCH_FEEDBACK_DISABLED = 0x0400;
	public static final int FLAG_DESTROY_CONTENT_ON_REMOVAL = 0x0800;
	public static final int FLAG_SHOULD_SHOW_IME = 0x1000;
	public static final int FLAG_STEAL_TOP_FOCUS_DISABLED = 0x2000;
	public static final int FLAG_OWN_FOCUS = 0x4000;

	public static final int FLAGS_SDK_33 = 0x3D4B;
	public static final int FLAGS_SDK_34_PLUS = 0x7D4B;

	private VirtualDisplayFlags() {
	}

	public static int forSdk(int sdk) {
		if (sdk >= 34) {
			return FLAGS_SDK_34_PLUS;
		}
		return FLAGS_SDK_33;
	}
}
