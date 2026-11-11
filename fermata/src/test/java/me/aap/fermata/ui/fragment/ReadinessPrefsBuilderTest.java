package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.ui.carapps.PhoneAppsStatus;
import me.aap.fermata.ui.control.PhoneReadiness;
import me.aap.fermata.ui.control.PhoneReadiness.Check;
import me.aap.fermata.ui.control.PhoneReadiness.Row;
import me.aap.fermata.ui.fragment.ReadinessPrefsBuilder.Group;
import me.aap.fermata.ui.fragment.ReadinessPrefsBuilder.Item;
import me.aap.fermata.ui.fragment.ReadinessPrefsBuilder.Kind;
import me.aap.fermata.ui.fragment.ReadinessPrefsBuilder.Summary;
import me.aap.fermata.ui.policy.RuntimeHostMode;

public class ReadinessPrefsBuilderTest {

	@Test
	public void everyCheckBelongsToTheGroupOfTheFeatureItServes() {
		assertEquals(Group.PLAYBACK, ReadinessPrefsBuilder.group(Check.STORAGE));
		assertEquals(Group.PLAYBACK, ReadinessPrefsBuilder.group(Check.NOTIFICATIONS));
		assertEquals(Group.PLAYBACK, ReadinessPrefsBuilder.group(Check.MICROPHONE));
		assertEquals(Group.ANDROID_AUTO, ReadinessPrefsBuilder.group(Check.CONNECTION));
		assertEquals(Group.ANDROID_AUTO, ReadinessPrefsBuilder.group(Check.CONTROL_SESSION));
		assertEquals(Group.ANDROID_AUTO, ReadinessPrefsBuilder.group(Check.LOCATION));
		assertEquals(Group.ANDROID_AUTO, ReadinessPrefsBuilder.group(Check.NAVIGATION));
		assertEquals(Group.ANDROID_AUTO, ReadinessPrefsBuilder.group(Check.ACCESSIBILITY));
		assertEquals(Group.ANDROID_AUTO, ReadinessPrefsBuilder.group(Check.OVERLAY));
		assertEquals(Group.ANDROID_AUTO, ReadinessPrefsBuilder.group(Check.SCREEN_CAPTURE));
		assertEquals(Group.STABILITY, ReadinessPrefsBuilder.group(Check.BATTERY));
		for (Check c : Check.values()) assertNotNull(c.name(), ReadinessPrefsBuilder.group(c));
	}

	@Test
	public void allGoodShowsEverythingIsReadyOneSummaryPerGroupAndNoDetails() {
		List<Item> plan = ReadinessPrefsBuilder.plan(rows(input(true, true, true, true, true)), PhoneAppsStatus.READY_ADB);
		assertEquals(Summary.OK, plan.get(0).summary());
		assertEquals(Kind.OVERALL, plan.get(0).kind());
		assertEquals(List.of(Group.PLAYBACK, Group.ANDROID_AUTO, Group.PHONE_APPS, Group.STABILITY), headers(plan));
		for (Group g : new Group[]{Group.PLAYBACK, Group.ANDROID_AUTO, Group.STABILITY}) {
			List<Item> summaries = of(plan, Kind.SUMMARY, g);
			assertEquals(g.name(), 1, summaries.size());
			assertEquals(g.name(), Summary.OK, summaries.get(0).summary());
		}
		assertTrue(of(plan, Kind.DETAIL, null).isEmpty());
	}

	@Test
	public void phoneAppsIsExactlyOneRowAndCountsOnlyWhenItNeedsAction() {
		List<Row> ok = rows(input(true, true, true, true, true));
		for (PhoneAppsStatus s : PhoneAppsStatus.values()) {
			List<Item> plan = ReadinessPrefsBuilder.plan(ok, s);
			List<Item> phoneApps = new ArrayList<>();
			for (Item i : plan) if (i.group() == Group.PHONE_APPS && i.kind() != Kind.HEADER) phoneApps.add(i);
			assertEquals(s.name(), 1, phoneApps.size());
			assertEquals(Kind.PHONE_APPS, phoneApps.get(0).kind());
			Summary expected = s.needsAction ? Summary.ATTENTION : Summary.OK;
			assertEquals(s.name(), expected, phoneApps.get(0).summary());
			assertEquals(s.name(), expected, plan.get(0).summary());
		}
		List<Item> none = ReadinessPrefsBuilder.plan(ok, null);
		assertFalse("Mobile or unsupported: no Phone Apps group", headers(none).contains(Group.PHONE_APPS));
		assertTrue(of(none, Kind.PHONE_APPS, null).isEmpty());
	}

	@Test
	public void aMissingRequiredPermissionShowsItsGroupDetailsAndBreaksEverythingReady() {
		List<Item> plan = ReadinessPrefsBuilder.plan(rows(input(false, true, true, true, true)), null);
		assertEquals(Summary.ATTENTION, plan.get(0).summary());
		assertEquals(Summary.ATTENTION, of(plan, Kind.SUMMARY, Group.PLAYBACK).get(0).summary());
		List<Item> details = of(plan, Kind.DETAIL, null);
		assertEquals(1, details.size());
		assertEquals(Check.STORAGE, details.get(0).row().check());
		assertEquals(Summary.OK, of(plan, Kind.SUMMARY, Group.STABILITY).get(0).summary());
	}

