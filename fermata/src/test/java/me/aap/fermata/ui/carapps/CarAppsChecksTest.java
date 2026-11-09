package me.aap.fermata.ui.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import me.aap.fermata.R;

public class CarAppsChecksTest {

	private static Map<String, CarAppsChecks.Check> checksMap(RootState root, int caps, int sdk) {
		List<CarAppsChecks.Check> list = CarAppsChecks.from(root, caps, sdk);
		return list.stream().collect(Collectors.toMap(c -> c.id, Function.identity()));
	}

	@Test
	public void constantsMatchExpectedValues() {
		assertEquals(1, CarAppsChecks.CAP_VD);
		assertEquals(2, CarAppsChecks.CAP_TRUSTED);
		assertEquals(4, CarAppsChecks.CAP_INJECT);
		assertEquals(8, CarAppsChecks.CAP_PANEL_POWER);
		assertEquals(16, CarAppsChecks.CAP_IME);
		assertEquals(32, CarAppsChecks.CAP_OWN_POWER_GROUP);
		assertEquals(64, CarAppsChecks.CAP_TASKS);
		assertEquals(128, CarAppsChecks.CAP_FOCUS);
	}

	@Test
	public void allCapabilitiesOnAndroid14ProducesAllOk() {
		int allCaps = CarAppsChecks.CAP_VD | CarAppsChecks.CAP_TRUSTED | CarAppsChecks.CAP_INJECT
				| CarAppsChecks.CAP_IME | CarAppsChecks.CAP_OWN_POWER_GROUP | CarAppsChecks.CAP_TASKS
				| CarAppsChecks.CAP_FOCUS;

		Map<String, CarAppsChecks.Check> checks = checksMap(RootState.GRANTED, allCaps, 34);

		assertEquals(CarAppsChecks.Status.OK, checks.get("channel").status);
		assertNull("a connected server always matches: a mismatch restarts it", checks.get("server_version"));
		assertEquals(CarAppsChecks.Status.OK, checks.get("trusted").status);
		assertEquals(CarAppsChecks.Status.OK, checks.get("inject").status);
		assertEquals(CarAppsChecks.Status.OK, checks.get("ime").status);
		assertEquals(CarAppsChecks.Status.OK, checks.get("tasks").status);
		assertEquals(CarAppsChecks.Status.OK, checks.get("focus").status);
		assertEquals(CarAppsChecks.Status.OK, checks.get("own_power_group").status);
	}

	@Test
	public void panelPowerCheckShownOnlyOnSdk30To35() {
		int withPanel = CarAppsChecks.CAP_PANEL_POWER | CarAppsChecks.CAP_INJECT;
		int withoutPanel = CarAppsChecks.CAP_INJECT;

		// SDK 29: not present
		assertNull(checksMap(RootState.GRANTED, withPanel, 29).get("panel_power"));

		// SDK 30..35 with panel: OK; without panel: WARN with fix
		for (int sdk = 30; sdk <= 35; sdk++) {
			CarAppsChecks.Check okCheck = checksMap(RootState.GRANTED, withPanel, sdk).get("panel_power");
			assertNotNull("SDK " + sdk, okCheck);
			assertEquals("SDK " + sdk, CarAppsChecks.Status.OK, okCheck.status);

			CarAppsChecks.Check warnCheck = checksMap(RootState.GRANTED, withoutPanel, sdk).get("panel_power");
			assertNotNull("SDK " + sdk, warnCheck);
			assertEquals("SDK " + sdk, CarAppsChecks.Status.WARN, warnCheck.status);
			assertEquals("SDK " + sdk, R.string.car_apps_check_panel_power_fix, warnCheck.fixRes);
		}

		// SDK 36: not present
		assertNull(checksMap(RootState.GRANTED, withPanel, 36).get("panel_power"));
	}

	@Test
	public void trustedMissingOnSdk30ProducesWarnWithFix() {
		int caps = CarAppsChecks.CAP_INJECT | CarAppsChecks.CAP_IME | CarAppsChecks.CAP_TASKS;
		Map<String, CarAppsChecks.Check> checks = checksMap(RootState.GRANTED, caps, 30);

		CarAppsChecks.Check trusted = checks.get("trusted");
		assertNotNull(trusted);
		assertEquals(CarAppsChecks.Status.WARN, trusted.status);
		assertEquals(R.string.car_apps_check_trusted_missing, trusted.summaryRes);
		assertEquals(R.string.car_apps_check_trusted_fix, trusted.fixRes);
	}

