# GPS trip recording

The dashboard subscribes to `TripTrackingService`; leaving the dashboard, switching media,
or turning the screen off does not pause recording. Use the pause button on the speed card
or in the ongoing notification at the end of a trip. An explicit pause survives navigation
and process recreation. Reset clears totals and all position anchors.

## Accounting

- GNSS updates are requested every second with no minimum distance. One provider owns the
  measurement stream; CarFinder's map/weather locations are not merged into trip totals.
- All intervals use elapsed realtime. Missing/out-of-order/old fixes, missing accuracy,
  large positional jumps and implausible speed changes are rejected before accounting.
- Integrate trusted speed with a trapezoid between continuous fixes. Keep subsecond time.
  Motion needs three observations; buffered startup distance is then included. Creeping
  at 0.5 m/s is retained. Speeds within reported uncertainty are treated conservatively.
- When sensor speed is unavailable, a 3–10 second position window can supply an estimate
  only when displacement exceeds twice the combined endpoint uncertainty. That distance
  is labeled estimated, and never also integrated as sensor speed.
- At over five seconds without a valid fix, show an unknown speed, not zero. Keep elapsed
  trip time running. Retry stalled registrations with a 15–120 second backoff, including
  streams that keep delivering old or rejected samples.
- After a gap, require three consistent fixes. Bridge to the confirmed endpoint only for
  gaps of at most ten minutes, endpoint accuracy at most 20 m, significant displacement,
  and plausible average speed. The chord includes the confirmation interval, so those
  fixes are not counted again. Otherwise mark the trip incomplete and start a new segment.
- The primary average includes stops and signal outages, but excludes explicit pauses.
  Estimated or incomplete distance makes the primary average approximate too. Moving
  average is unavailable if any segment was estimated or unobserved.
- Totals are checkpointed every ten seconds and on reset/pause. Recreated processes never
  reuse a location anchor; interruptions are marked incomplete. Same-boot interruption
  time is recovered from the checkpoint, while a reboot never mixes monotonic clocks.

## Limits and calibration

Thresholds are initial engineering defaults, not calibrated promises. A straight chord
undercounts curved tunnels and cannot reconstruct loops. No inertial dead reckoning, map
matching, vehicle bus data or old CarFinder Kalman smoothing is used. At most roughly one
checkpoint interval of recorded totals may be lost on abrupt process termination. Android
or vendor restrictions can still stop the service; `START_NOT_STICKY` avoids silently
restarting location collection from an ineligible background state.

Validate on the affected phone/head unit: stationary drift for ten minutes, crawling,
stop/go traffic, braking, straight/curved tunnels, screen off, GPS toggling, precise vs
approximate permission, provider starvation, dashboard recreation, explicit pause/resume,
process death and reboot. Compare against a known route or vehicle odometer; tune thresholds
only from retained replay traces. Diagnostic tag `FermataTrip` logs ages and registration
failures without logging coordinates.

## Tests

`TripEngineTest` exercises accounting and gap policies without Android. Controller/store/UI
tests and service tests cover Android lifecycle, persistence, and honest stale/estimated
display. Service tests run on API 28 and 35. The module enables Android resources for these
Robolectric tests. Run `:fermata:testAutoDebugUnitTest --tests 'me.aap.fermata.ui.glass.speedo.*'`
with the repository's local test-variant signing configuration where needed.
