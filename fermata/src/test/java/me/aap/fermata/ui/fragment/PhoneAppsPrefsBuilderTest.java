package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.R;
import me.aap.fermata.ui.carapps.CarAppsChecks;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.Page;
import me.aap.fermata.ui.carapps.PhoneAppsStatus;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.fermata.ui.policy.RuntimeHostMode;

public class PhoneAppsPrefsBuilderTest {

	@Test
	public void thePageIsInPhoneSettingsOnAutoWhereSupported() {
		assertTrue(PhoneAppsPrefsBuilder.shouldAdd(true, true, RuntimeHostMode.PHONE));
		assertFalse("Mobile flavor", PhoneAppsPrefsBuilder.shouldAdd(false, true, RuntimeHostMode.PHONE));
		assertFalse("unsupported", PhoneAppsPrefsBuilder.shouldAdd(true, false, RuntimeHostMode.PHONE));
		assertFalse("car screen settings", PhoneAppsPrefsBuilder.shouldAdd(true, true, RuntimeHostMode.AA_PROJECTION));
		assertFalse(PhoneAppsPrefsBuilder.shouldAdd(true, true, RuntimeHostMode.MIRROR));
	}

	@Test
	public void theReportButtonNeedsTheAutoBackend() {
		assertTrue(PhoneAppsPrefsBuilder.shouldAddReport(true, true, true, RuntimeHostMode.PHONE));
		assertFalse("Mobile flavor", PhoneAppsPrefsBuilder.shouldAddReport(false, true, true, RuntimeHostMode.PHONE));
		assertFalse("no backend", PhoneAppsPrefsBuilder.shouldAddReport(true, true, false, RuntimeHostMode.PHONE));
		assertFalse("unsupported", PhoneAppsPrefsBuilder.shouldAddReport(true, false, true, RuntimeHostMode.PHONE));
		assertFalse("car screen", PhoneAppsPrefsBuilder.shouldAddReport(true, true, true, RuntimeHostMode.AA_PROJECTION));
	}

	@Test
	public void setUpOrFixOnlyWhenSomethingIsMissing() {
		assertEquals(R.string.car_apps_settings_setup, PhoneAppsPrefsBuilder.primaryAction(PhoneAppsStatus.NEEDS_SETUP));
		assertEquals(R.string.car_apps_settings_setup, PhoneAppsPrefsBuilder.primaryAction(PhoneAppsStatus.NEEDS_ROOT_GRANT));
		assertEquals(R.string.car_apps_settings_fix, PhoneAppsPrefsBuilder.primaryAction(PhoneAppsStatus.FAILED));
		for (PhoneAppsStatus s : new PhoneAppsStatus[]{PhoneAppsStatus.READY_ROOT, PhoneAppsStatus.READY_ADB,
				PhoneAppsStatus.OFF, PhoneAppsStatus.NOT_SUPPORTED}) {
			assertEquals(s.name(), 0, PhoneAppsPrefsBuilder.primaryAction(s));
		}
		assertEquals("Fix pairs again, as Set up again did", Page.SETUP_AGAIN,
				PhoneAppsPrefsBuilder.setupPage(PhoneAppsStatus.FAILED));
		assertEquals(Page.SETUP, PhoneAppsPrefsBuilder.setupPage(PhoneAppsStatus.NEEDS_SETUP));
	}

	@Test
	public void detailsListTheChecksThatSayAnythingOnlyOnceTheServerCanRun() {
		List<CarAppsChecks.Check> checks = CarAppsChecks.from(RootState.GRANTED,
				CarAppsChecks.CAP_VD | CarAppsChecks.CAP_IME, 30);
		List<CarAppsChecks.Check> shown = PhoneAppsPrefsBuilder.detailChecks(true, checks);
		assertTrue(checks.stream().anyMatch(c -> c.status == CarAppsChecks.Status.NA));
		assertTrue(shown.stream().noneMatch(c -> c.status == CarAppsChecks.Status.NA));
		assertEquals(checks.stream().filter(c -> c.status != CarAppsChecks.Status.NA).count(), shown.size());
		assertTrue("empty state when it cannot run", PhoneAppsPrefsBuilder.detailChecks(false, checks).isEmpty());
		assertEquals(R.string.car_apps_settings_details_empty, PhoneAppsPrefsBuilder.detailsEmptyText(true));
		assertEquals("turned off, not 'open it on the car'", R.string.car_apps_turned_off,
				PhoneAppsPrefsBuilder.detailsEmptyText(false));
	}

	/** The enable switch is the car catalog's pref; the report moved to Diagnostics; Readiness opens this page. */
	@Test
	public void settingsWiring() throws Exception {
		String settings = source("SettingsFragment.java");
		String page = source("PhoneAppsPrefsBuilder.java");
		assertTrue(page.contains("CarAppsFeature.PREF_ENABLED"));
		assertTrue(page.contains("CarAppsFeature.syncEnabled"));
		int diagnostics = settings.indexOf("R.string.diagnostics");
		int report = settings.indexOf("R.string.car_apps_share_report");
		int backup = settings.indexOf("R.string.backup_restore");
		assertTrue("the report is a Diagnostics action", diagnostics > 0 && report > diagnostics && report < backup);
		assertTrue(settings.contains("new ReadinessPrefsBuilder(a, pageUiActive, this::openPhoneApps)"));
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		if (!Files.isRegularFile(file)) file = root.resolve("fermata/src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}
}