	@Test
	public void trustedIsNotApplicableBeforeAndroid11EvenIfTheServerAssumesIt() {
		for (int sdk = 28; sdk <= 29; sdk++) {
			for (int caps : new int[]{CarAppsChecks.CAP_INJECT, CarAppsChecks.CAP_INJECT | CarAppsChecks.CAP_TRUSTED}) {
				CarAppsChecks.Check trusted = checksMap(RootState.GRANTED, caps, sdk).get("trusted");
				assertNotNull(trusted);
				assertEquals("SDK " + sdk, CarAppsChecks.Status.NA, trusted.status);
				assertEquals(0, trusted.fixRes);
			}
		}
	}

	@Test
	public void injectMissingProducesFailWithXiaomiFix() {
		int caps = CarAppsChecks.CAP_TRUSTED | CarAppsChecks.CAP_IME | CarAppsChecks.CAP_TASKS;
		Map<String, CarAppsChecks.Check> checks = checksMap(RootState.GRANTED, caps, 34);

		CarAppsChecks.Check inject = checks.get("inject");
		assertNotNull(inject);
		assertEquals(CarAppsChecks.Status.FAIL, inject.status);
		assertEquals(R.string.car_apps_check_inject_missing, inject.summaryRes);
		assertEquals(R.string.car_apps_check_inject_fix, inject.fixRes);
	}

	@Test
	public void tasksMissingProducesWarnWithFix() {
		int caps = CarAppsChecks.CAP_INJECT | CarAppsChecks.CAP_TRUSTED;
		Map<String, CarAppsChecks.Check> checks = checksMap(RootState.GRANTED, caps, 34);

		CarAppsChecks.Check tasks = checks.get("tasks");
		assertNotNull(tasks);
		assertEquals(CarAppsChecks.Status.WARN, tasks.status);
		assertEquals(R.string.car_apps_check_tasks_missing, tasks.summaryRes);
		assertEquals(R.string.car_apps_check_tasks_fix, tasks.fixRes);
	}

	@Test
	public void focusMissingOnSdk34ProducesWarn() {
		int caps = CarAppsChecks.CAP_INJECT | CarAppsChecks.CAP_TRUSTED | CarAppsChecks.CAP_TASKS;
		Map<String, CarAppsChecks.Check> checks = checksMap(RootState.GRANTED, caps, 34);

		CarAppsChecks.Check focus = checks.get("focus");
		assertNotNull(focus);
		assertEquals(CarAppsChecks.Status.WARN, focus.status);
		assertEquals(R.string.car_apps_check_focus_missing, focus.summaryRes);
		assertEquals(R.string.car_apps_check_focus_fix, focus.fixRes);
	}

	@Test
	public void focusMissingOnSdk33ProducesNa() {
		int caps = CarAppsChecks.CAP_INJECT | CarAppsChecks.CAP_TRUSTED | CarAppsChecks.CAP_TASKS;
		Map<String, CarAppsChecks.Check> checks = checksMap(RootState.GRANTED, caps, 33);

		CarAppsChecks.Check focus = checks.get("focus");
		assertNotNull(focus);
		assertEquals(CarAppsChecks.Status.NA, focus.status);
		assertEquals(0, focus.fixRes);
	}

	@Test
	public void pairedAdbIsAnOkChannelWithoutRoot() {
		CarAppsFeature.setAdbPaired(true);
		try {
			CarAppsChecks.Check absent = checksMap(RootState.ABSENT, 0, 34).get("channel");
			assertEquals(CarAppsChecks.Status.OK, absent.status);
			assertEquals(R.string.car_apps_check_channel_adb, absent.summaryRes);
			assertEquals(CarAppsChecks.Status.OK, checksMap(RootState.AVAILABLE, 0, 34).get("channel").status);
			assertEquals(R.string.car_apps_check_channel_root, checksMap(RootState.GRANTED, 0, 34).get("channel").summaryRes);
			assertEquals(CarAppsChecks.Status.FAIL, checksMap(RootState.ABSENT, 0, 31).get("channel").status);
		} finally {
			CarAppsFeature.setAdbPaired(false);
		}
	}

	@Test
	public void channelReflectsRootState() {
		int caps = CarAppsChecks.CAP_INJECT;
		Map<String, CarAppsChecks.Check> granted = checksMap(RootState.GRANTED, caps, 34);
		assertEquals(CarAppsChecks.Status.OK, granted.get("channel").status);

		Map<String, CarAppsChecks.Check> available = checksMap(RootState.AVAILABLE, caps, 34);
		assertEquals(CarAppsChecks.Status.WARN, available.get("channel").status);

		Map<String, CarAppsChecks.Check> absent = checksMap(RootState.ABSENT, caps, 34);
		assertEquals(CarAppsChecks.Status.FAIL, absent.get("channel").status);
	}
}
