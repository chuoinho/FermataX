package me.app.fermatax.auto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import me.aap.fermata.FermataApplication;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = FermataApplication.class)
public class XposedEventDispatcherServiceTest {

	@Test
	public void onlyAndroidAutoMayRegisterForCarTouches() {
		PackageManager pm = RuntimeEnvironment.getApplication().getPackageManager();
		Shadows.shadowOf(pm).setPackagesForUid(10100, Xposed.AA_PKG);
		Shadows.shadowOf(pm).setPackagesForUid(10200, "com.example.other");

		assertTrue(XposedEventDispatcherService.isAndroidAuto(pm, 10100));
		assertFalse(XposedEventDispatcherService.isAndroidAuto(pm, 10200));
		assertFalse(XposedEventDispatcherService.isAndroidAuto(pm, 10300)); // unknown uid
		assertFalse(XposedEventDispatcherService.isAndroidAuto(pm, -1)); // no sender uid
	}
}
