package me.aap.fermata.location;

import static org.junit.Assert.*;
import static me.aap.fermata.location.LocationSource.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class LocationSourceSelectorTest {
 private final List<String> changes = new ArrayList<>();
 private final LocationSourceSelector selector = new LocationSourceSelector(
   (from, to, reason) -> changes.add(from + ":" + to + ":" + reason));

 @Test public void firstFixSelectsItsSource() {
  assertTrue(selector.offer(PLATFORM_FUSED, 0));
  assertEquals(PLATFORM_FUSED, selector.current());
  assertEquals(List.of("null:PLATFORM_FUSED:first_fix"), changes);
 }

 @Test public void betterSourceNeedsThreeConsecutiveFixes() {
  selector.offer(PLATFORM_FUSED, 0);
  for (int t = 1000; t <= 3000; t += 1000) {
   selector.offer(PLATFORM_FUSED, t);
   assertEquals(t == 3000, selector.offer(GPS, t));
  }
  assertEquals(GPS, selector.current());
  assertEquals("PLATFORM_FUSED:GPS:better_source", changes.get(1));
 }

 @Test public void gpsGapResetsTheCount() {
  selector.offer(PLATFORM_FUSED, 0);
  selector.offer(GPS, 1000);
  selector.offer(PLATFORM_FUSED, 2000);
  selector.offer(GPS, 2000);
  selector.offer(PLATFORM_FUSED, 4000);
  assertFalse(selector.offer(GPS, 4500));
  selector.offer(PLATFORM_FUSED, 5000);
  assertFalse(selector.offer(GPS, 5500));
  selector.offer(PLATFORM_FUSED, 6000);
  assertTrue(selector.offer(GPS, 6500));
 }

 @Test public void staleSourceFallsBackAtOnce() {
  selector.offer(GPS, 0);
  assertTrue(selector.offer(GOOGLE_FUSED, 2001));
  assertEquals(GOOGLE_FUSED, selector.current());
  assertEquals("GPS:GOOGLE_FUSED:stale", changes.get(1));
 }

 @Test public void fallbackPicksHighestAliveSource() {
  selector.offer(GPS, 0);
  assertFalse(selector.offer(GOOGLE_FUSED, 1501));
  assertFalse(selector.offer(PLATFORM_FUSED, 2001));
  assertEquals(GOOGLE_FUSED, selector.current());
 }

 @Test public void flakyGpsDoesNotFlap() {
  selector.offer(PLATFORM_FUSED, 0);
  for (int t = 1000; t <= 60000; t += 1000) {
   selector.offer(PLATFORM_FUSED, t);
   if (t % 4000 == 1000) assertFalse(selector.offer(GPS, t));
  }
  assertEquals(1, changes.size());
 }

 @Test public void nonCurrentSourceIsNotDelivered() {
  selector.offer(GPS, 0);
  assertFalse(selector.offer(PLATFORM_FUSED, 2000));
  assertEquals(GPS, selector.current());
 }

 @Test public void resetForgetsAllSources() {
  selector.offer(GPS, 0);
  selector.reset();
  assertNull(selector.current());
  assertTrue(selector.offer(PLATFORM_FUSED, 1));
  assertEquals(PLATFORM_FUSED, selector.current());
 }
 @Test public void outOfOrderSamplesCannotBuildPromotionStreak() {
  selector.offer(PLATFORM_FUSED, 0);
  assertFalse(selector.offer(GPS,1000));
  assertFalse(selector.offer(GPS,900));
  assertFalse(selector.offer(GPS,1001));
  assertEquals(PLATFORM_FUSED,selector.current());
 }
}
