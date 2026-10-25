package me.aap.fermata.diagnostics.android;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Callable;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.pref.DiagnosticsPreferences;
import me.aap.fermata.ui.policy.BackgroundRunState;

/**
 * Device, permission and app state written to {@code state.json} when a report is exported. Holds
 * no user data and no coordinates; each section is read independently so one failure cannot hide
 * the others.
 */
public final class DiagnosticStateSnapshot {
	private static final String GEARHEAD = "com.google.android.projection.gearhead";

	private DiagnosticStateSnapshot() {
	}

	public static String toJson(Context context) {
		JSONObject json = new JSONObject();
		try {
			json.put("permissions", section(() -> permissions(context)));
			json.put("location", section(() -> location(context)));
			json.put("background", section(() -> background(context)));
			json.put("auto", section(() -> auto(context)));
			json.put("app", section(() -> app(context)));
		} catch (Exception unexpected) {
			return "{\"error\":\"" + unexpected.getClass().getSimpleName() + "\"}";
		}
		return json.toString();
	}

	static JSONObject section(Callable<JSONObject> reader) {
		try {
			return reader.call();
		} catch (Throwable failure) {
			JSONObject error = new JSONObject();
			try {
				error.put("error", failure.getClass().getSimpleName());
			} catch (Exception ignored) {
			}
			return error;
		}
	}

	private static JSONObject permissions(Context context) throws Exception {
		JSONObject json = new JSONObject();
		PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
				PackageManager.GET_PERMISSIONS);
		if (info.requestedPermissions != null) {
			for (String p : info.requestedPermissions) {
				JSONObject entry = new JSONObject();
				entry.put("granted",
						ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED);
				if (p.endsWith(".ACCESS_FINE_LOCATION") || p.endsWith(".ACCESS_COARSE_LOCATION")) {
					entry.put("app_op", appOp(context, p));
				}
				json.put(p, entry);
			}
		}
		json.put("notification_listener", NotificationManagerCompat
				.getEnabledListenerPackages(context).contains(context.getPackageName()));
		return json;
	}

	private static String appOp(Context context, String permission) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unknown";
		String op = AppOpsManager.permissionToOp(permission);
		AppOpsManager ops = context.getSystemService(AppOpsManager.class);
		if ((op == null) || (ops == null)) return "unknown";
		int mode = ops.unsafeCheckOpNoThrow(op, android.os.Process.myUid(), context.getPackageName());
		switch (mode) {
			case AppOpsManager.MODE_ALLOWED:
				return "allow";
			case AppOpsManager.MODE_FOREGROUND:
				return "foreground";
			case AppOpsManager.MODE_IGNORED:
				return "ignore";
			case AppOpsManager.MODE_ERRORED:
				return "error";
			default:
				return "default";
		}
	}

	@SuppressWarnings("MissingPermission")
	private static JSONObject location(Context context) throws Exception {
		JSONObject json = new JSONObject();
		try {
            int status = com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
            json.put("google_play_services", status == com.google.android.gms.common.ConnectionResult.SUCCESS ? "available" : "missing");
        } catch (Throwable failure) { json.put("google_play_services", "error"); }
        LocationManager lm = context.getSystemService(LocationManager.class);
		if (lm == null) {
			json.put("service", "missing");
			return json;
		}
		json.put("enabled", lm.isLocationEnabled());
		json.put("providers", new JSONArray(lm.getProviders(true)));
		json.put("has_fused", (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
				&& (lm.getProvider(LocationManager.FUSED_PROVIDER) != null));
		String throttle = Settings.Global.getString(context.getContentResolver(),
				"location_background_throttle_interval_ms");
		json.put("background_throttle_ms", (throttle == null) ? JSONObject.NULL : throttle);
		JSONObject lastKnown = new JSONObject();
		long nowNanos = SystemClock.elapsedRealtimeNanos();
		for (String provider : lm.getProviders(false)) {
			JSONObject entry = new JSONObject();
			try {
				Location l = lm.getLastKnownLocation(provider);
				if (l == null) {
					entry.put("present", false);
				} else {
					entry.put("age_ms", (nowNanos - l.getElapsedRealtimeNanos()) / 1_000_000L);
					if (l.hasAccuracy()) entry.put("accuracy_m", Math.round(l.getAccuracy()));
				}
			} catch (SecurityException denied) {
				entry.put("error", "SecurityException");
			}
			lastKnown.put(provider, entry);
		}
		json.put("last_known", lastKnown);
		return json;
	}

	private static JSONObject background(Context context) throws Exception {
		JSONObject json = new JSONObject();
		json.put("manufacturer", Build.MANUFACTURER);
		json.put("vendor_guide_done", BackgroundRunState.isVendorGuideDone(context));
		ActivityManager am = context.getSystemService(ActivityManager.class);
		if (am != null) json.put("background_restricted", am.isBackgroundRestricted());
		PowerManager pm = context.getSystemService(PowerManager.class);
		if (pm != null) {
			json.put("ignoring_battery_optimizations",
					pm.isIgnoringBatteryOptimizations(context.getPackageName()));
			json.put("power_save", pm.isPowerSaveMode());
		}
		UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
		if (usm != null) json.put("standby_bucket", usm.getAppStandbyBucket());
		json.put("foreground", AndroidDiagnosticsRuntime.get().isForeground());
		ActivityManager.RunningAppProcessInfo process = new ActivityManager.RunningAppProcessInfo();
		ActivityManager.getMyMemoryState(process);
		json.put("importance", process.importance);
		return json;
	}

	private static JSONObject auto(Context context) throws Exception {
		JSONObject json = new JSONObject();
		try {
			json.put("gearhead_version",
					context.getPackageManager().getPackageInfo(GEARHEAD, 0).versionName);
		} catch (PackageManager.NameNotFoundException missing) {
			json.put("gearhead_version", "not_installed");
		}
		json.put("connection", AutomotiveConnectionState.get().state().name());
		return json;
	}

	private static JSONObject app(Context context) throws Exception {
		JSONObject json = new JSONObject();
		PackageManager pm = context.getPackageManager();
		String installer = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
				? pm.getInstallSourceInfo(context.getPackageName()).getInstallingPackageName()
				: pm.getInstallerPackageName(context.getPackageName());
		json.put("installer", (installer == null) ? JSONObject.NULL : installer);
		DiagnosticsPreferences prefs = new DiagnosticsPreferences(context);
		json.put("detailed_enabled", prefs.isDetailedEnabled(System.currentTimeMillis()));
		json.put("detailed_expires_at_ms", prefs.getDetailedExpiresAt());
		SharedPreferences trip = context.getSharedPreferences("speedo_trip_v2", Context.MODE_PRIVATE);
		json.put("trip_user_paused", trip.getBoolean("user_paused", false));
		json.put("trip_allow_mock", trip.getBoolean("allow_mock", false));
		json.put("addons", section(() -> addons()));
		return json;
	}

	private static JSONObject addons() throws Exception {
		JSONObject json = new JSONObject();
		AddonManager manager = AddonManager.get();
		if (manager == null) return json;
		for (AddonInfo info : manager.getAddonInfos()) {
			json.put(info.moduleName, manager.getAddonState(info).name());
		}
		return json;
	}
}
