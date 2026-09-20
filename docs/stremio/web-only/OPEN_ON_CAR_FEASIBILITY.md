# Stremio Open on Car — feasibility gate

Status: **INCOMPLETE / NOT ENABLED**. The existing hosted-player acceptance results do not prove
phone-to-car transfer. This checkpoint hardens Stremio route persistence, recovery and existing
controls only. It does not install a source-selection observer, produce a `STREMIO_PLAYER`
request, silence phone playback, or enable a car receiver. The generic Web URL observer is not
used for Stremio. Do not advertise Stremio Open on Car as working.

## Why forwarding remains closed

The current Stremio client has ordinary WebView navigation callbacks and an origin-scoped
MediaSession control bridge. Neither a player-shaped hash, a native navigation gesture, a
history callback, READY, nor handler registration proves a specific user-selected source.
POST, recovery, redirect and same-document SPA routes must not be reclassified as explicit
source selection. No deterministic upstream selection signal has been established here.

A real player URL may embed source/episode context. Its portability into a fresh CAR WebView,
using that WebView's own account/server state, has not been tested. No upstream `usePlayer.js`
reading, synthetic fixture or old single-host playback result can pass this gate.

## Safe checkpoint behavior

- Route eligibility is bounded at 64 KiB of UTF-8, checks the hosted HTTPS origin and excludes
  URL userinfo and non-default ports. Player routes, including the empty and encoded prefixes,
  remain excluded from saved entry state. No stream object is extracted or reconstructed.
- Fresh-document recovery accepts only persistable browsing routes. Disconnect and new-session
  reset drop its RAM-only pending target. Renderer recovery of a player goes to Home, never
  reloads the old player. This is local recovery, not a substituted detail/home transfer.
- Existing controls require observed playback state: Play only while paused, Pause only while
  playing. The JavaScript command must match the live document and session, and pagehide closes
  it. READY/registered handlers alone are insufficient. Next remains the current hosted player
  handler; no synthetic episode advance is introduced.
- Service-owned `ControlOnlySessionState` still refuses a second bridge until the first releases.
  The Stremio bridge supplies document/session content identity and invalidates itself on service
  revocation. No second MediaSession, stream server, TLS bypass, retry, or reconnect replay exists.

## Tests and limits

`StremioOpenOnCarTest` uses **synthetic** detail/season/episode/player fixtures and `media.invalid`.
Its player fixture is not a captured upstream route. It verifies route classification,
UTF-8 boundaries, RAM recovery cancellation, player recovery exclusion and existing service
ownership. View tests use Android default-value stubs and constructor-free allocation; they
do not demonstrate renderer, fragment, WebView or audible behavior on a device.

`StremioWebSessionPolicyTest` and `StremioWebMediaSessionBridgeTest` cover persistence and control
state. `node modules/web/src/test/stremio-control-script.cjs` executes the production shim with
native-shaped and fallback MediaSession fixtures. These are control tests, **not source-silence**
or transfer tests. No once-only typed transfer, POST transfer, OFF-before-commit, Settings/back
transfer, connection/registration/source token, or destination lease test can pass without an
implemented verified adapter; those remain open requirements rather than vacuous tests.

## Runtime checklist before implementation can be accepted

All entries below are **NOT RUN** for Open on Car.

1. Record device/Android, WebView package and exact version, Stremio deployment/upstream revision,
   account eligibility and user-configured streaming-server reachability on both hosts. Keep
   credentials and real player payloads out of logs, screenshots, saved preferences and reports.
2. Observe a deterministic explicit source selection. Separately exercise detail, season and
   episode browsing, autoplay/next, restoration, POST, redirects and same-document navigation.
   Only the verified explicit source selection may create a typed `STREMIO_PLAYER` request.
3. Open the exact selected player route in a fresh CAR Stremio WebView using its existing session.
   Verify the same source/episode and genuine progressing playback. Do not send a generic/raw
   media URL or account credentials. Retain the bounded payload only in RAM.
4. Verify the Stremio-only capture/silence hook prevents phone playback before transfer commit,
   including races and alternate upstream player paths. A pause after audible playback is not
   proof of silence. Do not add a global media/DOM hook or affect generic Web/YouTube/Mirror.
5. Prove a single typed dispatch and all source, connection, registration, mode, request-token
   and service-lease guards. OFF before commit, disconnect/reconnect, Settings/back, source/view
   loss and renderer loss must cancel pending work without replay. No local playback fallback.
6. Prove old-owner release before car-owner claim. READY/LOAD_DISPATCHED are not PLAYING; do not
   send blind Play. After commit, OFF must not move the owner, and Next must remain car-owned.

If source silence or exact-route portability fails, keep the feature disabled and report the
specific blocker. Do not downgrade to detail URLs or generic URL synchronization.
