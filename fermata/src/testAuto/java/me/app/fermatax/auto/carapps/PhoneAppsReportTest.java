package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.provider.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.app.fermatax.auto.carapps.adb.AdbRecoveryJob;

/** The plain-text report a beta tester sends: what it says for each channel, and what it must never contain. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = me.aap.fermata.FermataApplication.class)
public class PhoneAppsReportTest {
	private Context ctx;
	private PhoneAppsJournal journal;
	private Predicate<Context> savedWifi;
	private Function<String, String> savedProps;

	@Before public void setUp() throws Exception {
		ctx = RuntimeEnvironment.getApplication();
		ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit().clear().commit();
		File file = new File(Files.createTempDirectory("report").toFile(), "carapps_journal.txt");
		journal = new PhoneAppsJournal(file, Runnable::run, System::currentTimeMillis, () -> 7L);
		PhoneAppsJournal.setForTest(journal);
		CarDisplayClient.get().resetForTesting();
		savedWifi = AdbRecoveryJob.wifiCheck;
		savedProps = PhoneAppsReport.propReader;
		PhoneAppsReport.propReader = name -> "sys.usb.config".equals(name) ? "mtp,adb" : null;
	}

	@After public void tearDown() {
		PhoneAppsJournal.setForTest(null);
		PhoneAppsReport.propReader = savedProps;
		AdbRecoveryJob.wifiCheck = savedWifi;
		CarDisplayClient.get().resetForTesting();
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setAdbPaired(false);
		CarAppsFeature.setEnabled(new me.aap.utils.pref.BasicPreferenceStore(), true);
	}

	@Test public void rootPhoneReport() {
		CarAppsFeature.setState(RootState.GRANTED);
		journal.add("root_grant", Map.of("result", "granted"));
		journal.add("server_connected", Map.of("uid", 1000, "version", 335));

		String r = PhoneAppsReport.build(ctx);

		for (String line : new String[]{"app: ", "android: ", "device: ", "rom: ", "abi: ", "locale: ", "enabled: true",
				"channel: root", "root: GRANTED", "adb_paired: false", "notifications_enabled: ", "write_secure_settings: ",
				"adb_enabled: ", "adb_wifi_enabled: ", "development_settings_enabled: ", "usb_debugging: ", "wifi: connected=",
				"client: state=IDLE", "server: ", "--- OEM notes ---", "--- Events (newest last, 2) ---"}) {
			assertTrue(line + " in\n" + r, r.contains(line));
		}
		assertTrue(r, r.indexOf("root_grant result=granted") < r.indexOf("server_connected uid=1000"));
		assertTrue("events are the end of the report", r.trim().endsWith("server_connected uid=1000 version=335"));
	}

	@Test public void adbPhoneReportExplainsAFailureWithoutDetailedDiagnostics() {
		shadowOf((Application) ctx).grantPermissions("android.permission.WRITE_SECURE_SETTINGS");
		Settings.Global.putInt(ctx.getContentResolver(), "adb_enabled", 1);
		Settings.Global.putInt(ctx.getContentResolver(), "adb_wifi_enabled", 0);
		Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
		CarAppsFeature.setAdbPaired(true);
		AdbRecoveryJob.wifiCheck = c -> false;

		CarDisplayClient.get().ensureRunning(ctx, "car_session");
		String r = PhoneAppsReport.build(ctx);

		for (String line : new String[]{"channel: adb", "root: ABSENT", "adb_paired: true", "write_secure_settings: granted",
				"adb_enabled: 1", "adb_wifi_enabled: 0", "development_settings_enabled: 1",
				"usb_debugging: on (adb_enabled=1, sys.usb.config=mtp,adb, persist.sys.usb.config=unreadable)",
				"adb_wifi_restore_marker: none", "client: state=FAILED", "failed_for_wifi=true", "last_failure=no_wifi",
				"start_requested trigger=car_session", "server_starting attempt=0 channel=adb", "server_failed reason=no_wifi"}) {
			assertTrue(line + " in\n" + r, r.contains(line));
		}
	}

	@Test public void oemNotesFollowTheManufacturer() {
		ShadowBuild.setManufacturer("Xiaomi");
		assertTrue(PhoneAppsReport.build(ctx).contains("Autostart"));
		ShadowBuild.setManufacturer("samsung");
		assertTrue(PhoneAppsReport.build(ctx).contains("Never sleeping apps"));
		ShadowBuild.setManufacturer("Google");
		String r = PhoneAppsReport.build(ctx);
		assertTrue(r, r.contains("--- OEM notes ---\nnone"));
	}

	private static final Pattern[] FORBIDDEN = {
			Pattern.compile("PRIVATE KEY|BEGIN [A-Z ]*KEY"),
			Pattern.compile("(?i)code\\D{0,3}\\d{6}"),
			Pattern.compile("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b"),
			Pattern.compile("(?i)ssid"),
	};
	private static final Pattern IPV4 = Pattern.compile("(?<![0-9.])\\d{1,3}(?:\\.\\d{1,3}){3}(?![0-9.])");

	/** Scans a report made from hostile input for keys, pairing codes, MAC and IP addresses and Wi-Fi names. */
	static void assertPrivate(String report) {
		for (Pattern p : FORBIDDEN) assertFalse(p + " in\n" + report, p.matcher(report).find());
		Matcher ip = IPV4.matcher(report);
		while (ip.find()) assertEquals("only loopback in\n" + report, "127.0.0.1", ip.group());
	}

	@Test public void theReportHoldsNoSecretsNorNetworkIdentifiers() {
		CarAppsFeature.setAdbPaired(true);
		Map<String, Object> hostile = new LinkedHashMap<>();
		hostile.put("code", "654321");
		hostile.put("ssid", "HomeNet");
		hostile.put("error", "SSLException at 10.0.0.7 key -----BEGIN PRIVATE KEY----- 3c:22:fb:01:02:03");
		hostile.put("reason", "pairing code 123456");
		hostile.put("from", "127.0.0.1:41234");
		journal.add("pairing", hostile);
		ShadowBuild.setFingerprint("google/oriole/oriole:13/TQ3A.230805.001/10316531:user/release-keys");

		String r = PhoneAppsReport.build(ctx);

		assertPrivate(r);
		assertTrue("the loopback port is fine", r.contains("127.0.0.1:41234"));
	}

	@Test public void theScanCatchesWhatItLooksFor() {
		for (String bad : new String[]{"-----BEGIN PRIVATE KEY-----", "pairing code: 123456", "mac 3c:22:fb:01:02:03",
				"wifi SSID home", "ip 192.168.1.4"}) {
			try {
				assertPrivate(bad);
			} catch (AssertionError expected) {
				continue;
			}
			throw new AssertionError("not caught: " + bad);
		}
		assertPrivate("server 127.0.0.1:5555");
	}
}
