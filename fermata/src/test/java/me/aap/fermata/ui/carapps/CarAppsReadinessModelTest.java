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
