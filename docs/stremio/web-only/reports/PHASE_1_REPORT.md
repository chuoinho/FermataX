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

## Status

**PARTIAL.** Addon registration, hosted-session retention, and one inline trailer route are
observed. Phase 1B remains blocked on a user-configured working streaming server and playable
HTTP/HLS stream. Phase 1C additionally has an observed detail-return blank surface to reproduce
and attribute. This result must not be converted into a native fallback implementation.
