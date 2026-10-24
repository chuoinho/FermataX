package me.aap.fermata.location;

import java.util.Arrays;

final class LocationSourceSelector {
 interface Listener {
  void onSourceChanged(LocationSource from, LocationSource to, String reason);
 }

 private final Listener listener;
 private final long[] last = new long[LocationSource.values().length];
 private final int[] consecutive = new int[last.length];
 private LocationSource current;

 LocationSourceSelector(Listener listener) {
  this.listener = listener;
  reset();
 }

 boolean offer(LocationSource source, long nowMs) {
  return offer(source, nowMs, nowMs);
 }

 boolean offer(LocationSource source, long fixMs, long nowMs) {
  int i = source.ordinal();
  if (fixMs > nowMs || nowMs - fixMs > 2000
    || (consecutive[i] != 0 && fixMs <= last[i])) return false;
  consecutive[i] = consecutive[i] != 0 && fixMs - last[i] <= 2000
    ? consecutive[i] + 1 : 1;
  last[i] = fixMs;
  LocationSource next = current;
  String reason = null;
  if (current == null || !alive(current, nowMs)) {
   for (LocationSource candidate : LocationSource.values()) {
    if (alive(candidate, nowMs)) {
     next = candidate;
     break;
    }
   }
   reason = current == null ? "first_fix" : "stale";
  } else if (i < current.ordinal() && consecutive[i] >= 3) {
   next = source;
   reason = "better_source";
  }
  if (next != current) {
   LocationSource previous = current;
   current = next;
   listener.onSourceChanged(previous, next, reason);
  }
  return current == source;
 }

 private boolean alive(LocationSource source, long nowMs) {
  int i = source.ordinal();
  return consecutive[i] != 0 && nowMs - last[i] <= 2000;
 }

 LocationSource current() { return current; }

 void reset() {
  current = null;
  Arrays.fill(last, 0);
  Arrays.fill(consecutive, 0);
 }
}
