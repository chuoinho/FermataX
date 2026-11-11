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
		CarAppsFeature.setAdbPaired(false);
		CarAppsFeature.setBackend(null);
	}

	@Test
	public void pairedAdbMakesTheRowReadyWithoutRoot() {
		CarAppsFeature.setAdbPaired(true);
		for (RootState state : new RootState[]{RootState.ABSENT, RootState.AVAILABLE}) {
			CarAppsReadinessModel model = CarAppsReadinessModel.from(state, CarAppsReadinessModel.CAP_INJECT, 34);
			assertTrue(shown(model));
			assertEquals(R.string.car_apps_readiness_status_ready, model.statusStringRes);
			assertEquals(0, model.actionStringRes);
		}
		assertFalse("Android 11-12 cannot use ADB", shown(CarAppsReadinessModel.from(RootState.ABSENT, 0, 31)));
		assertFalse("12L remains blocked", shown(CarAppsReadinessModel.from(RootState.ABSENT, 0, 32)));
	}

	@Test
	public void setupWithoutRootIsOfferedOnlyOnAndroid13UpWithoutRootOrPairing() {
		for (int sdk : new int[]{33, 34, 36, 37}) {
			CarAppsReadinessModel absent = CarAppsReadinessModel.from(RootState.ABSENT, 0, sdk);
			assertTrue(shown(absent));
			assertTrue(offersAdbSetup(RootState.ABSENT, sdk));
			assertEquals(R.string.car_apps_readiness_status_need_setup, absent.statusStringRes);
			assertTrue(offersAdbSetup(RootState.AVAILABLE, sdk));
			assertFalse(offersAdbSetup(RootState.GRANTED, sdk));
		}
		for (int sdk : new int[]{30, 31, 32}) {
			assertFalse(offersAdbSetup(RootState.ABSENT, sdk));
			assertFalse(offersAdbSetup(RootState.AVAILABLE, sdk));
		}
		assertFalse(offersAdbSetup(null, 34));
		CarAppsFeature.setAdbPaired(true);
		assertFalse(offersAdbSetup(RootState.ABSENT, 34));
		assertFalse(offersAdbSetup(RootState.AVAILABLE, 34));
	}

	/** With the ADB pairing recorded but no start working (authorization revoked), a new pairing must be reachable. */
	@Test
	public void failedAdbStartOffersSettingUpAgain() {
		boolean[] failed = {true};
		CarAppsFeature.setBackend(new CarAppsFeature.Backend() {
			@Override
			public FutureSupplier<Boolean> requestRoot() {
				return Completed.completed(false);
			}

			@Override
			public ActivityFragment createFragment() {
				return null;
			}

			@Override
			public boolean serverFailed() {
				return failed[0];
			}
		});
		CarAppsFeature.setAdbPaired(true);
		for (RootState state : new RootState[]{RootState.ABSENT, RootState.AVAILABLE}) {
			CarAppsReadinessModel model = CarAppsReadinessModel.from(state, CarAppsReadinessModel.CAP_INJECT, 34);
			assertTrue(shown(model));
			assertTrue(setupAgain(state));
			assertEquals(R.string.car_apps_server_error, model.statusStringRes);
		}
		assertFalse("root starts it, not ADB", setupAgain(RootState.GRANTED));
		failed[0] = false;
		assertFalse(setupAgain(RootState.ABSENT));
		assertEquals(R.string.car_apps_readiness_status_ready, CarAppsReadinessModel.from(RootState.ABSENT, 0, 34).statusStringRes);
		CarAppsFeature.setAdbPaired(false);
		failed[0] = true;
		assertFalse("not paired: the first setup is offered", setupAgain(RootState.ABSENT));
	}

	@Test
	public void absentHidesRow() {
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.ABSENT, 0, 31);
		assertFalse(shown(model));
		assertEquals(0, model.statusStringRes);
		assertEquals(0, model.actionStringRes);
		assertEquals(0, model.warningStringRes);

		CarAppsReadinessModel nullStateModel = CarAppsReadinessModel.from(null, 0);
		assertFalse(shown(nullStateModel));
	}

	@Test
	public void availableOffersGrantAction() {
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.AVAILABLE, 0);
		assertTrue(shown(model));
		assertEquals(R.string.car_apps_readiness_status_need_root, model.statusStringRes);
		assertEquals(R.string.car_apps_readiness_action_grant, model.actionStringRes);
		assertEquals(0, model.warningStringRes);

		Context context = RuntimeEnvironment.getApplication();
		assertEquals("Root access required", context.getString(model.statusStringRes));
		assertEquals("Grant root", context.getString(model.actionStringRes));
	}

	@Test
	public void unsupportedNonRootAndroid11And12HideReadinessAndActions() {
		for (int sdk : new int[]{30, 31}) {
			CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.ABSENT, 0, sdk);
			assertFalse("Unsupported non-root settings stay hidden on SDK " + sdk, shown(model));
			assertEquals(0, model.statusStringRes);
			assertEquals(0, model.actionStringRes);
			assertEquals(0, model.warningStringRes);
			assertTrue(model.checks.isEmpty());

			CarAppsReadinessModel available = CarAppsReadinessModel.from(RootState.AVAILABLE, 0, sdk);
			assertTrue(shown(available));
			assertEquals(R.string.car_apps_readiness_status_need_root, available.statusStringRes);
			assertEquals(R.string.car_apps_readiness_action_grant, available.actionStringRes);
			assertEquals(0, available.warningStringRes);
		}
	}

	@Test
	public void approvedScopePreservesRootReadinessAnd12LBlock() {
		for (int sdk : new int[]{30, 31, 33, 34, 35, 36, 37}) {
			CarAppsReadinessModel granted = CarAppsReadinessModel.from(RootState.GRANTED,
					CarAppsReadinessModel.CAP_INJECT, sdk);
			assertEquals(R.string.car_apps_readiness_status_ready, granted.statusStringRes);
			assertEquals(0, granted.actionStringRes);
			assertEquals(0, granted.warningStringRes);
			assertEquals(R.string.car_apps_readiness_warn_xiaomi_inject,
					CarAppsReadinessModel.from(RootState.GRANTED, 0, sdk).warningStringRes);
		}
		assertFalse(shown(CarAppsReadinessModel.from(RootState.ABSENT, 0, 29)));
		for (int sdk : new int[]{29, 33, 34, 35, 36, 37}) {
			assertEquals(0, CarAppsReadinessModel.from(RootState.AVAILABLE, 0, sdk).warningStringRes);
		}
		for (RootState state : RootState.values()) {
			assertFalse("12L remains blocked", shown(CarAppsReadinessModel.from(state, 0, 32)));
		}
		assertFalse(shown(CarAppsReadinessModel.from(null, 0, 30)));
	}

	@Test
	public void grantedWithAllCapabilitiesShowsReady() {
		int allCaps = CarAppsReadinessModel.CAP_INJECT;
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.GRANTED, allCaps);
		assertTrue(shown(model));
		assertEquals(R.string.car_apps_readiness_status_ready, model.statusStringRes);
		assertEquals(0, model.actionStringRes);
		assertEquals(0, model.warningStringRes);

		Context context = RuntimeEnvironment.getApplication();
		assertEquals("Ready", context.getString(model.statusStringRes));
	}

	@Test
	public void grantedWithoutInjectShowsXiaomiHint() {
		CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.GRANTED, 0);
		assertTrue(shown(model));
		assertEquals(R.string.car_apps_readiness_status_ready, model.statusStringRes);
		assertEquals(0, model.actionStringRes);
		assertEquals(R.string.car_apps_readiness_warn_xiaomi_inject, model.warningStringRes);

		Context context = RuntimeEnvironment.getApplication();
		assertEquals("Requires USB debugging (Security settings)", context.getString(model.warningStringRes));

		// When both are missing, inject warning takes priority
		CarAppsReadinessModel bothMissing = CarAppsReadinessModel.from(RootState.GRANTED, 0);
		assertTrue(shown(bothMissing));
		assertEquals(R.string.car_apps_readiness_status_ready, bothMissing.statusStringRes);
		assertEquals(0, bothMissing.actionStringRes);
		assertEquals(R.string.car_apps_readiness_warn_xiaomi_inject, bothMissing.warningStringRes);
	}

	@Test
	public void grantedModelExposesChecksList() {
		int allCaps = CarAppsChecks.CAP_VD | CarAppsChecks.CAP_INJECT | CarAppsChecks.CAP_TRUSTED;
		assertFalse("12L is hidden", shown(CarAppsReadinessModel.from(RootState.GRANTED, allCaps, 32)));
		assertFalse("12L is hidden", shown(CarAppsReadinessModel.from(RootState.AVAILABLE, 0, 32)));
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

		CarAppsReadinessModel absent = CarAppsReadinessModel.from(RootState.ABSENT, 0, 31);
		CarAppsReadinessModel available = CarAppsReadinessModel.from(RootState.AVAILABLE, 0);
		CarAppsReadinessModel granted = CarAppsReadinessModel.from(RootState.GRANTED,
				CarAppsReadinessModel.CAP_INJECT);
		assertFalse(shown(CarAppsReadinessModel.from(RootState.ABSENT, 0, 30)));
		assertTrue(shown(CarAppsReadinessModel.from(RootState.AVAILABLE, 0, 31)));

		assertFalse(shown(absent));
		assertTrue(shown(available));
		assertTrue(shown(granted));
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

	/** Hidden models carry no status. */
	private static boolean shown(CarAppsReadinessModel m) {
		return m.statusStringRes != 0;
	}

	/** The setup without root is offered: its channel, and nothing can run yet. */
	private static boolean offersAdbSetup(RootState state, int sdk) {
		return (PhoneAppsGuide.channel(state, sdk) == PhoneAppsGuide.Channel.ADB)
				&& !CarAppsFeature.canRun(state, CarAppsFeature.isAdbPaired(), sdk);
	}

	/** Fix (pair again) is offered for a failed start of the paired channel. */
	private static boolean setupAgain(RootState state) {
		return PhoneAppsStatus.of(true, true, state, CarAppsFeature.isAdbPaired(), CarAppsFeature.serverFailed(), 34)
				== PhoneAppsStatus.FAILED;
	}
}
