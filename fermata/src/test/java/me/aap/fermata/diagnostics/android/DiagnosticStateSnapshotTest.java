package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class DiagnosticStateSnapshotTest {

	@Test
	public void snapshotHasAllSections() throws Exception {
		Context context = RuntimeEnvironment.getApplication();
		String text = DiagnosticStateSnapshot.toJson(context);
		JSONObject json = new JSONObject(text);
		for (String section : new String[]{"permissions", "location", "background", "auto", "app"}) {
			assertTrue(section, json.has(section));
		}
		Object fine = json.getJSONObject("permissions")
				.getJSONObject("android.permission.ACCESS_FINE_LOCATION").get("granted");
		assertTrue(fine instanceof Boolean);
		assertEquals("not_installed", json.getJSONObject("auto").getString("gearhead_version"));
        assertEquals("missing", json.getJSONObject("location").getString("google_play_services"));
		assertFalse(text.contains("latitude"));
		assertFalse(text.contains("longitude"));
	}

	@Test
	public void failingSectionReportsItsError() throws Exception {
		JSONObject section = DiagnosticStateSnapshot.section(() -> {
			throw new SecurityException("denied");
		});
		assertEquals("SecurityException", section.getString("error"));
		assertEquals(1, section.length());
	}
}
