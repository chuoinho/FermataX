package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSettings;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PhoneIdleWatcherTest {

	@Test
	public void idleAfterTheTimeoutOnly() {
		assertFalse(PhoneIdleWatcher.isIdle(10_000, 0, 30_000));
		assertTrue(PhoneIdleWatcher.isIdle(30_000, 0, 30_000));
		assertFalse("timeout 0 or less means never", PhoneIdleWatcher.isIdle(99_000, 0, 0));
	}

	@Test
	public void litPhoneLeftAloneTurnsOffOnceDarkPhoneNever() {
		Context ctx = RuntimeEnvironment.getApplication();
		ShadowSettings.setCanDrawOverlays(true);
		org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.WRITE_SETTINGS);
		assertTrue(Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 3000));
		assertEquals(3000, Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 0));
		AtomicBoolean dark = new AtomicBoolean(true);
		AtomicInteger idle = new AtomicInteger();
		PhoneIdleWatcher w = new PhoneIdleWatcher(new Handler(Looper.getMainLooper()), dark::get, idle::incrementAndGet);
		w.start(ctx);

		ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals("panel off: nothing to count", 0, idle.get());

		dark.set(false);
		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals("the count starts when the screen comes on", 0, idle.get());
		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(1, idle.get());

		w.stop();
		ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals("stopped", 1, idle.get());
	}

	@Test
	public void withoutOverlayPermissionNothingRuns() {
		Context ctx = RuntimeEnvironment.getApplication();
		ShadowSettings.setCanDrawOverlays(false);
		AtomicInteger idle = new AtomicInteger();
		PhoneIdleWatcher w = new PhoneIdleWatcher(new Handler(Looper.getMainLooper()), () -> false, idle::incrementAndGet);
		w.start(ctx);
		ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.MINUTES);
		assertEquals(0, idle.get());
	}
}
