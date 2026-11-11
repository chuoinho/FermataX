package me.app.fermatax.auto.carapps.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.Channel;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.Page;
import me.aap.fermata.ui.carapps.RootProbe;
import me.aap.fermata.ui.carapps.RootState;

/** The one guide screen: channel-aware setup steps, then How to use; on the phone, never asking for a permission. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class PhoneAppsGuideScreenTest {
	private Context ctx;

	@Before
	public void setUp() {
		ctx = RuntimeEnvironment.getApplication();
		AdbPairing.resetForTesting();
		CarAppsFeature.setAdbPaired(false);
		CarAppsFeature.setState(RootState.ABSENT);
	}

	@After
	public void tearDown() {
		AdbPairing.resetForTesting();
		CarAppsFeature.setAdbPaired(false);
		CarAppsFeature.setState(RootState.ABSENT);
	}

	@Test
	public void rootChannelShowsTheRootStepsWithGrantRootAsTheCurrentAction() {
		CarAppsFeature.setState(RootState.AVAILABLE);
		AdbSetupActivity a = open(Page.SETUP);
		View root = a.getWindow().getDecorView();
		assertNotNull(findDescribed(root, a.getString(R.string.car_apps_step_current, a.getString(R.string.car_apps_step_allow_root))));
		assertNotNull(findDescribed(root, a.getString(R.string.car_apps_step_todo, a.getString(R.string.car_apps_step_connect_car))));
		assertNotNull(findDescribed(root, a.getString(R.string.car_apps_step_todo, a.getString(R.string.car_apps_step_open_on_car))));
		assertNotNull(findButton(root, a.getString(R.string.car_apps_grant_root)));
		assertNull("no pairing for root", findButton(root, a.getString(R.string.car_apps_setup_open_dev)));
		assertNull("no permission prompt", shadowOf(a).getLastRequestedPermission());
	}

	@Test
	public void rootGrantedMarksTheFirstStepDoneAndContinuesToHowToUse() {
		CarAppsFeature.setState(RootState.GRANTED);
		AdbSetupActivity a = open(Page.SETUP);
		View root = a.getWindow().getDecorView();
		assertNotNull(findDescribed(root, a.getString(R.string.car_apps_step_done, a.getString(R.string.car_apps_step_allow_root))));
		assertNotNull(findDescribed(root, a.getString(R.string.car_apps_step_current, a.getString(R.string.car_apps_step_connect_car))));
		Button next = findButton(root, a.getString(R.string.car_apps_settings_how_to));
		assertNotNull(next);
		next.performClick();
		assertTrue(textOf(root).contains(a.getString(R.string.car_apps_guide_keyboard_title)));
		assertNotNull(findButton(root, a.getString(R.string.car_apps_guide_got_it)));
	}

	@Test
	public void adbChannelShowsWhichPairingStepIsCurrentAndTheOneTimeWifiNote() {
		Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
		Settings.Global.putInt(ctx.getContentResolver(), "adb_wifi_enabled", 0);
		AdbSetupActivity a = open(Page.SETUP);
		View root = a.getWindow().getDecorView();
		assertNotNull(findDescribed(root, a.getString(R.string.car_apps_step_done, a.getString(R.string.car_apps_step_dev_options))));
		assertNotNull(findDescribed(root, a.getString(R.string.car_apps_step_current,
				a.getString(R.string.car_apps_step_wireless_debugging))));
		assertNotNull(findDescribed(root, a.getString(R.string.car_apps_step_todo, a.getString(R.string.car_apps_step_type_code))));
		assertTrue(textOf(root).contains(a.getString(R.string.car_apps_setup_adb_note)));
		assertNotNull(findButton(root, a.getString(R.string.car_apps_setup_open_dev)));
	}

	@Test
	public void setupThenUsageEndsOnTheUsageGuideOncePaired() {
		CarAppsFeature.setAdbPaired(true);
		AdbSetupActivity a = open(Page.SETUP_THEN_USAGE);
		View root = a.getWindow().getDecorView();
		assertTrue(textOf(root).contains(a.getString(R.string.car_apps_setup_done)));
		findButton(root, a.getString(R.string.car_apps_settings_how_to)).performClick();
		assertTrue(textOf(root).contains(a.getString(R.string.car_apps_guide_title)));
		findButton(root, a.getString(R.string.car_apps_guide_got_it)).performClick();
		assertTrue(a.isFinishing());
	}

	@Test
	@Config(sdk = 31)
	public void android12WithoutRootIsNotSupported() {
		AdbSetupActivity a = open(Page.SETUP);
		View root = a.getWindow().getDecorView();
		assertTrue(textOf(root).contains(a.getString(R.string.car_apps_status_unsupported)));
		assertEquals("nothing is posted", AdbPairing.State.IDLE, AdbPairing.state());
	}

	@Test
	public void usageGuideSectionsPerChannelAndAndroidVersion() {
		int[] adb15 = AdbSetupActivity.usageSections(Channel.ADB, 35);
		int[] root15 = AdbSetupActivity.usageSections(Channel.ROOT, 35);
		int[] adb16 = AdbSetupActivity.usageSections(Channel.ADB, 36);
		assertTrue(contains(adb15, R.string.car_apps_guide_wifi_body));
		assertFalse("root needs no Wi-Fi", contains(root15, R.string.car_apps_guide_wifi_body));
		assertTrue(contains(adb15, R.string.car_apps_guide_screen_body_dark));
		assertFalse(contains(adb15, R.string.car_apps_guide_screen_body_sleep));
		assertTrue(contains(adb16, R.string.car_apps_guide_screen_body_sleep));
		assertFalse(contains(adb16, R.string.car_apps_guide_screen_body_dark));
		for (int[] s : new int[][]{adb15, root15, adb16}) {
			assertEquals("title and body pairs", 0, s.length % 2);
			assertEquals(R.string.car_apps_guide_open_title, s[0]);
			assertTrue(contains(s, R.string.car_apps_guide_buttons_body));
			assertTrue(contains(s, R.string.car_apps_guide_off_body));
			assertEquals(R.string.car_apps_guide_report_body, s[s.length - 1]);
		}
	}

	@Test
	public void usagePageHasHeadingsForTalkBack() {
		AdbSetupActivity a = open(Page.USAGE);
		View root = a.getWindow().getDecorView();
		List<TextView> headings = new ArrayList<>();
		collectHeadings(root, headings);
		assertTrue(headings.size() >= 7);
		Button gotIt = findButton(root, a.getString(R.string.car_apps_guide_got_it));
		assertTrue(gotIt.getMinHeight() >= Math.round(48 * a.getResources().getDisplayMetrics().density));
		assertEquals("no pairing notification from the usage page", AdbPairing.State.IDLE, AdbPairing.state());
	}

	@Test
	public void setUpAgainForgetsThePairingAndOpensTheSetupPage() {
		RootProbe.recordAdbPaired(ctx, true);
		CarAppsFeature.backend().openGuide(ctx, Page.SETUP_AGAIN);
		Intent i = shadowOf((android.app.Application) ctx).getNextStartedActivity();
		assertFalse(CarAppsFeature.isAdbPaired());
		assertEquals(AdbSetupActivity.class.getName(), i.getComponent().getClassName());
		assertEquals(Page.SETUP.name(), i.getStringExtra(AdbSetupActivity.EXTRA_PAGE));
		assertTrue("started from the application context", (i.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
		assertArrayEquals(new Page[]{Page.USAGE}, new Page[]{AdbSetupActivity.page(
				AdbSetupActivity.intent(ctx, Page.USAGE))});
		assertEquals("unknown extra falls back to setup", Page.SETUP, AdbSetupActivity.page(new Intent()));
	}

	private AdbSetupActivity open(Page page) {
		Intent i = new Intent(ctx, AdbSetupActivity.class).putExtra(AdbSetupActivity.EXTRA_PAGE, page.name());
		return Robolectric.buildActivity(AdbSetupActivity.class, i).setup().get();
	}

	private static boolean contains(int[] a, int v) {
		for (int x : a) if (x == v) return true;
		return false;
	}

	private static void collectHeadings(View v, List<TextView> out) {
		if ((v instanceof TextView t) && t.isAccessibilityHeading() && (t.getVisibility() == View.VISIBLE)) out.add(t);
		if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) collectHeadings(g.getChildAt(i), out);
	}

	private static View findDescribed(View v, String description) {
		if ((v.getVisibility() == View.VISIBLE) && (v.getContentDescription() != null)
				&& description.contentEquals(v.getContentDescription())) return v;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				View f = findDescribed(g.getChildAt(i), description);
				if (f != null) return f;
			}
		}
		return null;
	}

	private static Button findButton(View v, String text) {
		if ((v instanceof Button b) && text.contentEquals(b.getText()) && (b.getVisibility() == View.VISIBLE)) return b;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				Button b = findButton(g.getChildAt(i), text);
				if (b != null) return b;
			}
		}
		return null;
	}

	private static String textOf(View v) {
		if (v.getVisibility() != View.VISIBLE) return "";
		StringBuilder sb = new StringBuilder();
		if (v instanceof TextView t) sb.append(t.getText()).append('\n');
		if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) sb.append(textOf(g.getChildAt(i)));
		return sb.toString();
	}
}
