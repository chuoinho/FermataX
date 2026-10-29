package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServerCommandTest {

	@Test
	public void ServerCommandQuotesPathWithSpaces() {
		String apkPath = "/data/app/with spaces/base.apk";
		String cmd = ServerCommand.build(apkPath, 10123, 42, "me.app.fermataX.auto");
		assertTrue(cmd.contains("CLASSPATH='/data/app/with spaces/base.apk'"));

		String pathWithQuote = "/data/app/foo'bar/base.apk";
		String cmdWithQuote = ServerCommand.build(pathWithQuote, 10123, 42, "me.app.fermataX.auto");
		assertTrue(cmdWithQuote.contains("CLASSPATH='/data/app/foo'\\''bar/base.apk'"));
	}

	@Test
	public void ServerCommandContainsMainClassAndArgs() {
		String apkPath = "/data/app/base.apk";
		int appUid = 10150;
		int versionCode = 21000;
		String pkg = "me.app.fermataX.auto.debug";

		String cmd = ServerCommand.build(apkPath, appUid, versionCode, pkg);
		assertTrue(cmd.startsWith("nohup "));
		assertTrue(cmd.contains("app_process / me.app.fermatax.auto.carapps.server.Main " + appUid + " " + versionCode + " " + pkg));
		assertTrue(cmd.endsWith(">/dev/null 2>&1 &"));
	}
}
