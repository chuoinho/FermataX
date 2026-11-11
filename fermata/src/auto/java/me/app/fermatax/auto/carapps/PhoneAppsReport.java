package me.app.fermatax.auto.carapps;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.app.fermatax.auto.carapps.adb.AdbRecoveryJob;

/**
 * The plain-text Phone Apps report a beta tester sends: build, phone, the settings Phone Apps depends on, the client's
 * state and the always-on journal. Built off the main thread when the user asks for it. Holds no key, pairing code,
 * Wi-Fi name, MAC or IP address (the loopback port aside), account, app list, location, clipboard or notification text.
 */
public final class PhoneAppsReport {
	/** System properties are read with getprop; an app may be denied some, which then read as unreadable. */
	static volatile Function<String, String> propReader = PhoneAppsReport::getprop;

	private PhoneAppsReport() {
	}

	public static String build(Context ctx) {
		StringBuilder r = new StringBuilder(8192);
		r.append("=== FermataX Phone Apps report ===\n");
		line(r, "created", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ", Locale.US))
				+ " uptime_ms=" + SystemClock.elapsedRealtime());
		line(r, "app", ctx.getPackageName() + ' ' + BuildConfig.VERSION_NAME + " build " + BuildConfig.VERSION_CODE
				+ " flavor=" + BuildConfig.FLAVOR + " type=" + BuildConfig.BUILD_TYPE);
		line(r, "android", Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT + " patch=" + Build.VERSION.SECURITY_PATCH);
		line(r, "device", Build.MANUFACTURER + " / " + Build.BRAND + " / " + Build.MODEL);
		line(r, "rom", Build.FINGERPRINT);
		line(r, "abi", String.join(",", Build.SUPPORTED_ABIS));
		line(r, "locale", Locale.getDefault().toLanguageTag());

		r.append("--- Phone Apps ---\n");
		ServerStarter.Channel channel = ServerStarter.choose(CarAppsFeature.state(), CarAppsFeature.isAdbPaired(),
				Build.VERSION.SDK_INT);
		line(r, "enabled", String.valueOf(CarAppsFeature.isEnabled()));
		line(r, "channel", channel.name().toLowerCase(Locale.ROOT));
		line(r, "root", String.valueOf(CarAppsFeature.state()));
		line(r, "adb_paired", String.valueOf(CarAppsFeature.isAdbPaired()));
		line(r, "notifications_enabled", String.valueOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()));
		line(r, "write_secure_settings", (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
				== PackageManager.PERMISSION_GRANTED) ? "granted" : "not granted");
		String adb = global(ctx, "adb_enabled");
		line(r, "adb_enabled", adb);
		line(r, "adb_wifi_enabled", global(ctx, "adb_wifi_enabled"));
		line(r, "development_settings_enabled", global(ctx, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED));
		String usb = prop("sys.usb.config");
		String persist = prop("persist.sys.usb.config");
		boolean on = "1".equals(adb) || usb.contains("adb") || persist.contains("adb");
		line(r, "usb_debugging", (on ? "on" : "off") + " (adb_enabled=" + adb + ", sys.usb.config=" + usb
				+ ", persist.sys.usb.config=" + persist + ')');
		android.content.SharedPreferences prefs = ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
		line(r, "adb_wifi_restore_marker", prefs.contains(AdbRecoveryJob.PREF_RESTORE)
				? String.valueOf(prefs.getInt(AdbRecoveryJob.PREF_RESTORE, -1)) : "none");
		line(r, "recovery_tries", String.valueOf(prefs.getInt(AdbRecoveryJob.PREF_TRIES, 0)));
		line(r, "wifi", wifi(ctx));

