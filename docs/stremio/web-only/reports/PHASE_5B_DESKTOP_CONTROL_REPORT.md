# Phase 5B: Desktop Direct-Stream Control Validation

## Result

**`BLOCKED_GUEST_UNAVAILABLE`**

The required isolated desktop Chromium environment could not be created in this
execution environment. The terminal rejected the attempt to launch the installed
Chrome executable with a new `--user-data-dir` and loopback remote-debugging
configuration before any command in that launch sequence ran. No Chrome process,
profile, DevTools listener, Stremio page, Guest session, or addon installation
was created.

The only browser-control surface available to the agent was the Codex in-app
browser. It was not used because it is not the required standard desktop Chromium
process with the newly-created, explicitly isolated profile. No user Chrome
profile, Stremio account, FermataX instance, or Android device was accessed or
changed in this phase.

Accordingly, this report does not assign any of the playback classifications
(`DESKTOP_CONTROL_PASS`, `FIXTURE_ROUTE_FAILURE`, `PLAYER_LOAD_FAILURE`, or
`BROWSER_SECURITY_BLOCK`). None can be evidenced without the required Guest
control environment.

## Preflight

- Worktree HEAD: `380cba9f` at the start of Phase 5B.
- Worktree: clean at the start and no production or test file was changed.
- Exact retained fixture directory:
  `C:\Users\ttanh\AppData\Local\Temp\fermatax-stremio-direct-fixture-phase4`.
- Fixture was initially stopped; no process using that directory was found and
  TCP 7000 was closed.
- No ADB command, ADB reverse rule, or FermataX interaction occurred.

## Fixture Protocol Evidence

The unchanged retained fixture was temporarily started as its existing Node
server, bound to `127.0.0.1` only. The following local protocol checks passed.
No complete URL, query, token, or media path is recorded here.

| Check | Observed result |
| --- | --- |
| Addon manifest | `200` JSON |
| Movie catalog | `200` JSON |
| MP4 metadata | `200` JSON |
| MP4 stream resource | `200` JSON |
| Subtitle resource | `200` JSON |
| MP4 `HEAD` | `200`, `Content-Type: video/mp4`, `Accept-Ranges: bytes` |
| MP4 ranged GET | `206`, valid `Content-Range`, `Content-Length: 1024` |
| CORS origin | `Access-Control-Allow-Origin: https://web.stremio.com` |
| Stream object | Has `url`; loopback `http` scheme; recognizable `.mp4` path |
| Web capability | `behaviorHints.notWebReady` is `false` |
| External/torrent fields | No `externalUrl`, magnet URL, or `infoHash` |

The fixture request log confirmed the protocol probes and showed the expected
origin header. Range positions have been redacted from this report.

## Desktop Control Status

- Chromium version: not observed; no permitted desktop Chromium process started.
- Temporary profile path: planned under Windows Temp; confirmed absent after the
  rejected launch, so no profile cleanup was necessary.
- Guest status: not reached.
- `Play in external player`: not reached in desktop Guest. The existing Phase 5A
  FermataX observation remains `Disabled`, but it was not reused as desktop
  evidence.
- Fixture addon installation: not attempted.
- Stream-card click: not attempted.
- Route transition, HTML5 player UI, MP4 request shape, response in Chromium,
  video playback, console warnings/errors, and Runtime exceptions: not observed.

## Cleanup Evidence

- Fixture server process was stopped after the protocol check.
- TCP 7000: confirmed closed after shutdown.
- TCP 9224: confirmed closed; no remote-debugging endpoint was created.
- No desktop Chromium process associated with a temporary profile was started.
- No temporary profile directory exists.
- The original Phase 4 fixture directory was retained unchanged as required.
- FermataX and the real Stremio account were not opened or modified.

## Change Audit and Remaining Matrix

- Production/test LOC: `0`.
- This report is the only Phase 5B repository change.
- Not run: desktop Guest addon installation, MP4 stream-card activation, HTML5
  playback, HLS, torrent, subtitles, seek, fullscreen, lifecycle, and
  next-track.

Phase 5B stops at this blocker. A later desktop control attempt requires an
execution surface that can launch a clean Chromium profile without falling back
to a user profile or the Codex in-app browser.
