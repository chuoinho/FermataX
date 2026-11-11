package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.fermata.ui.carapps.CarAppsReadinessModel;
import me.aap.fermata.ui.carapps.RootState;

public class ReadinessPrefsBuilderTest {
	@Test
	public void phoneAppsReadinessRequiresAutoBuildAndSupportedRootState() {
		CarAppsReadinessModel rooted =
				CarAppsReadinessModel.from(
						RootState.GRANTED, 0, 30);
		assertTrue(ReadinessPrefsBuilder.shouldAddCarAppsRow(true, rooted));
		assertFalse(ReadinessPrefsBuilder.shouldAddCarAppsRow(false, rooted));
		for (int sdk : new int[]{30, 31, 32}) {
			assertFalse(ReadinessPrefsBuilder.shouldAddCarAppsRow(true,
					CarAppsReadinessModel.from(
							RootState.ABSENT, 0, sdk)));
		}
		assertFalse(ReadinessPrefsBuilder.shouldAddCarAppsRow(true,
				CarAppsReadinessModel.from(
						RootState.GRANTED, 0, 32)));
	}

	@Test
	public void phoneAppsReportButtonOnlyWithThePhoneAppsRowOnAuto() {
		CarAppsReadinessModel rooted = CarAppsReadinessModel.from(RootState.GRANTED, 0, 30);
		CarAppsReadinessModel hidden = CarAppsReadinessModel.from(RootState.ABSENT, 0, 31);
		assertTrue(ReadinessPrefsBuilder.shouldAddPhoneAppsReport(true, rooted, true));
		assertFalse("Mobile flavor", ReadinessPrefsBuilder.shouldAddPhoneAppsReport(false, rooted, true));
		assertFalse("no Phone Apps backend", ReadinessPrefsBuilder.shouldAddPhoneAppsReport(true, rooted, false));
		assertFalse("Phone Apps not shown", ReadinessPrefsBuilder.shouldAddPhoneAppsReport(true, hidden, true));
	}

	@Test
	public void readinessIsAvailableOnlyFromThePhoneSettingsHost() {
		assertTrue(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.PHONE));
		assertFalse(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.AA_PROJECTION));
		assertFalse(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.MIRROR));
	}

	/** Playback and Android Auto work without notification permission, so it is optional. */
	@Test
	public void notApplicableChecksAreNotShown() {
		java.util.List<me.aap.fermata.ui.carapps.CarAppsChecks.Check> checks = me.aap.fermata.ui.carapps.CarAppsChecks
				.from(RootState.GRANTED, me.aap.fermata.ui.carapps.CarAppsChecks.CAP_IME, 30);
		for (me.aap.fermata.ui.carapps.CarAppsChecks.Check c : checks) {
			assertEquals(c.id, c.status != me.aap.fermata.ui.carapps.CarAppsChecks.Status.NA, ReadinessPrefsBuilder.isShown(c));
		}
		assertTrue(checks.stream().anyMatch(c -> !ReadinessPrefsBuilder.isShown(c)));
	}

	@Test
	public void notificationsAreOptional() {
		java.util.List<me.aap.fermata.ui.control.PhoneReadiness.Check> required =
				java.util.List.of(ReadinessPrefsBuilder.REQUIRED_CHECKS);
		java.util.List<me.aap.fermata.ui.control.PhoneReadiness.Check> optional =
				java.util.List.of(ReadinessPrefsBuilder.OPTIONAL_CHECKS);
		assertFalse(required.contains(me.aap.fermata.ui.control.PhoneReadiness.Check.NOTIFICATIONS));
		assertTrue(optional.contains(me.aap.fermata.ui.control.PhoneReadiness.Check.NOTIFICATIONS));
		assertTrue(required.contains(me.aap.fermata.ui.control.PhoneReadiness.Check.STORAGE));
		assertTrue(required.contains(me.aap.fermata.ui.control.PhoneReadiness.Check.LOCATION));
	}

	@Test
	public void closedOrInactiveViewsRejectRefreshCallbacks() {
		assertTrue(ReadinessPrefsBuilder.isRefreshAllowed(() -> true, false));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> false, false));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> true, true));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> {
			throw new IllegalStateException("stale view");
		}, false));
	}

	@Test
	public void sectionHeadersUseTheActivityThemeForTheirPrimaryTextColor() throws Exception {
		String source = source("ReadinessPrefsBuilder.java");
		assertTrue(source.contains("Context themeContext = activity.getContext();"));
		assertTrue(source.contains("new TextView(themeContext)"));
		assertTrue(source.contains("themeContext.getTheme().resolveAttribute"));
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		}
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}
}
