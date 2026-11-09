package me.app.fermatax.auto.carapps;

/**
 * Builds the shell command to launch the CarDisplayServer process via su / app_process.
 */
public final class ServerCommand {

	private static final String MAIN_CLASS = "me.app.fermatax.auto.carapps.server.Main";

	private ServerCommand() {
	}

	public static String build(String apkPath, int appUid, int versionCode, String pkg) {
		String escapedPath = apkPath.replace("'", "'\\''");
		return "CLASSPATH='" + escapedPath + "' nohup app_process / "
				+ MAIN_CLASS + " " + appUid + " " + versionCode + " " + pkg
				+ " >/dev/null 2>&1 &";
	}

	/**
	 * Over wireless ADB a server backgrounded with & dies when the shell command ends, even with nohup or setsid
	 * (measured). In the foreground it is reparented to init and outlives the session, so the session just closes.
	 */
	public static String buildForAdb(String apkPath, int appUid, int versionCode, String pkg) {
		String escapedPath = apkPath.replace("'", "'\\''");
		return "CLASSPATH='" + escapedPath + "' app_process / "
				+ MAIN_CLASS + " " + appUid + " " + versionCode + " " + pkg + " >/dev/null 2>&1";
	}

	/**
	 * Runs the server as shell, the only uid that can create a VirtualDevice; plain root if su cannot switch.
	 * Before Android 13 shell has no ADD_TRUSTED_DISPLAY, and apps opened on an untrusted display land on the phone;
	 * root has no package name there, which createVirtualDisplay refuses. So it runs as system.
	 * Why su refused goes to logcat: nothing reads the root shell's output.
	 */
	public static String buildForRoot(String apkPath, int appUid, int versionCode, String pkg, int sdk) {
		String cmd = build(apkPath, appUid, versionCode, pkg);
		int uid = (sdk < 33) ? CallerPolicy.UID_SYSTEM : CallerPolicy.UID_SHELL;
		return "e=$(su " + uid + " -c '" + cmd.replace("'", "'\\''") + "' 2>&1) || { log -t CarDisplayServer \"su "
				+ uid + " failed: $e\"; " + cmd + " }";
	}
}
