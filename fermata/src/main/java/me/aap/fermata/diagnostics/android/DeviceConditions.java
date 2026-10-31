package me.aap.fermata.diagnostics.android;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Heat and power conditions behind car lag (thermal throttling, a hot phone on a charger). Read on
 * demand from the sticky battery broadcast and PowerManager: no permission, no polling.
 */
final class DeviceConditions {
	private static final String[] THERMAL = {"none", "light", "moderate", "severe", "critical",
			"emergency", "shutdown"};

	private DeviceConditions() {
	}

	static Map<String, Object> read(Context context) {
		Map<String, Object> c = new LinkedHashMap<>();
		PowerManager pm = context.getSystemService(PowerManager.class);
		if ((pm != null) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
			int status = pm.getCurrentThermalStatus();
			c.put("thermal_status", ((status >= 0) && (status < THERMAL.length)) ?
					THERMAL[status] : "unknown");
		}
		Intent battery = context.registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		if (battery == null) return c;
		int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
		if (temp != Integer.MIN_VALUE) c.put("battery_temp_c", temp / 10.0);
		int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
		if ((level >= 0) && (scale > 0)) c.put("battery_pct", Math.round(level * 100f / scale));
		c.put("plugged", plugged(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)));
		return c;
	}

	private static String plugged(int plugged) {
		switch (plugged) {
			case 0:
				return "none";
			case BatteryManager.BATTERY_PLUGGED_USB:
				return "usb";
			case BatteryManager.BATTERY_PLUGGED_AC:
				return "ac";
			case BatteryManager.BATTERY_PLUGGED_WIRELESS:
				return "wireless";
			default:
				return "other";
		}
	}
}
