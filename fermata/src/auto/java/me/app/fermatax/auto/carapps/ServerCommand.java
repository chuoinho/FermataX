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
	 * Runs the server as shell, the only uid that can create a VirtualDevice; plain root if su cannot switch.
	 * Before Android 13 shell has no ADD_TRUSTED_DISPLAY, and apps opened on an untrusted display land on the phone;
	 * root has no package name there, which createVirtualDisplay refuses. So it runs as system.
	 */
	public static String buildForRoot(String apkPath, int appUid, int versionCode, String pkg, int sdk) {
		String cmd = build(apkPath, appUid, versionCode, pkg);
		return "su " + ((sdk < 33) ? CallerPolicy.UID_SYSTEM : CallerPolicy.UID_SHELL) + " -c '" + cmd.replace("'", "'\\''") + "' || " + cmd;
	}
}
