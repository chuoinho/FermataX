# Phase 1C: Native MediaSession Observation Remediation

## Status

**Implementation and automated verification PASS. Physical playback acceptance
remains PARTIAL.**

## Defect

`StremioWebMediaSessionBridge` previously returned as soon as WebView supplied
the native `navigator.mediaSession` object. That preserved the browser API but
left FermataX unable to observe the page's playback state, metadata, and
control-handler registration. The prior lifecycle fixture demonstrated the
result: visible HTML5 playback advanced while FermataMediaService remained
`NONE`.

## Correction

The document-start bridge now decorates the native MediaSession instance when it
is available. It preserves each native setter and `setActionHandler` call before
emitting the already bounded, versioned control message to FermataX. It observes
only `play`, `pause`, and `nexttrack`; it does not read DOM, video sources,
cookies, storage, route payloads, or account data. If the native properties
cannot be safely decorated, it fails closed and leaves the host session
unclaimed.

The action allow-list has no inherited properties, so names outside those three
actions are still passed to the browser's native API but are never reported to
or dispatched by FermataX.

The existing synthetic fallback is unchanged for WebViews without native Media
Session support. Navigator's native MediaSession object is never replaced.

## Verification

- `:web:testAutoDebugUnitTest --tests
  me.aap.fermata.addon.web.stremio.StremioWebMediaSessionBridgeTest`: PASS.
- A Node VM execution of the exact generated shim against a native-like
  MediaSession: PASS. Native handler calls were preserved; `READY`, handler,
  metadata and playback-state messages were emitted; bridge dispatch invoked the
  registered native handler.
- Signed `WEB_STREMIO=true` Auto release universal APK: PASS build and install
  on device `15c36230`.
- `aauto.aar` SHA-256 remained
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

## Physical Gate Still Open

The device displayed Stremio identity `anonymous`. The temporary local addon
was visible in the current Addons UI, but disappeared after the required app
restart because it was not synced to an authenticated account. The hosted detail
route then had no stream provider, so no real stream could be selected after
restart. This is fixture/session-state evidence, not a failure of the corrected
bridge.

Accordingly, this report does not claim that the corrected bridge reached
`PLAYING` on the physical device. Background/resume, addon switching and
renderer recovery remain unverified until the user signs into Stremio and the
temporary fixture survives the lifecycle required for the test.

## Cleanup

- The temporary addon is absent from the final device UI.
- The Node fixture process was stopped.
- `adb reverse tcp:7000` was removed.
- Host TCP 7000 has no listener; the device loopback probe returned
  `Connection refused`.
- The stopped lifecycle fixture folder and local screenshots/XML remain because
  the execution environment rejects the verified, scoped recursive deletion.
  They contain no account material and no process references them.
