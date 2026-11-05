package me.app.fermatax.auto.carapps;

/**
 * Flags for VirtualDisplay creation across Android SDK versions. Values are the AOSP
 * DisplayManager.VIRTUAL_DISPLAY_FLAG_* bits (hidden ones included).
 *
 * SDK 28..29 (Android 9..10): 0x014B
 * SDK 30..32 (Android 11..12L): 0x054B (adds TRUSTED)
 * SDK 33 (Android 13 Tiramisu): 0x3D4B (adds OWN_DISPLAY_GROUP, ALWAYS_UNLOCKED, TOUCH_FEEDBACK_DISABLED)
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
	public static final int FLAG_DEVICE_DISPLAY_GROUP = 0x8000;

	// Bits per AOSP DisplayManager.java: TRUSTED from android11, OWN_DISPLAY_GROUP from android12,
	// ALWAYS_UNLOCKED and TOUCH_FEEDBACK_DISABLED from android13, OWN_FOCUS (here) from android14.
	public static final int FLAGS_SDK_28 = FLAG_PUBLIC | FLAG_PRESENTATION | FLAG_OWN_CONTENT_ONLY
			| FLAG_SUPPORTS_TOUCH | FLAG_DESTROY_CONTENT_ON_REMOVAL;
	public static final int FLAGS_SDK_30 = FLAGS_SDK_28 | FLAG_TRUSTED;
	// Android 12 has OWN_DISPLAY_GROUP, but measured: with it the power key turns the phone off without locking it.
	public static final int FLAGS_SDK_33 = FLAGS_SDK_30 | FLAG_OWN_DISPLAY_GROUP | FLAG_ALWAYS_UNLOCKED | FLAG_TOUCH_FEEDBACK_DISABLED;
	public static final int FLAGS_SDK_34_PLUS = FLAGS_SDK_33 | FLAG_OWN_FOCUS;

	private VirtualDisplayFlags() {
	}

	public static int forSdk(int sdk) {
		if (sdk >= 34) {
			return FLAGS_SDK_34_PLUS;
		}
		if (sdk >= 33) {
			return FLAGS_SDK_33;
		}
		if (sdk >= 30) {
			return FLAGS_SDK_30;
		}
		return FLAGS_SDK_28;
	}

	/** Without ADD_TRUSTED_DISPLAY the system refuses a display that asks for either. */
	public static int untrusted(int flags) {
		return flags & ~(FLAG_TRUSTED | FLAG_OWN_DISPLAY_GROUP);
	}

	/** Display.FLAG_TRUSTED as reported by Display.getFlags() (Display.java), not the 1 << 10 request flag. */
	private static final int DISPLAY_FLAG_TRUSTED = 1 << 7;

	/**
	 * True if FLAG_TRUSTED was requested but the created display does not report Display.FLAG_TRUSTED:
	 * without ADD_TRUSTED_DISPLAY the system may drop the flag silently instead of throwing.
	 * Before Android 11 TRUSTED is never requested, so this is always false there.
	 */
	public static boolean trustedDropped(int requestedFlags, int displayFlags) {
		return (requestedFlags & FLAG_TRUSTED) != 0 && (displayFlags & DISPLAY_FLAG_TRUSTED) == 0;
	}

	/** Display.FLAG_OWN_FOCUS as reported by Display.getFlags() (Display.java, android14..16-release), not the 0x4000 request flag. */
	private static final int DISPLAY_FLAG_OWN_FOCUS = 1 << 11;

	/** True if FLAG_OWN_FOCUS was requested but the created display does not report it. Before Android 14 it is never requested. */
	public static boolean focusDropped(int requestedFlags, int displayFlags) {
		return (requestedFlags & FLAG_OWN_FOCUS) != 0 && (displayFlags & DISPLAY_FLAG_OWN_FOCUS) == 0;
	}

	/** Older builders have no screen off timeout, and a plain display already gets its own power group there. */
	public static boolean usesVirtualDevice(int sdk) {
		return sdk >= 36;
	}

	/** Before Android 16 a group has no screen off timeout of its own: unheld, the car display sleeps with the phone. */
	public static boolean needsDisplayWakeLock(int sdk, int displayGroupId) {
		return !usesVirtualDevice(sdk) && (displayGroupId != 0);
	}

	/** Flags for a display of a VirtualDevice, the only kind that gets its own power group on Android 16. */
	public static int forVirtualDevice(int sdk) {
		return (forSdk(sdk) & ~(FLAG_OWN_DISPLAY_GROUP | FLAG_PRESENTATION)) | FLAG_DEVICE_DISPLAY_GROUP;
	}
}
