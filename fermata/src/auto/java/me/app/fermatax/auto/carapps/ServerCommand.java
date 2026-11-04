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
	 * shortcut: a shell server on a phone without VirtualDevice cannot start the dim screen (not exported), fix it with phase 2.
	 */
	public static String buildForRoot(String apkPath, int appUid, int versionCode, String pkg) {
		String cmd = build(apkPath, appUid, versionCode, pkg);
		return "su " + CallerPolicy.UID_SHELL + " -c '" + cmd.replace("'", "'\\''") + "' || " + cmd;
	}
}
