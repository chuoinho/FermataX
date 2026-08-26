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

The physical evidence does not identify a JavaScript exception, rejected promise, network request,
or renderer restart. A direct comparison was attempted with `com.kododake.aabrowser`, which uses
the same installed Android System WebView (`com.android.webview 145.0.7632.109`). The browser could
be launched, but its independent menu navigation did not complete a valid Stremio page load and it
does not share the authenticated FermataX Stremio session. The upstream comparison is therefore
**BLOCKED/INCONCLUSIVE**, not evidence that the behavior is upstream or Android WebView.

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
| Upstream independent-browser comparison | BLOCKED/INCONCLUSIVE | AABrowser navigation/session unavailable |

No production, test, or build-script code was changed in this follow-up. Consequently no build was
rerun; the previously recorded unit and release-build gates remain the relevant build evidence.

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
surface. Their classification remains **INCONCLUSIVE** pending a valid independent-browser
reproduction or further browser-level evidence. Server-backed playback remains
**BLOCKED_USER_CONFIGURATION**. This result must not be converted into a native fallback
implementation.
