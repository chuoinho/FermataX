package me.app.fermatax.auto.carapps;

/**
 * Caller UID verification policies for car display server and binder provider.
 */
public final class CallerPolicy {

	public static final int UID_ROOT = 0;
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
	 * Provider accepts only calls from root (UID 0) or shell / adb (UID 2000).
	 */
	public static boolean providerAccepts(int callingUid) {
		return callingUid == UID_ROOT || callingUid == UID_SHELL;
	}
}
