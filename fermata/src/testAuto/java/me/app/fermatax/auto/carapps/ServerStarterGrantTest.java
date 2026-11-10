package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The pairing's single pm grant may have failed; the start retries it once, inside the one start command. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ServerStarterGrantTest {
	private static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
	private static final String START = "CLASSPATH='/a.apk' nohup app_process / Main 1 2 pkg persist >/dev/null 2>&1";

	@Test
	public void missingPermissionGrantsOnceBeforeTheStartAndAFailedGrantCannotStopIt() {
		Context ctx = RuntimeEnvironment.getApplication();
		shadowOf((Application) ctx).denyPermissions(PERMISSION);

		String cmd = ServerStarter.withGrant(ctx, START);

		String grant = "pm grant " + ctx.getPackageName() + " " + PERMISSION;
		assertTrue(cmd, cmd.startsWith(grant + " >/dev/null 2>&1; "));
		assertTrue("the start command is unchanged and last", cmd.endsWith("; " + START));
		assertEquals("grant once", cmd.indexOf(grant), cmd.lastIndexOf(grant));
		assertEquals("a failing grant must not be a condition", -1, cmd.indexOf("&&"));
	}

	@Test
	public void grantedPermissionLeavesTheCommandAlone() {
		Context ctx = RuntimeEnvironment.getApplication();
		shadowOf((Application) ctx).grantPermissions(PERMISSION);

		assertEquals(START, ServerStarter.withGrant(ctx, START));
	}
}
