package me.aap.fermata.ui.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class RootProbeTest {

	private Context context;

	@Before
	public void setUp() {
		context = RuntimeEnvironment.getApplication();
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit().clear().commit();
		CarAppsFeature.setState(RootState.ABSENT);
	}

	@After
	public void tearDown() {
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit().clear().commit();
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setAdbPaired(false);
	}

	@Test
	public void absentWhenNoSuFileAndNoManager() {
		RootState state = RootProbe.detect(
				path -> false,
				pkg -> false,
				false
		);
		assertEquals(RootState.ABSENT, state);
	}

	@Test
	public void availableWhenSuFileExists() {
		for (String suPath : RootProbe.SU_PATHS) {
			RootState state = RootProbe.detect(
					path -> path.equals(suPath),
					pkg -> false,
					false
			);
			assertEquals("Expected AVAILABLE when " + suPath + " exists", RootState.AVAILABLE, state);
		}
	}

	@Test
	public void availableWhenOnlyManagerInstalled() {
		for (String managerPkg : RootProbe.MANAGER_PACKAGES) {
			RootState state = RootProbe.detect(
					path -> false,
					pkg -> pkg.equals(managerPkg),
					false
			);
			assertEquals("Expected AVAILABLE when manager " + managerPkg + " is installed", RootState.AVAILABLE, state);
		}
	}

	@Test
	public void grantedWhenGrantedBeforeAndSuPresent() {
		RootState state = RootProbe.detect(
				"/system/bin/su"::equals,
				pkg -> false,
				true
		);
		assertEquals(RootState.GRANTED, state);
	}

	@Test
	public void grantedWhenGrantedBeforeAndOnlyManagerInstalled() {
		RootState state = RootProbe.detect(
				path -> false,
				"com.topjohnwu.magisk"::equals,
				true
		);
		assertEquals(RootState.GRANTED, state);
	}

	/** Root hidden from the app (no su file, no manager seen) but a grant worked: that is proof of root. */
	@Test
	public void grantedWhenGrantedBeforeEvenWithRootHidden() {
		RootState state = RootProbe.detect(
				path -> false,
				pkg -> false,
				true
		);
		assertEquals(RootState.GRANTED, state);
	}

	@Test
	public void constantsVerifyExpectedEntries() {
		Set<String> suPaths = new HashSet<>(Arrays.asList(RootProbe.SU_PATHS));
		assertTrue(suPaths.contains("/system/bin/su"));
		assertTrue(suPaths.contains("/system/xbin/su"));
		assertTrue(suPaths.contains("/sbin/su"));
		assertTrue(suPaths.contains("/product/bin/su"));
		assertTrue(suPaths.contains("/vendor/bin/su"));
		assertTrue("Magisk on Android 12 images without the Magisk app", suPaths.contains("/system_ext/bin/su"));
		assertTrue(suPaths.contains("/debug_ramdisk/su"));

		Set<String> managers = new HashSet<>(Arrays.asList(RootProbe.MANAGER_PACKAGES));
		assertTrue(managers.contains("com.topjohnwu.magisk"));
		assertTrue(managers.contains("me.weishu.kernelsu"));
		assertTrue(managers.contains("me.bmax.apatch"));

		assertEquals("CAR_APPS_ROOT_GRANTED", RootProbe.PREF_ROOT_GRANTED);
	}

	@Test
	public void refreshOnCleanContextYieldsAbsent() {
		RootProbe.refresh(context);
		assertEquals(RootState.ABSENT, CarAppsFeature.state());
	}

	@Test
	public void refreshWithNullContextSetsAbsent() {
		CarAppsFeature.setState(RootState.AVAILABLE);
		RootProbe.refresh(null);
		assertEquals(RootState.ABSENT, CarAppsFeature.state());
	}

	@Test
	public void recordGrantedPersistsPreferenceAndUpdatesState() {
		RootProbe.recordGranted(context, true);
		assertTrue(context.getSharedPreferences("fermata", Context.MODE_PRIVATE)
				.getBoolean(RootProbe.PREF_ROOT_GRANTED, false));

		// No su file nor manager here, as with hidden root: the grant itself counts
		assertEquals(RootState.GRANTED, CarAppsFeature.state());

		RootProbe.recordGranted(context, false);
		assertFalse(context.getSharedPreferences("fermata", Context.MODE_PRIVATE)
				.getBoolean(RootProbe.PREF_ROOT_GRANTED, true));
		assertEquals("a failed start clears it", RootState.ABSENT, CarAppsFeature.state());
	}

	@Test
	public void adbPairedFlagSurvivesProcessDeathAndRevocationClearsIt() {
		RootProbe.recordAdbPaired(context, true);
		assertTrue(CarAppsFeature.isAdbPaired());
		assertTrue(context.getSharedPreferences("fermata", Context.MODE_PRIVATE).getBoolean("CAR_APPS_ADB_PAIRED", false));

		CarAppsFeature.setAdbPaired(false); // a new process starts with nothing in memory
		RootProbe.restoreAdbPaired(context);
		assertTrue(CarAppsFeature.isAdbPaired());

		RootProbe.recordAdbPaired(context, false);
		assertFalse(CarAppsFeature.isAdbPaired());
		RootProbe.restoreAdbPaired(context);
		assertFalse("A revoked authorization must not come back", CarAppsFeature.isAdbPaired());
	}
}