	@Test
	public void optionalItemsOffAreListedButKeepEverythingReady() {
		List<Item> plan = ReadinessPrefsBuilder.plan(rows(input(true, false, true, true, true)), null);
		assertEquals(Summary.OK, plan.get(0).summary());
		assertEquals(Summary.OPTIONAL_OFF, of(plan, Kind.SUMMARY, Group.PLAYBACK).get(0).summary());
		List<Item> details = of(plan, Kind.DETAIL, Group.PLAYBACK);
		assertEquals(1, details.size());
		assertEquals(Check.NOTIFICATIONS, details.get(0).row().check());
	}

	@Test
	public void batteryRestrictedNeedsAttentionInStability() {
		List<Item> plan = ReadinessPrefsBuilder.plan(rows(input(true, true, false, true, true)), null);
		assertEquals(Summary.ATTENTION, plan.get(0).summary());
		assertEquals(Summary.ATTENTION, of(plan, Kind.SUMMARY, Group.STABILITY).get(0).summary());
		assertEquals(Check.BATTERY, of(plan, Kind.DETAIL, Group.STABILITY).get(0).row().check());
	}

	/** Not being in the car is not a problem: the connection is a status line, never a detail or a count. */
	@Test
	public void disconnectedIsAStatusNotAProblem() {
		List<Item> plan = ReadinessPrefsBuilder.plan(rows(input(true, true, true, false, true)), null);
		assertEquals(Summary.OK, plan.get(0).summary());
		assertEquals(1, of(plan, Kind.CONNECTION, Group.ANDROID_AUTO).size());
		assertEquals(Summary.OK, of(plan, Kind.SUMMARY, Group.ANDROID_AUTO).get(0).summary());
	}

	@Test
	public void mobileBuildHasNoConnectionRow() {
		PhoneReadiness.Input in = new PhoneReadiness.Input(false, false, null, false, true, true, false, true, true,
				false, true, true);
		List<Item> plan = ReadinessPrefsBuilder.plan(PhoneReadiness.evaluate(in), null);
		assertTrue(of(plan, Kind.CONNECTION, null).isEmpty());
		assertEquals(Summary.OK, plan.get(0).summary());
	}

	@Test
	public void phoneAppsRowOnlyOnAutoWhereSupported() {
		assertTrue(ReadinessPrefsBuilder.shouldAddPhoneAppsRow(true, true));
		assertFalse("Mobile flavor", ReadinessPrefsBuilder.shouldAddPhoneAppsRow(false, true));
		assertFalse("unsupported (11-12 without root, 12L)", ReadinessPrefsBuilder.shouldAddPhoneAppsRow(true, false));
	}

	@Test
	public void theReportAndTheRootGrantLeftReadiness() throws Exception {
		String source = source("ReadinessPrefsBuilder.java");
		assertFalse(source.contains("car_apps_share_report"));
		assertFalse(source.contains("sharePhoneAppsReport"));
		assertFalse(source.contains("requestRoot"));
		assertFalse("main reaches the setup screen through the backend",
				source.contains("me.app.fermatax.auto.carapps.adb.AdbSetupActivity"));
	}

	@Test
	public void readinessIsAvailableOnlyFromThePhoneSettingsHost() {
		assertTrue(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.PHONE));
		assertFalse(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.AA_PROJECTION));
		assertFalse(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.MIRROR));
	}

	/** Playback and Android Auto work without notification permission, so it is optional. */
	@Test
	public void notificationsAreOptional() {
		List<Check> required = List.of(ReadinessPrefsBuilder.REQUIRED_CHECKS);
		List<Check> optional = List.of(ReadinessPrefsBuilder.OPTIONAL_CHECKS);
		assertFalse(required.contains(Check.NOTIFICATIONS));
		assertTrue(optional.contains(Check.NOTIFICATIONS));
		assertTrue(required.contains(Check.STORAGE));
		assertTrue(required.contains(Check.LOCATION));
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

	/** storage, notifications, battery, connected, location; the rest granted. */
	static PhoneReadiness.Input input(boolean storage, boolean notifications, boolean battery, boolean connected,
			boolean location) {
		return new PhoneReadiness.Input(true, true, connected ? AutomotiveConnectionState.State.CONNECTED :
				AutomotiveConnectionState.State.DISCONNECTED, true, storage, location, true, true, notifications,
				true, battery, true);
	}

	private static List<Row> rows(PhoneReadiness.Input in) {
		return PhoneReadiness.evaluate(in);
	}

	private static List<Group> headers(List<Item> plan) {
		List<Group> out = new ArrayList<>();
		for (Item i : plan) if (i.kind() == Kind.HEADER) out.add(i.group());
		return out;
	}

	private static List<Item> of(List<Item> plan, Kind kind, Group group) {
		List<Item> out = new ArrayList<>();
		for (Item i : plan) if ((i.kind() == kind) && ((group == null) || (i.group() == group))) out.add(i);
		return out;
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
