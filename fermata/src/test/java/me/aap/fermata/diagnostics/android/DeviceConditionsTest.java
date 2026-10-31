package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

import me.aap.fermata.diagnostics.DiagnosticSanitizer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class DeviceConditionsTest {

	@SuppressWarnings("deprecation")
	private static Context withBattery(int tenthsC, int level, int plugged) {
		Context context = RuntimeEnvironment.getApplication();
		context.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED)
				.putExtra(BatteryManager.EXTRA_TEMPERATURE, tenthsC)
				.putExtra(BatteryManager.EXTRA_LEVEL, level)
				.putExtra(BatteryManager.EXTRA_SCALE, 100)
				.putExtra(BatteryManager.EXTRA_PLUGGED, plugged));
		return context;
	}

	@Test
	public void readsThermalBatteryAndCharging() {
		Map<String, Object> c = DeviceConditions.read(
				withBattery(412, 80, BatteryManager.BATTERY_PLUGGED_USB));
		assertEquals("none", c.get("thermal_status"));
		assertEquals(41.2, (Double) c.get("battery_temp_c"), 0.01);
		assertEquals(80, c.get("battery_pct"));
		assertEquals("usb", c.get("plugged"));
	}

	@Test
	public void unpluggedAndWireless() {
		assertEquals("none", DeviceConditions.read(withBattery(300, 50, 0)).get("plugged"));
		assertEquals("wireless", DeviceConditions.read(withBattery(300, 50,
				BatteryManager.BATTERY_PLUGGED_WIRELESS)).get("plugged"));
	}

	@Test
	public void conditionsSurviveExportSanitizer() {
		Map<String, Object> c = DeviceConditions.read(
				withBattery(412, 80, BatteryManager.BATTERY_PLUGGED_AC));
		assertEquals(c.keySet(), new DiagnosticSanitizer().sanitizeAttributes(c).keySet());
	}

	@Test
	public void stateSnapshotHasDeviceSection() throws Exception {
		JSONObject device = new JSONObject(DiagnosticStateSnapshot.toJson(
				withBattery(350, 60, BatteryManager.BATTERY_PLUGGED_USB))).getJSONObject("device");
		assertEquals("usb", device.getString("plugged"));
		assertTrue(device.has("thermal_status"));
		assertFalse(device.has("error"));
	}

	@Test
	public void stallEventCarriesDeviceConditions() throws Exception {
		String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
				"src/main/java/me/aap/fermata/diagnostics/android/AndroidDiagnosticsRuntime.java")),
				java.nio.charset.StandardCharsets.UTF_8);
		int stall = source.indexOf("\"suspected_main_thread_stall\"");
		assertTrue(source.lastIndexOf("DeviceConditions.read(", stall) > source.lastIndexOf(
				"attributes.put(\"missed_probes\"", stall));
	}
}
