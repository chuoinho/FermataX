# Phase 1 Report - Hosted Stremio Web Addon

## Scope

Implement the minimum inline Stremio Web addon. No external-player, native player, torrent/server,
Stremio Core or `aauto.aar` work was performed.

## Implementation

- `WEB_STREMIO=true` skips legacy `:stremio` and adds one Web-owned `stremio_fragment`.
- `StremioWebAddon` uses dedicated `stremio_web` preferences and the official hosted home URL.
- `StremioWebFragment` reuses generic browser fullscreen/back and exposes Stremio search for voice.
- `StremioWebClient` rejects only main-frame `intent:` and `stremio:` navigation. It preserves
  Stremio client type through renderer replacement and does not intercept HTTPS/media requests.
- Shared Web shell adds isolated last-URL preference and two client factory hooks; the Web Browser
  default remains `web` plus `http://google.com`.

## Build Evidence

- `gradlew projects -PWEB_STREMIO=true`: PASS; `:stremio` absent.
- `gradlew :web:dependencies --configuration mobileDebugRuntimeClasspath -PWEB_STREMIO=true`:
  PASS; no `jlibtorrent` dependency in `:web`.
- `gradlew :web:testMobileDebugUnitTest -PWEB_STREMIO=true`: PASS.
- `gradlew :fermata:assembleAutoRelease -PWEB_STREMIO=true`: PASS.
- `gradlew :fermata:packageAutoReleaseUniversalApk -PWEB_STREMIO=true`: PASS.
- `aauto.aar` SHA-256 before and after build:
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

## Physical Device Evidence

Device: Redmi Note 8, Android 16, serial `15c36230`. The signed Auto universal APK was installed
over v303, preserving application data. Stremio appeared in Addons, was enabled, appeared in the
navigation list, and opened `https://web.stremio.com/#/`. The authenticated Stremio avatar and
catalog rendered, proving WebView session persistence. No crash, renderer-loss or ANR was observed.

Stremio Web displayed `Streaming server is not available.`. Therefore no valid HTTP/HLS playback,
seek, fullscreen/back-during-video, or lifecycle-video evidence exists yet.

An upstream trailer was opened from a Stremio detail page and rendered/played inline under the
`https://web.stremio.com/#/player/...` route. FermataX remained foreground and no external-player
chooser or external application appeared. This is evidence for the hosted HTML5 trailer route
only; it is not evidence for server-backed stream playback, browser custom-view fullscreen, or
the unobserved upstream `Install` action.

When the player's own back affordance returned to the expected `#/detail/movie/...` route, the
hosted detail surface stayed blank for at least 20 seconds. The app remained responsive, no
renderer-loss callback, crash, or ANR was observed, and a subsequent Android Back returned to the
Dashboard because WebView had no native history entry. This must be reproduced with an upstream
browser comparison and attributed before back/lifecycle acceptance can pass.

### Phase 1B/1C Follow-up QA

Follow-up QA ran on the same physical device and signed release build. The hosted home was restored
explicitly through `https://web.stremio.com/#/`, then the public `Obsession` detail
(`#/detail/movie/tt37287335/tt37287335`) and its 2:13 YouTube trailer were used as a repeatable
HTML5 fixture. The trailer played inline under its `#/player/...` hash route with no external
chooser or external application.

**Fullscreen entry: PASS (browser custom view).** After revealing player controls, selecting the
Stremio player control labelled `Enter fullscreen mode` attached a child WebView under
`browserFullScreenView`. This is the container populated by `FermataChromeClient.onShowCustomView()`;
the original `browserWebView` remained outside that container. The route remained `#/player/...`
and playback was observed at 00:00:52 of 00:02:13. This is browser custom-view fullscreen, not a
FermataX native player or a CSS-only replacement. No crash, ANR, renderer-loss callback, or
external-player handoff was observed.

**Fullscreen exit with Android Back: FAIL.** From that verified custom-view state, Android Back
removed the visible custom view but navigated the hosted app to
`#/detail/movie/tt37287335/tt37287335` rather than returning to the inline player. The detail body
was blank after two seconds and remained blank for more than 20 seconds. Therefore neither route
retention nor playback-position retention can be accepted. This is recorded as an observed failure,
not as proof that `FermataChromeClient` is the cause.

