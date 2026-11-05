package me.app.fermatax.auto.carapps;

/**
 * Caller UID verification policies for car display server and binder provider.
 */
public final class CallerPolicy {

	public static final int UID_ROOT = 0;
	public static final int UID_SYSTEM = 1000;
	public static final int UID_SHELL = 2000;

	private CallerPolicy() {
	}

	/**
	 * Server accepts only calls originating from the app itself (callingUid == appUid).
	 */
	public static boolean serverAccepts(int callingUid, int appUid) {
		return callingUid == appUid;
	}

	/**
	 * Provider accepts only calls from root (UID 0) or shell / adb (UID 2000), and before Android 13 from the system
	 * uid the server runs as there: see ServerCommand. Platform-signed code of that uid can already do far more.
	 */
	public static boolean providerAccepts(int callingUid, int sdk) {
		return callingUid == UID_ROOT || callingUid == UID_SHELL || (callingUid == UID_SYSTEM && sdk < 33);
	}
}
