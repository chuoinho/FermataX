package me.aap.fermata.location;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class LocationHubSourcesTest {
 private Application ctx;
 private LocationManager lm;
 private final List<Map<String,Object>> registrations = new ArrayList<>();
 private final List<LocationSource> received = new ArrayList<>();
 private Consumer<Location> googleSink;

 @Before public void setup() {
  LocationHub.resetForTesting();
  ctx = RuntimeEnvironment.getApplication();
  shadowOf(ctx).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
  lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
  shadowOf(lm).setLocationEnabled(true);
  shadowOf(lm).setProviderEnabled("gps", true);
  if (android.os.Build.VERSION.SDK_INT >= 31) {
   shadowOf(lm).setProviderProperties("fused", new org.robolectric.shadows.ShadowLocationManager.ProviderProperties(false, false, false, false, true, true, true, 3, 1));
   shadowOf(lm).setProviderEnabled("fused", true);
  }
  ShadowSystemClock.advanceBy(Duration.ofMinutes(30));
  LocationHub.googleFactory = (c,l,s) -> null;
  LocationHub.diagnostics = (n,a) -> { if (n.equals("location_registered")) registrations.add(a); };
 }
 @After public void cleanup() {
  LocationHub.resetForTesting();
  LocationHub.googleFactory = GoogleFusedSource.DEFAULT;
  LocationHub.diagnostics = LocationHub.DEFAULT_DIAGNOSTICS;
 }
 private LocationHub acquire() {
  LocationHub hub = LocationHub.get(ctx);
  hub.acquire("test", new LocationHub.Listener() {
   public void onLocation(Location l) { fail("must deliver source"); }
   public void onLocation(Location l, LocationSource s) { received.add(s); }
  });
  return hub;
 }
 private Location fix(String provider) {
  Location l = new Location(provider);
  l.setAccuracy(5);
  l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
  return l;
 }
 private void send(String provider) {
  shadowOf(lm).simulateLocation(fix(provider));
  ShadowLooper.idleMainLooper();
  shadowOf(LocationHub.get(ctx).getLooperForTesting()).idle();
 }
 @Test public void registersGpsAndPlatformFused() {
  acquire();
  assertEquals(registrations.toString(), 2, shadowOf(lm).getRequestLocationUpdateListeners().size());
  assertTrue(registrations.stream().anyMatch(a -> "gps".equals(a.get("provider")) && "ok".equals(a.get("reason"))));
  assertTrue(registrations.stream().anyMatch(a -> "platform_fused".equals(a.get("provider")) && "ok".equals(a.get("reason"))));
  assertTrue(registrations.stream().anyMatch(a -> "no_google_services".equals(a.get("reason"))));
 }
 @Test public void googleSourceIsUsedWhenAvailable() {
  LocationHub.googleFactory = (c,l,s) -> { googleSink = s; return new GoogleFusedSource() {
   public void start() {} public void stop() {}
  }; };
  acquire();
  for (int i=0;i<3;i++) {
   googleSink.accept(fix("fused"));
   ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
  }
  assertEquals(List.of(LocationSource.GOOGLE_FUSED, LocationSource.GOOGLE_FUSED, LocationSource.GOOGLE_FUSED), received);
 }
 @Test public void gpsWinsOverFused() {
  acquire(); send("fused");
  for (int i=0;i<3;i++) {
   ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
   send("fused"); send("gps");
  }
  assertEquals(LocationSource.GPS, received.get(received.size()-1));
  int count = received.size(); send("fused");
  assertEquals(count, received.size());
 }
 @Test public void failingSourceDoesNotBlockOthers() {
  LocationHub.googleFactory = (c,l,s) -> new GoogleFusedSource() {
   public void start() { throw new SecurityException("denied"); }
   public void stop() {}
  };
  acquire(); send("gps");
  assertEquals(List.of(LocationSource.GPS), received);
  assertTrue(registrations.stream().anyMatch(a -> "google_fused".equals(a.get("provider")) && "failed".equals(a.get("reason"))));
 }
 @Test @Config(sdk=30) public void gpsOnlyDeviceStillWorks() {
  acquire();
  assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());
  send("gps");
  assertEquals(List.of(LocationSource.GPS), received);
 }
 @Test public void gpsWithoutAccuracyIsNotDelivered() {
  acquire();
  Location l = fix("gps"); l.removeAccuracy();
  shadowOf(lm).simulateLocation(l);
  shadowOf(LocationHub.get(ctx).getLooperForTesting()).idle();
  assertTrue(received.isEmpty());
 }
 @Test public void oldFixIsNotDelivered() {
  acquire();
  Location l = fix("gps"); l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-3_000_000_000L);
  shadowOf(lm).simulateLocation(l);
  shadowOf(LocationHub.get(ctx).getLooperForTesting()).idle();
  assertTrue(received.isEmpty());
 }
}

