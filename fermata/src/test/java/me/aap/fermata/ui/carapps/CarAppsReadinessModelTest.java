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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.R;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class CarAppsReadinessModelTest {

	@Before
	@After
	public void resetState() {
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setBackend(null);
	}

	@Test
	public void absentHidesRow() {
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.ABSENT, 0);
		assertFalse(model.rowVisible);
		assertEquals(0, model.statusStringRes);
		assertEquals(0, model.actionStringRes);
		assertEquals(0, model.warningStringRes);

		CarAppsReadinessModel nullStateModel = CarAppsReadinessModel.from(null, 0);
		assertFalse(nullStateModel.rowVisible);
	}

	@Test
	public void availableOffersGrantAction() {
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.AVAILABLE, 0);
		assertTrue(model.rowVisible);
		assertEquals(R.string.car_apps_readiness_status_need_root, model.statusStringRes);
		assertEquals(R.string.car_apps_readiness_action_grant, model.actionStringRes);
		assertEquals(0, model.warningStringRes);

		Context context = RuntimeEnvironment.getApplication();
		assertEquals("Root access required", context.getString(model.statusStringRes));
		assertEquals("Grant root", context.getString(model.actionStringRes));
	}

	@Test
	public void android11And12WithoutRootExplainWhyRootIsRequired() {
		for (int sdk : new int[]{30, 31}) {
			CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.ABSENT, 0, sdk);
			assertTrue("An unrooted phone needs an explanation on SDK " + sdk, model.rowVisible);
			assertEquals(R.string.car_apps_readiness_status_need_root, model.statusStringRes);
			assertEquals("No root manager means no grant action", 0, model.actionStringRes);
			assertTrue(model.checks.isEmpty());
			assertKeyboardRestriction(model);

			CarAppsReadinessModel available = CarAppsReadinessModel.from(RootState.AVAILABLE, 0, sdk);
			assertEquals(R.string.car_apps_readiness_action_grant, available.actionStringRes);
			assertKeyboardRestriction(available);
		}
	}

	@Test
	public void keyboardRestrictionDoesNotChangeGrantedRootOrOtherAndroidVersions() {
		for (int sdk : new int[]{30, 31, 33, 34, 35, 36, 37}) {
			CarAppsReadinessModel granted = CarAppsReadinessModel.from(RootState.GRANTED,
					CarAppsReadinessModel.CAP_INJECT, sdk);
			assertEquals(R.string.car_apps_readiness_status_ready, granted.statusStringRes);
			assertEquals(0, granted.actionStringRes);
			assertEquals(0, granted.warningStringRes);
			assertEquals(R.string.car_apps_readiness_warn_xiaomi_inject,
					CarAppsReadinessModel.from(RootState.GRANTED, 0, sdk).warningStringRes);
		}
		for (int sdk : new int[]{29, 33, 34, 35, 36, 37}) {
			assertFalse(CarAppsReadinessModel.from(RootState.ABSENT, 0, sdk).rowVisible);
			assertEquals(0, CarAppsReadinessModel.from(RootState.AVAILABLE, 0, sdk).warningStringRes);
		}
		for (RootState state : RootState.values()) {
			assertFalse("12L remains blocked", CarAppsReadinessModel.from(state, 0, 32).rowVisible);
		}
		assertFalse(CarAppsReadinessModel.from(null, 0, 30).rowVisible);
	}

	private static void assertKeyboardRestriction(CarAppsReadinessModel model) {
		assertTrue("The root-required row needs the keyboard reason", model.warningStringRes != 0);
		assertEquals("Android 11–12 blocks the car keyboard on untrusted displays",
				RuntimeEnvironment.getApplication().getString(model.warningStringRes));
	}

	@Test
	public void grantedWithAllCapabilitiesShowsReady() {
		int allCaps = CarAppsReadinessModel.CAP_INJECT;
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.GRANTED, allCaps);
		assertTrue(model.rowVisible);
		assertEquals(R.string.car_apps_readiness_status_ready, model.statusStringRes);
		assertEquals(0, model.actionStringRes);
		assertEquals(0, model.warningStringRes);

		Context context = RuntimeEnvironment.getApplication();
		assertEquals("Ready", context.getString(model.statusStringRes));
	}

	@Test
	public void grantedWithoutInjectShowsXiaomiHint() {
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.GRANTED, 0);
		assertTrue(model.rowVisible);
		assertEquals(R.string.car_apps_readiness_status_ready, model.statusStringRes);
		assertEquals(0, model.actionStringRes);
		assertEquals(R.string.car_apps_readiness_warn_xiaomi_inject, model.warningStringRes);

		Context context = RuntimeEnvironment.getApplication();
		assertEquals("Requires USB debugging (Security settings)", context.getString(model.warningStringRes));

		// When both are missing, inject warning takes priority
		CarAppsReadinessModel bothMissing = CarAppsReadinessModel.from(RootState.GRANTED, 0);
		assertTrue(bothMissing.rowVisible);
		assertEquals(R.string.car_apps_readiness_status_ready, bothMissing.statusStringRes);
		assertEquals(0, bothMissing.actionStringRes);
		assertEquals(R.string.car_apps_readiness_warn_xiaomi_inject, bothMissing.warningStringRes);
	}

	@Test
	public void grantedModelExposesChecksList() {
		int allCaps = CarAppsChecks.CAP_VD | CarAppsChecks.CAP_INJECT | CarAppsChecks.CAP_TRUSTED;
		assertFalse("12L is hidden", CarAppsReadinessModel.from(RootState.GRANTED, allCaps, 32).rowVisible);
		assertFalse("12L is hidden", CarAppsReadinessModel.from(RootState.AVAILABLE, 0, 32).rowVisible);
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.GRANTED, allCaps, 34);
		org.junit.Assert.assertNotNull(model.checks);
		assertFalse(model.checks.isEmpty());
		assertTrue(model.checks.stream().anyMatch(c -> "inject".equals(c.id) && c.status == CarAppsChecks.Status.OK));
		assertTrue(model.checks.stream().anyMatch(c -> "tasks".equals(c.id) && c.status == CarAppsChecks.Status.WARN));
	}

	@Test
	public void noServerSeenYetShowsNoChecksInsteadOfFalseAlarms() {
		// 0 and the backend's CAP_INJECT default are "unknown": every real server reports CAP_VD
		assertTrue(CarAppsReadinessModel.from(RootState.GRANTED, 0, 34).checks.isEmpty());
		assertTrue(CarAppsReadinessModel.from(RootState.GRANTED, CarAppsChecks.CAP_INJECT, 34).checks.isEmpty());
	}

	@Test
	public void rowNeverTriggersSuAtConstruction() throws Exception {
		AtomicInteger rootRequests = new AtomicInteger();
		CarAppsFeature.setBackend(new CarAppsFeature.Backend() {
			@Override
			public FutureSupplier<Boolean> requestRoot() {
				rootRequests.incrementAndGet();
				return Completed.completed(true);
			}

			@Override
			public ActivityFragment createFragment() {
				return null;
			}
		});

		CarAppsReadinessModel absent = CarAppsReadinessModel.from(RootState.ABSENT, 0);
		CarAppsReadinessModel available = CarAppsReadinessModel.from(RootState.AVAILABLE, 0);
		CarAppsReadinessModel granted = CarAppsReadinessModel.from(RootState.GRANTED,
				CarAppsReadinessModel.CAP_INJECT);
		assertTrue(CarAppsReadinessModel.from(RootState.ABSENT, 0, 30).rowVisible);
		assertTrue(CarAppsReadinessModel.from(RootState.AVAILABLE, 0, 31).rowVisible);

		assertFalse(absent.rowVisible);
		assertTrue(available.rowVisible);
		assertTrue(granted.rowVisible);
		assertEquals("Creating readiness model must never trigger requestRoot()", 0, rootRequests.get());

		// Verify that ReadinessPrefsBuilder source code never executes su directly
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilder.java");
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilder.java");
		}
		if (Files.isRegularFile(file)) {
			String source = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
			assertFalse("Must never invoke su process in ReadinessPrefsBuilder",
					source.contains("exec(\"su") || source.contains("exec(\"/system/bin/su"));
		}
	}
}
