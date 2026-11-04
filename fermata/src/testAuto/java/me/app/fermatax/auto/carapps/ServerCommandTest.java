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
		assertTrue(cmd.startsWith("CLASSPATH="));
		assertTrue(cmd.contains("nohup app_process / me.app.fermatax.auto.carapps.server.Main " + appUid + " " + versionCode + " " + pkg));
		assertTrue(cmd.endsWith(">/dev/null 2>&1 &"));
	}

	@Test
	public void rootStartsTheServerAsShellBecauseOnlyShellCanCreateAVirtualDevice() {
		String plain = ServerCommand.build("/data/app/base.apk", 10150, 21000, "pkg");
		String cmd = ServerCommand.buildForRoot("/data/app/base.apk", 10150, 21000, "pkg");
		assertTrue(cmd.startsWith("su 2000 -c '" + plain.replace("'", "'\\''") + "' || "));
	}

	@Test
	public void rootStillStartsTheServerWhenSuCannotSwitchUser() {
		String plain = ServerCommand.build("/data/app/base.apk", 10150, 21000, "pkg");
		String cmd = ServerCommand.buildForRoot("/data/app/base.apk", 10150, 21000, "pkg");
		assertTrue(cmd.endsWith(" || " + plain));
	}

	@Test
	public void rootCommandSurvivesAQuoteInThePath() {
		String cmd = ServerCommand.buildForRoot("/data/app/foo'bar/base.apk", 10150, 21000, "pkg");
		// one more level of quoting for the inner shell
		assertTrue(cmd.contains("CLASSPATH='\\''/data/app/foo'\\''\\'\\'''\\''bar/base.apk'\\''"));
	}
}
