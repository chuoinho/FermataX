# Phase 5B5 - FermataX Hosted Playback Matrix

## Purpose

Phase 5B4 proved the upstream Stremio Guest/Core-to-HTML5 direct-MP4 boundary
in Chrome. Phase 5B5 is a separate physical-device acceptance run for the
FermataX hosted WebView shell. It must not reuse Chrome success as evidence for
FermataX ownership, fullscreen, lifecycle, or renderer behavior.

## Fixed Architecture Boundaries

- `https://web.stremio.com` remains the catalog, account, stream-selection,
  HTML5-player, progress, and subtitle owner.
- FermataX remains the WebView host, browser-custom-view/fullscreen, Android
  lifecycle, and control-only MediaSession owner.
- No native player, stream-URL extraction, Stremio Core, torrent runtime,
  profile/server credential access, DOM scripting, Core dispatch, storage
  access, or `aauto.aar` change is permitted.
- The local fixture is direct HTTP/HLS validation content only. It cannot be
  used as proof of torrent transport, external-player support, or a real
  episode `nexttrack` transition.

## Isolation and Safety

1. Record the pre-existing installed-addon list through the normal Stremio UI.
   Do not read account state or browser storage.
2. Start the known local fixture only on host loopback and expose it only with
   a temporary `adb reverse tcp:7000 tcp:7000` rule.
3. Install exactly one `Fermata Local Validation` addon through the hosted
   Stremio UI and confirm it is present before each selection. Do not alter,
   reorder, or remove other addons.
4. Passive network evidence may record timestamp, method, redacted path class,
   response status, media type, Range presence, and lifecycle. It must not
   record media URLs, query strings, cookies, tokens, authorization, referer,
   response bodies, or account data.
5. Stop immediately on unexpected addon/account changes, external-app launch,
   crash, ANR, or a fixture that cannot be safely uninstalled.

## Acceptance Matrix

| ID | Scenario | Required physical evidence | Pass criterion |
| --- | --- | --- | --- |
| F0 | Hosted preflight | FermataX starts Stremio hosted origin and retained user session; server setting remains user-owned | Hosted UI usable; no legacy/native playback surface appears |
| F1 | Fixture activation | Normal UI install, reload, canonical Installed list, fixture catalog | One temporary fixture only; other addon list unchanged |
| F2 | Direct MP4 handoff | Catalog -> meta -> stream -> `#/player/...`; media `GET` with Range and `206`; Player play/pause UI | Hosted WebView player receives and starts direct MP4 |
| F3 | Bridge ownership | `dumpsys media_session` while active, then after leaving Stremio | Control-only Stremio claim exists only while page advertises playable media and releases after exit |
| F4 | Seek | One visible player seek gesture and a later media range / resumed player state | Seek is handled by the hosted player without crash, external handoff, or a stale Fermata session |
| F5 | Subtitle selection | One normal player subtitle selection; visible selected state and/or bounded VTT player evidence | Selection path is operable. Merely prefetching VTT is not a pass |
| F6 | Fullscreen and Back | Normal fullscreen entry, Back exit, detail/player route and playback state after exit | Back exits browser custom view first; no unexpected reload or external player |
| F7 | Background/resume | Home/background then resume FermataX while direct MP4 is active | No crash/ANR; page and bridge recover to an operable state without stale ownership |
| F8 | HLS handoff | Separate normal HLS selection; manifest/segment requests plus Player UI | Hosted player begins HLS loading without external handoff or app instability |
| F9 | Exit and cleanup | Fixture uninstall via canonical UI, close test surface, stop transport | Fixture absent; original addon list restored; server/reverse/forwards/temporary artifacts removed |

## Non-Claims and Deferred Items

- Torrent/infohash transport: not run. It requires a separately valid and
  approved server-backed fixture.
- `nexttrack`: not run unless Stremio advertises a real next-track handler on a
  repeatable multi-item fixture.
- AA/DHU vehicle media-button behavior: not inferred from phone/ADB controls.
- Renderer-loss process kill: not run without a safe targeted trigger.
- HLS quality, subtitle rendering semantics, audio-track switching, resume
  persistence, and autoplay policy remain independent of an initial handoff.

## Cleanup Gate

The phase is not complete until the fixture is uninstalled through normal UI,
the installed list is rechecked, the test UI/session is closed, the exact
fixture server is stopped, `adb reverse tcp:7000` is removed, temporary CDP/ADB
forwards made by this phase are removed, host port 7000 is closed, and device
loopback connection is refused. Existing unrelated forwards and user state must
be left intact.
