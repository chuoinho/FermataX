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
		return "nohup CLASSPATH='" + escapedPath + "' app_process / "
				+ MAIN_CLASS + " " + appUid + " " + versionCode + " " + pkg
				+ " >/dev/null 2>&1 &";
	}
}