		CarDisplayClient client = CarDisplayClient.get();
		line(r, "client", "state=" + client.state() + " failed_for_wifi=" + client.failedForWifi()
				+ " asking_running_server=" + client.isAskingRunningServer() + " last_failure=" + client.lastFailure()
				+ " panel_off=" + client.isPanelOff());
		line(r, "server", "uid=" + client.serverUid() + " expected_version=" + client.getExpectedVersion()
				+ " epoch=" + client.serverEpoch() + " capabilities=0x" + Integer.toHexString(client.capabilities())
				+ " saved_capabilities=0x" + Integer.toHexString(CarDisplayClient.savedCapabilities(ctx, 0)));

		r.append("--- OEM notes ---\n").append(oemNotes(Build.MANUFACTURER, Build.BRAND));

		List<String> events = PhoneAppsJournal.snapshot();
		r.append("--- Events (newest last, ").append(events.size()).append(") ---\n");
		for (String e : events) r.append(e).append('\n');
		return r.toString();
	}

	private static void line(StringBuilder r, String name, String value) {
		r.append(name).append(": ").append(value).append('\n');
	}

	private static String global(Context ctx, String name) {
		try {
			int v = Settings.Global.getInt(ctx.getContentResolver(), name, Integer.MIN_VALUE);
			return (v == Integer.MIN_VALUE) ? "unset" : String.valueOf(v);
		} catch (RuntimeException e) {
			return "unreadable";
		}
	}

	private static String prop(String name) {
		String v;
		try {
			v = propReader.apply(name);
		} catch (RuntimeException e) {
			v = null;
		}
		return ((v == null) || v.trim().isEmpty()) ? "unreadable" : v.trim().replaceAll("[^A-Za-z0-9_,.-]", "_");
	}

	/** Connected, metered and refused flags only: never the network's name, BSSID or addresses. */
	@SuppressWarnings("deprecation")
	private static String wifi(Context ctx) {
		boolean connected = false, metered = false, validated = false;
		try {
			ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
			if (cm != null) {
				for (Network n : cm.getAllNetworks()) {
					NetworkCapabilities caps = cm.getNetworkCapabilities(n);
					if ((caps == null) || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
					connected = true;
					metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
					validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
					break;
				}
			}
		} catch (RuntimeException ignored) {
		}
		boolean usable;
		try {
			usable = AdbRecoveryJob.hasWifi(ctx);
		} catch (RuntimeException e) {
			usable = false;
		}
		return "connected=" + connected + " metered=" + metered + " validated=" + validated
				+ " usable_for_wireless_debugging=" + usable + " refused_by_android_once=" + (AdbRecoveryJob.refusedWifi != null);
	}

	/** Hints only, for the manufacturer's background and debugging limits; nothing is changed on the phone. */
	static String oemNotes(@Nullable String manufacturer, @Nullable String brand) {
		String m = ((manufacturer == null) ? "" : manufacturer) + ' ' + ((brand == null) ? "" : brand);
		m = m.toLowerCase(Locale.ROOT);
		StringBuilder n = new StringBuilder();
		if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
			n.append("Xiaomi/MIUI/HyperOS: allow Autostart for FermataX and set its battery saver to No restrictions.\n")
					.append("Xiaomi/MIUI/HyperOS: Developer options > USB debugging (Security settings) is needed for touch.\n");
		}
		if (m.contains("samsung")) {
			n.append("Samsung: Battery > Background usage limits: put FermataX in Never sleeping apps.\n");
		}
		if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) {
			n.append("Oppo/Realme/OnePlus: allow Auto launch and background activity for FermataX.\n");
		}
		if (m.contains("vivo") || m.contains("iqoo")) {
			n.append("Vivo/iQOO: allow Autostart and High background power consumption for FermataX.\n");
		}
		if (m.contains("huawei") || m.contains("honor")) {
			n.append("Huawei/Honor: App launch: manage FermataX manually and allow all three options.\n");
		}
		return (n.length() == 0) ? "none\n" : n.toString();
	}

	@Nullable
	private static String getprop(String name) {
		try {
			Process p = new ProcessBuilder("getprop", name).redirectErrorStream(true).start();
			String out;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				out = r.readLine();
			}
			if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroy();
			return out;
		} catch (Exception e) {
			return null;
		}
	}
}
