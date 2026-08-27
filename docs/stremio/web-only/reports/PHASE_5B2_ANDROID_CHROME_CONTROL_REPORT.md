# Phase 5B2: Android Chrome Incognito Control Validation

## Result

**`BLOCKED_INCOGNITO_OR_GUEST`**

The connected physical device `15c36230` is running Android 16 (API 36), but it
does not have the Chrome Android package (`com.android.chrome`) installed. A
package-level check found no Chrome package, and the narrowly-scoped Chrome
activity check found no existing Incognito activity.

The phase requires Chrome Android Incognito specifically. Installing Chrome,
using another browser, inspecting Chrome normal tabs, or using an existing
browser profile would exceed its permitted scope, so no substitution was made.

## Preflight

- Worktree HEAD: `eb51ad38`.
- Worktree was clean before Phase 5B2; no production or test file was changed.
- Device: `15c36230`, connected over ADB.
- Android: release `16`, SDK `36`.
- TCP 7000: closed.
- `adb reverse --list`: empty; no `tcp:7000` rule existed.
- The retained fixture directory was not started or modified.
- FermataX was not opened, queried, or changed.
- No Chrome normal tab, cookie, token, storage, history, account, or site data
  was read or modified.

## Chrome and Guest Status

| Check | Observation |
| --- | --- |
| `com.android.chrome` | Not installed on the device |
| Existing Chrome Incognito activity | None observed |
| Isolated Incognito tab | Not created |
| Hosted Stremio page | Not opened |
| Stremio Guest | Not reached |
| External-player setting | Not reached |
| Fixture addon installation | Not attempted |

The device does contain a separate browser package, but it was not opened or
used because it is not Chrome Android and would invalidate this control test.

## Playback Evidence

No Player UI, stream row, MP4 request, Range response, playback-progress,
Chrome MediaSession, console, crash, ANR, or external-app evidence exists: the
mandatory Chrome Incognito environment was unavailable before fixture startup.

No result is assigned to `ANDROID_CHROME_CONTROL_PASS`,
`STREAM_ROUTE_FAILURE`, `PLAYER_MEDIA_FAILURE`, or `CHROME_SECURITY_BLOCK`.

## Cleanup Evidence

- Fixture server: remained stopped.
- TCP 7000: remained closed.
- `adb reverse tcp:7000`: was never created.
- No Incognito tab or addon was created, so no browser cleanup action was
  required.
- FermataX and the real Stremio account were untouched.

## Change Audit and Remaining Matrix

- Production/test LOC: `0`.
- This report is the only Phase 5B2 repository change.
- Not run: fixture protocol recheck, device loopback check, Guest selection,
  fixture installation, MP4 stream selection, playback, HLS, subtitles, seek,
  fullscreen, lifecycle, next-track, and torrent.

Phase 5B2 stops here. Continuing this exact control requires Chrome Android to
be available on a test device, followed by a new explicit authorization to
repeat the Incognito-Guest test.
