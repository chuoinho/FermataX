# Phase 5B2: Android Chrome Incognito Control Validation

## Result

**Pre-player addon-fetch failure; none of the four Player classifications is
applicable.**

Chrome Android Incognito and Stremio Guest were reached successfully, but the
single permitted fixture-install submission failed before the addon became
installed:

```text
Failed to fetch
Failed to get addon manifest from [redacted loopback manifest]
```

The fixture server did receive one `GET` for the manifest and returned `200`.
That request had no `Origin` header. The Stremio UI nevertheless reported fetch
failure and did not install `Fermata Local Validation`; consequently no catalog,
stream row, Player UI, MP4 request, or playback test was possible.

This is **not** classified as `CHROME_SECURITY_BLOCK`: Chrome presented the
normal Private Network Access permission prompt and it was accepted for this
Incognito test after explicit user confirmation, while filtered Chrome logcat
contained no mixed-content, CORS, Private Network Access, or access-control
message. The available evidence establishes a manifest-fetch boundary failure,
but not its precise browser-security or Stremio-Core cause. No fixture change or
retry was made after that first control result.

## Environment and Isolation

- Device: physical ADB device `15c36230`.
- Android: release `16`, SDK `36`.
- Chrome: `151.0.7922.173`.
- A Chrome Incognito tab was created through Chrome's standard UI. Chrome's
  first-run UI was explicitly set to not sign in; no real Stremio account was
  used.
- Hosted Stremio showed the anonymous/Guest UI. No Chrome normal-tab content,
  cookie, token, storage, history, account, or site data was read.
- FermataX was not opened, queried, or modified.

## Fixture and Transport Evidence

The exact retained Phase 4 fixture was started unchanged and bound only to
`127.0.0.1:7000`.

| Check | Observed result |
| --- | --- |
| PC MP4 `HEAD` | `200`, `Content-Type: video/mp4`, `Accept-Ranges: bytes` |
| PC MP4 ranged GET | `206`, valid `Content-Range`, 1024-byte response |
| ADB reverse | `tcp:7000` mapped to PC `tcp:7000` |
| Device loopback manifest | `200` through the reverse rule |
| Chrome PNA prompt | Presented for `web.stremio.com`; allowed for this test |
| Fixture installation request | One UI submission only; server observed manifest `GET 200` |
| Addon installation result | Failed; fixture was not installed |

No full loopback URL, query, token, or media path is recorded in this report.
The fixture request log distinguishes the PC protocol probes from the later
device manifest check and single UI-driven manifest request.

## Player and Media Evidence

- External-player setting: not observed. The manifest fetch failure occurred
  before the required in-Guest setting visit; this must not be inferred from
  Phase 5A's FermataX setting.
- Fixture stream row: not reached; the requested pre-activation screenshot
  could not be produced.
- Player UI/control bar: not reached.
- MP4 request, Range request, `206` response during playback: not reached.
- Playback progress of five seconds: not reached.
- Chrome MediaSession: no Chrome playback session observed.
- Crash, ANR, external-app launch: none observed.

Android blocks normal screen capture of Incognito content; the captured frames
were black. UI accessibility snapshots, the visible failure text, fixture
request log, and MediaSession dump were used instead. This limitation did not
justify leaving Incognito or using a normal Chrome tab.

## Cleanup Evidence

- The Stremio Incognito tab was closed using Chrome's tab-switcher UI. No
  Incognito activity remained afterward.
- The fixture was never installed, so no uninstall action was available or
  needed.
- `adb reverse tcp:7000` was removed.
- Fixture Node server was stopped.
- TCP 7000: confirmed closed.
- Device loopback access after cleanup: connection failure as expected.
- No Chrome data or site data was cleared, and no normal Chrome tab was opened
  or changed.

## Change Audit and Remaining Matrix

- Production/test LOC: `0`.
- This report is the only Phase 5B2 repository change.
- Not run: external-player setting verification, addon installation success,
  MP4 stream selection, Player playback, HLS, subtitles, seek, fullscreen,
  lifecycle, next-track, and torrent.

The control checkpoint stops at the manifest-install boundary. A new phase must
first diagnose the guest manifest-fetch path without changing browser security
or assuming it is a renderer/player failure.