**Inline lifecycle: PASS.** Before this follow-up, the same trailer was played inline, backgrounded
with Android Home, and restored through the FermataX launcher. The player route remained active and
controls showed approximately 00:00:41 of 00:02:13 after return, without an external handoff or
reload to zero.

**Detail-return blank surface: reproduced.** The controlled `detail -> trailer -> fullscreen ->
Android Back` run above is the third observed reproduction when combined with the two prior
controlled trailer runs. In every observed failure the URL changed to the expected detail hash,
the native activity stayed responsive, and no `AndroidRuntime` crash, ANR, WebView renderer-loss,
or SSL/HTTP error attributable to FermataX was found in filtered logcat. Native history did contain
a player/detail navigation entry in the latest run: its Forward action returned to `#/player/...`,
but that player surface was also blank. Reopening FermataX and loading the hosted home recovered
the catalog, so this is not persistent profile or storage corruption.

The physical evidence did not initially identify a JavaScript exception, rejected promise, network
request, or renderer restart. That initial classification was superseded by the independent-browser
comparison below.

| Scenario | Result | Observed evidence |
| --- | --- | --- |
| Inline trailer playback | PASS | `#/player/...`, 2:13 trailer, no external handoff |
| Inline trailer -> background -> resume | PASS | Same player route; position about 00:00:41 |
| Player control -> browser custom view | PASS | `browserFullScreenView` received child WebView |
| Custom view -> Android Back | FAIL | Detail hash, then blank hosted surface |
| Detail route after player return | FAIL | Reproduced blank surface three times |
| Player route via native Forward after failure | FAIL | Expected player hash, blank surface |
| Player fullscreen exit button | BLOCKED | State could not be restored after the Back failure without resetting the hosted route |
| Three repeated fullscreen cycles | BLOCKED | First Android Back invalidates the player/detail surface |
| Fullscreen -> background -> resume | BLOCKED | Cannot hold a valid fullscreen session after exit failure |
| Orientation / lock-screen fullscreen | BLOCKED | Not attempted after the exit failure |
| Upstream independent-browser comparison | PASS | Reproduced in AABrowser; desktop Chromium returns to detail |

No production, test, or build-script code was changed in this follow-up. Consequently no build was
rerun; the previously recorded unit and release-build gates remain the relevant build evidence.

### Independent Browser Classification

The independent comparison was completed on the same Redmi Note 8 without using FermataX's
`StremioWebFragment`, `FermataChromeClient`, preferences, or navigation code. `com.kododake.aabrowser`
opened public `web.stremio.com`, loaded the same `Obsession` detail, played the same trailer, and
then received Android Back. After eight seconds it showed the same blank hosted surface. Remote
inspection showed the expected detail hash while `#app` had been unmounted to an empty 20-byte
element. Reloading that same hash restored the detail page.

An error listener installed before the independent trailer run captured this exception from the
hosted Stremio bundle:

```text
Uncaught TypeError: Cannot read properties of undefined (reading 'setActionHandler')
source: https://web.stremio.com/.../scripts/main.js
```

Both the FermataX WebView and AABrowser reported `navigator.mediaSession` as absent, including an
absent `setActionHandler`. The installed Android System WebView provider was
`com.android.webview 145.0.7632.109`. In contrast, the same public detail -> trailer -> browser
Back flow in independent desktop Chromium restored the detail route and DOM; its only console
warning was a non-fatal YouTube iframe attachment warning.

Classification is therefore **UPSTREAM_STREMIO_WEB on Android WebView**: the hosted bundle invokes
the unsupported Media Session API without a guard on this WebView path, then leaves the application
root empty. It is not attributable to FermataX integration. No production workaround is applied:
injecting a Media Session shim, reloading every Back, or manipulating Stremio DOM/router state would
violate the hosted-app boundary and could conceal upstream state errors.

### Streaming Server Status

Stremio Web still reports that its streaming server is unavailable. No streaming-server URL was
configured or changed, no server was installed, and no torrent or public server was used. Actual
server-backed HTTP/HLS playback, seek, subtitle/audio-track handling, and its fullscreen/lifecycle
matrix remain **BLOCKED_USER_CONFIGURATION**. The trailer evidence above must not be interpreted as
server-backed playback evidence.

## Status

