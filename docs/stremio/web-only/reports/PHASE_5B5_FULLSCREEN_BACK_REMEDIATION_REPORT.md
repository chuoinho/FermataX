# Phase 5B5 Follow-up: Browser Fullscreen Back

## Result

**F6 PASS on the physical Android device.** Phase 5B5 remains **PARTIAL** only
because the required explicit subtitle-selection interaction still has not
been observed. This follow-up does not claim that remaining matrix item.

## Root Cause

The original F6 run proved that one physical Android Back generated two
application-level decisions:

1. fullscreen exit was requested;
2. after the custom WebView view detached, a second immediate Back reached
   `WebBrowserFragment` and dispatched Web history.

The latter navigated the hosted Stremio player back to the detail route and
released the control-only MediaSession. It was not a Stremio route change or a
media-renderer failure.

## Change

- `WebBrowserFragment` now arms a one-shot, 250 ms suppression window only
  after it has accepted `EXIT_FULLSCREEN`.
- A subsequent Back while browser fullscreen is already false is consumed only
  inside that window; all ordinary history and parent Back behavior is
  unchanged.
- `history_back_suppressed` is a privacy-safe diagnostic event, distinct from
  `history_back_dispatched`.
- The custom-view ownership cleanup added before this guard remains in place:
  it clears callback ownership before invoking Chromium's callback, preventing
  reentrant duplicate detach handling.

## Physical Evidence

- Device: `15c36230`, Android 16.
- Artifact: signed Auto release universal APK built with `WEB_STREMIO=true` and
  installed successfully over the existing package.
- Fixture route: normal visible Stremio UI selected the local direct-MP4 item;
  fixture traffic showed HTTP Range playback and the player reached `PLAYING`.
- Before Back: visible route classified as Player, browser custom fullscreen
  was present, and the visible control changed to `Exit fullscreen mode`.
- After exactly one Android Back: visible route remained Player, custom
  fullscreen was gone, `Enter fullscreen mode` returned, and playback stayed
  active.
- `dumpsys media_session` after Back showed `FermataMediaService` in
  `PLAYING` with `Fermata Local MP4` metadata.
- The exported, redacted diagnostics report recorded
  `back_exit_requested` followed 26 ms later by
  `history_back_suppressed`. There was no following
  `history_back_dispatched` in that test session.

The earlier failing session remains visible in the same report as historical
evidence only: it has `back_exit_requested` followed by
`history_back_dispatched`. The new session is separately identified by its
event sequence and process lifetime.

## Automated Verification

Passed:

- `:web:testAutoDebugUnitTest`
- `:web:testMobileDebugUnitTest`
- `:fermata:testAutoDebugUnitTest --tests me.aap.fermata.diagnostics.DiagnosticSanitizerTest`
- signed `:fermata:bundleAutoRelease`
- signed `:fermata:packageAutoReleaseUniversalApk`

## Cleanup

- FermataX was force-stopped after the playback check.
- The test `adb reverse tcp:7000` mapping was removed.
- The fixture Node process was stopped.
- Host TCP port 7000 no longer listened; the device loopback probe returned
  `Connection refused`.
- The local fixture directory was left stopped but could not be deleted by the
  execution environment's destructive-file-operation guard. It contains only
  the test MP4, fixture server script, and request log; no process or ADB
  mapping still refers to it.

## Scope

- Production/test code changed only for this F6 lifecycle repair and its
  diagnostics allowlist coverage.
- No account credentials, cookies, addon storage, or stream URLs were read or
  recorded.
- Still unverified: explicit subtitle selection, torrent transport, next-track,
  renderer-loss recovery, AA/DHU hardware controls, HLS quality selection,
  audio-track switching, and persistent resume semantics.
