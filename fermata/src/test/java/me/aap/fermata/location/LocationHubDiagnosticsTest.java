package me.aap.fermata.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@LooperMode(PAUSED)
public class LocationHubDiagnosticsTest {
	private final List<String> names = new ArrayList<>();
	private final List<Map<String, Object>> attrs = new ArrayList<>();
	private Context ctx;
	private LocationManager lm;

	@Before
	public void setUp() {
		ctx = RuntimeEnvironment.getApplication();
		lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
		shadowOf(lm).setLocationEnabled(true);
		shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
		ShadowSystemClock.advanceBy(Duration.ofMinutes(30));
		LocationHub.resetForTesting();
		LocationHub.diagnostics = (name, a) -> {
			names.add(name);
			attrs.add(new LinkedHashMap<>(a));
		};
	}

	@After
	public void tearDown() {
		LocationHub.resetForTesting();
		LocationHub.diagnostics = LocationHub.DEFAULT_DIAGNOSTICS;
	}

	@Test
	public void missingPermissionIsReported() {
		LocationHub.get(ctx).acquire("t", l -> { });
		int i = names.indexOf("location_registered");
		assertTrue(names.toString(), i >= 0);
		assertEquals("no_permission", attrs.get(i).get("reason"));
	}

	@Test
	public void closingAndReopeningDoesNotEndThePreviousDeliveryGap() {
		shadowOf((Application) ctx).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
		LocationHub hub = LocationHub.get(ctx);
		hub.acquire("t", l -> { });
		hub.checkWatchdogForTesting(SystemClock.elapsedRealtime() + 9_000);
		assertTrue(names.contains("location_gap_started"));
		hub.release("t");
		ShadowSystemClock.advanceBy(Duration.ofMinutes(5));
		hub.acquire("t", l -> { });
		Location fix = new Location(LocationManager.GPS_PROVIDER);
		fix.setAccuracy(5);
		fix.setTime(System.currentTimeMillis());
		fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		shadowOf(lm).simulateLocation(fix);
		ShadowLooper.idleMainLooper();
		shadowOf(hub.getLooperForTesting()).idle();
		assertTrue(names.toString(), !names.contains("location_gap_ended"));
	}

	@Test
	public void deliveryGapStartsAndEnds() {
		shadowOf((Application) ctx).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
		LocationHub hub = LocationHub.get(ctx);
		hub.acquire("t", l -> { });
		int i = names.indexOf("location_registered");
		assertEquals("ok", attrs.get(i).get("reason"));
		hub.checkWatchdogForTesting(SystemClock.elapsedRealtime() + 9_000);
		int gap = names.indexOf("location_gap_started");
		assertTrue(names.toString(), gap >= 0);
		assertTrue(((Number) attrs.get(gap).get("stalled_for_ms")).longValue() >= 9_000);
		ShadowSystemClock.advanceBy(Duration.ofSeconds(10));
		Location fix = new Location(LocationManager.GPS_PROVIDER);
		fix.setLatitude(21.0);
		fix.setLongitude(105.8);
		fix.setAccuracy(5);
		fix.setTime(System.currentTimeMillis());
		fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		shadowOf(lm).simulateLocation(fix);
		Location fused = new Location(fix);
		fused.setProvider("fused");
		shadowOf(lm).simulateLocation(fused);
		ShadowLooper.idleMainLooper();
		shadowOf(hub.getLooperForTesting()).idle();
		int ended = names.indexOf("location_gap_ended");
		assertTrue(names.toString(), ended >= 0);
		assertTrue(((Number) attrs.get(ended).get("duration_ms")).longValue() >= 9_000);
	}
}
