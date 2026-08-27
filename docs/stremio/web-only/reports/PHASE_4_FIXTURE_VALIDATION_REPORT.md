# Phase 4: Local Direct-Stream Fixture Validation

## Scope

- Device: physical Android device over ADB.
- Fixture: a temporary local Stremio addon reachable only through `adb reverse`.
- Excluded: torrent, production-code changes, account API calls, DOM/Core dispatch, and profile/storage edits.

## Account Safety

The initial installed-addon snapshot contained exactly these six synced addons:

1. Cinemeta
2. YouTube
3. WatchHub
4. Public Domain Movies
5. OpenSubtitles v3
6. Local Files (without catalog support)

Only `Fermata Local Validation` was installed for this validation. No existing addon was reordered, changed, or removed.

The fixture catalog, meta, and stream-list routes were observed from the FermataX WebView with `Origin: https://web.stremio.com`. The MP4 item reached the Stremio detail and stream-selection UI. Activating the visible play control did not emit a media request and Fermata's MediaSession remained `NONE`; therefore HLS, seek, subtitles, fullscreen, lifecycle, and next-track tests were not started.

## Fixture Removal And Origin Reset

- Stremio Web outside FermataX showed the original six addons and did not expose the fixture for UI removal.
- FermataX continued to show the fixture after normal reload and after Stremio UI logout/login.
- The exact WebView DevTools target for `https://web.stremio.com` acknowledged `Storage.clearDataForOrigin` with `storageTypes: all`.
- After FermataX restart, the installed-addon list returned to the original six entries and `Fermata Local Validation` was absent.
- The account session was restored by Stremio despite the origin reset. No other origin was inspected or cleared.

## Cleanup Evidence

- Fixture Node server on TCP 7000: stopped.
- `adb reverse tcp:7000 tcp:7000`: removed.
- TCP 7000 listener: absent after cleanup.
- Temporary DevTools ADB forward used for exact-origin clearing: removed.

The fixture directory under the Windows temporary folder remains because the environment rejected the recursive deletion command before it ran. It contains only the local test media, HLS segments, WebVTT, request log, and fixture server script; it is no longer reachable from the device or listening on a port.

## Result

Account and FermataX addon state are restored to the original six-addon set. Playback acceptance remains blocked at the direct MP4 play-control boundary; no unsupported matrix item was claimed as passing.
