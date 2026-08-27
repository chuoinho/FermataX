# Phase 5B3: LNA/Core Worker Retry Diagnosis

## Final Classification

**`INSTALL_PASS_FIRST_ATTEMPT` + `PLAYER_CONTROL_FAILURE` (pre-player state
boundary).**

The first permitted UI install succeeded, so no second install or worker-reload
attempt was sent. This does not prove that re-creating the Core worker is the
cause of the Phase 5B2 result. It does establish a meaningful differential:
after Chrome Local Network Access (LNA) had been granted before Stremio Guest
completed its initialization, the manifest request completed and the Addons UI
reported `Fermata Local Validation` as installed.

The control did not reach Player. A fixture catalog deep link fetched and
displayed the MP4 item, but simultaneously showed "This addon is not installed.
Install now?" A direct fixture metadata route then reported "No addons were
requested for this meta!" and produced no fixture `meta` or `stream` request.
Therefore the installed-addon presentation state did not become active Core
addon-routing state in this Guest session. No second install was allowed after
the first success, and no Player/MP4 control was attempted.

## Upstream Worker Boundary

The audited upstream `createTransport.ts` creates its Core `Worker` at module
scope, then creates the Bridge used by `init`, state reads, and dispatch. This
supports treating worker creation as a meaningful lifecycle boundary, but this
phase does not attribute the earlier failure to it because attempt 2 was not
needed. Source: [upstream createTransport.ts](https://raw.githubusercontent.com/Stremio/stremio-web/development/src/core/createTransport.ts).

## Environment and LNA Evidence

- Device: physical ADB device `15c36230`.
- Android: release `16`, SDK `36`.
- Chrome: `151.0.7922.173`.
- Chrome was used only through an Incognito tab. Its first-run flow was set to
  not sign in; Stremio displayed anonymous/Guest UI.
- Chrome showed the normal Local Network Access prompt for `web.stremio.com`.
  It was allowed for this test under the user's explicit approval.
- Guest setting `Play in external player`: observed as `Disabled` before the
  install attempt.
- FermataX, real Stremio accounts, normal Chrome-tab contents, cookies, tokens,
  storage, and history were not read or modified.

## Fixture and Safe Logging Evidence

The retained fixture was temporarily started unchanged on loopback and reached
through `adb reverse tcp:7000 tcp:7000`.

| Transport check | Result |
| --- | --- |
| PC MP4 `HEAD` | `200`, `video/mp4`, `Accept-Ranges: bytes` |
| PC MP4 ranged GET | `206`, valid `Content-Range` |
| Device manifest through reverse | `200` |
| Install attempt 1 manifest request | one `GET`, `200`, response finished |
| Safe request origin | `https://web.stremio.com` |
| `Sec-Fetch-Mode` | `cors` |
| `Sec-Fetch-Site` | `cross-site` |
| `Sec-Fetch-Dest` | `empty` |
| Abort evidence | none; lifecycle was `finish`, then `close_after_finish` |

Temporary fixture logging recorded only timestamp, method, path without query,
Origin, the three `Sec-Fetch-*` fields, status, lifecycle, abort state, and an
available `Content-Length` header value. It did not record cookies,
authorization, referer, tokens, or response bodies. The Node response API did
not expose a reliable per-response actual-byte count without wrapping response
writes, so no synthetic byte value was recorded.

The logging patch was applied only for this run and was restored before final
verification. Fixture server SHA-256 before and after is identical:

```text
458CF3C88FC51462BB2099676E3C279B75463CC95EA9E539266E98194439B733
```

The requested log cleanup could not run because the environment rejected the
targeted content-clear command before execution. Instead, a recorded line
boundary separated protocol probes from the two relevant browser phases; no
wide Temp cleanup was attempted.

## Attempt Results

### Attempt 1

- LNA had been granted in the active Incognito session before Guest completed
  loading.
- One standard Addons-UI submission was sent.
- Fixture received one manifest request with the safe headers above and returned
  `200` with normal finish lifecycle.
- Stremio Addons UI displayed `Fermata Local Validation`, `Installed`, and
  `Uninstall`.
- Result: **pass**.

### Attempt 2 / Worker Reload

Not run. The Phase 5B3 rule requires skipping attempt 2 when attempt 1 succeeds.
No reload was performed after the successful install.

## MP4 Control Result

The fixture catalog request succeeded and the UI displayed `Fermata Local MP4`.
However, that same deep-link view treated the addon as not installed. A direct
metadata route produced the upstream UI message that no addons were requested
for the meta; the fixture received no `meta`, `stream`, or MP4 request after
that point.

- Player UI/control bar: not reached.
- MP4 Range request / `206` during playback: not reached.
- Playback progress: not reached.
- Chrome MediaSession: no Chrome playback session observed.
- Crash, ANR, or external-app launch: none observed.

The result is a `PLAYER_CONTROL_FAILURE` only in the broad sense required by
the phase classification: the intended Player control could not be exercised.
It is specifically a pre-player Core routing-state failure, not evidence of an
HTML5 decoder or renderer failure.

## Cleanup Evidence

- The Incognito Stremio tab was closed with Chrome's tab-switcher UI; no
  Incognito activity remained.
- The fixture was no longer listed in the Addons UI at cleanup, so no safe
  uninstall target was available. Closing all Incognito tabs discarded its
  Guest partition as required.
- `adb reverse tcp:7000` was removed.
- Fixture server was stopped.
- TCP 7000: closed.
- Device loopback request after cleanup: connection failure as expected.
- Fixture logging patch: restored; SHA-256 matches the preflight value.
- FermataX was not opened after cleanup.

## Change Audit and Remaining Matrix

- Production/test LOC: `0`.
- This report is the only Phase 5B3 repository change.
- Not run: attempt 2, worker reload, Player control, MP4 playback, HLS,
  subtitles, seek, fullscreen, lifecycle, next-track, and torrent.

Phase 5B3 stops at the active-addon/Core-routing boundary. It does not justify
changing fixture responses, Chrome security, FermataX, or the Stremio player.