**PARTIAL.** Addon registration, hosted-session retention, inline trailer playback, inline
background/resume, and browser custom-view fullscreen entry are observed. Fullscreen exit with
Android Back and player-to-detail navigation are not accepted because they produce the hosted blank
surface. Independent AABrowser evidence classifies that failure as **UPSTREAM_STREMIO_WEB on
Android WebView**, not a FermataX integration defect. Server-backed playback remains
**BLOCKED_USER_CONFIGURATION**. This result must not be converted into a native fallback
implementation.

## Phase 2A/2B - WebView Media Session Compatibility and Control Bridge

Android System WebView `145.0.7632.109` on the Redmi Note 8 does not provide
`navigator.mediaSession`. The hosted Stremio bundle calls `setActionHandler` during player
cleanup, which caused the previously observed unguarded exception and blank `#app` surface.

FermataX now supplies a Stremio-only, document-start compatibility layer only when the browser API
is absent. It is restricted to the exact `https://web.stremio.com` main-frame origin. The shim
supports only the API that the current Stremio Web player invokes: `playbackState`, `metadata`,
`MediaMetadata`, and `play`/`pause`/`nexttrack` handlers. The native bridge uses AndroidX WebKit
`addWebMessageListener`; it does not use `addJavascriptInterface`, DOM selectors, router changes,
stream extraction, a native renderer, or a player URL.

The temporary native MediaSession delegate is control-only. It claims controls only while the
Stremio fragment is active and the current document has a valid, non-`none` playback state plus a
play or pause handler. It dispatches only fixed play, pause, and next callbacks. It owns no
decoder, source, item, URL, position, duration, progress, audio focus, or streaming transport.
Fragment switch/hide, document navigation, session close, WebView destruction, and renderer
replacement release the claim. Each document has a fresh opaque token, so stale page messages are
ignored.

### Phase 2 Physical Evidence

Device: Redmi Note 8, Android 16, serial `15c36230`; signed Auto universal release APK.

- DevTools on the hosted Obsession detail confirmed `navigator.mediaSession` is an `object`, the
  Fermata shim reports version `1`, and `#app` has rendered children.
- The 2:13 Obsession YouTube trailer rendered and played under `#/player/...`; Chromium requested
  audio focus, while `dumpsys media_session` showed Fermata's control-only state `PLAYING` with
  play, pause, toggle, and no advertised next action.
- ADB `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_PLAY`, and two `KEYCODE_MEDIA_PLAY_PAUSE` operations
  produced exactly the expected `PAUSED`, `PLAYING`, `PAUSED`, and `PLAYING` MediaSession states.
  `KEYCODE_MEDIA_NEXT` left state unchanged because this page did not register `nexttrack`.
- `detail -> trailer -> browser custom fullscreen -> Android Back` returned to a rendered Obsession
  detail surface. Filtered logcat contained no `setActionHandler` exception, crash, or ANR.
- Leaving Stremio for Dashboard released its claim immediately: `active=false`, `state=NONE`, and
  `actions=0`. A subsequent Play key was handled by the native media engine, not the Stremio page.

### Phase 2 Gates

- `gradlew projects -PWEB_STREMIO=true`: PASS; `:stremio` is absent.
- `gradlew :web:dependencies --configuration mobileDebugRuntimeClasspath -PWEB_STREMIO=true`:
  PASS; no `jlibtorrent` or new player/torrent dependency.
- `gradlew :web:testMobileDebugUnitTest -PWEB_STREMIO=true`: PASS.
- `gradlew :fermata:assembleAutoRelease :fermata:packageAutoReleaseUniversalApk
  -PWEB_STREMIO=true`: PASS.
- `aauto.aar` SHA-256 remained
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.
- Universal APK signer SHA-256:
  `A8:6D:57:6F:F1:EC:0E:32:45:F5:A6:15:2C:5D:8D:66:B5:DF:8B:B5:10:82:0D:75:DC:61:11:0B:19:C3:AE:B4`.

### Remaining Limits

Automotive/DHU hardware-host controls were not attached in this run:
`AUTOMOTIVE_HOST_NOT_VERIFIED`. The Web page did not expose `nexttrack`, so next callback dispatch
is covered by unit behavior but not physical-player evidence. Streaming-server-backed playback,
seek, subtitle/audio-track handling, and renderer-replacement recovery remain
`BLOCKED_USER_CONFIGURATION` or unobserved. The hosted HTML5/Chromium renderer remains the sole
player.
