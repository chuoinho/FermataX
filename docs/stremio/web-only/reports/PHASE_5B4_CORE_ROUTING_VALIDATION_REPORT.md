# Phase 5B4: Guest/Core Addon Activation and Direct MP4 Handoff

## Final Classification

**`PHASE_5B4_PASS`**.

A Chrome Incognito Guest session completed the real standard-UI route from the
installed local addon to the upstream Player and an actual direct-MP4 media
request. This replaces the earlier provisional blocker classification: the
physical device provided a usable UI surface after it was changed to Chrome's
standard Desktop-site viewport. No URL route was constructed, no DOM/Core
action was scripted, and no account or browser storage was read or changed.

This result proves the Phase 5B4 boundary only. It does not claim playback
quality, seek, subtitles, HLS, lifecycle, next-track, torrent, or FermataX
WebView-player parity.

## Environment and Preconditions

- Worktree: `E:\\Chatgpt\\fermata-stremio-web-only`, branch
  `codex/stremio-web-only`.
- Device: physical ADB device `15c36230`, Android `16` (SDK `36`).
- Browser: Chrome `151.0.7922.173`, anonymous Incognito Guest. No real Stremio
  account was used, read, or modified.
- FermataX was not opened.
- The direct-stream validation fixture ran only on host loopback and was
  reached through `adb reverse tcp:7000 tcp:7000`.
- Chrome's Local Network Access permission had already been granted through
  its normal prompt before Guest/Core initialization.
- Chrome's standard `Desktop site` UI setting was enabled temporarily to make
  the Discover selector and cards reachable. This changes browser presentation
  only; it did not alter the fixture, addon, Core state, or security policy.

## Exact Standard-UI Flow

1. The canonical Addons UI showed `Fermata Local Validation` as `Installed`
   after the earlier real install and one normal reload.
2. In Discover, the visible catalog selector listed `fermata-local`; it was
   selected through its normal bounded UI control.
3. The visible `Fermata Local MP4` card was selected once through its bounded
   UI control.
4. Stremio navigated to its normal detail route and displayed the fixture
   metadata and the `Local MP4` stream row.
5. The `Local MP4` stream row was selected once through its normal UI control.
6. Stremio navigated to its normal `#/player/...` surface. The visible Player
   control reported `Pause`, which is direct UI evidence of started playback.
7. Testing stopped at the first media handoff boundary. No HLS, seek,
   fullscreen, lifecycle, next-track, torrent, or deliberate subtitle test was
   performed.

## Safe Request Evidence

All browser-to-fixture records below are request path classes only; media path
details and route payloads are redacted.

| Boundary | Observed request/result |
| --- | --- |
| Addon manifest | `GET /manifest.json` -> `200` |
| Catalog | `GET /catalog/...` -> `200` |
| Metadata | `GET /meta/...` -> `200` |
| Stream list | `GET /stream/...` -> `200` |
| Player preflight | `HEAD /media/<redacted>.mp4` -> `200` |
| Direct media | `GET /media/<redacted>.mp4`, `Range: bytes=0-` -> `206` |
| Later media ranges | additional video `GET` requests -> `206` |

The direct media request was identified by passive CDP as a `Media` request;
the fixture independently recorded `Sec-Fetch-Dest: video`, `no-cors`, and
normal finish lifecycle. Passive CDP also observed the preflight `Fetch` HEAD
and the `206` media response. Fixture catalog, metadata, and stream requests
originated from `https://web.stremio.com` with normal CORS request shape.

The player also fetched the fixture's declared subtitle resource automatically.
That is an observed upstream player side effect, not a subtitle acceptance
claim; subtitle selection/rendering was intentionally not tested.

## Boundary Result

| Request boundary | Result |
| --- | --- |
| Guest addon collection | PASS: fixture stayed Installed after reload |
| Discover source selection | PASS: `fermata-local` was visible and selected |
| Catalog | PASS: browser request returned `200` |
| Metadata | PASS: browser request returned `200` |
| Stream selection | PASS: browser request returned `200` |
| Player handoff | PASS: normal `#/player/...` surface and `Pause` control |
| Direct MP4 | PASS: real video request with `Range` and `206` |

The formerly suspected Guest/Core hydration failure was an invalid conclusion:
the prior action only submitted the Add-addon dialog and did not activate the
separate modal `Install` control. This run used the actual `Install` control.

## Cleanup Evidence

- Player was exited through normal browser Back before cleanup.
- `Fermata Local Validation` was removed once through its canonical Addons
  `Uninstall` UI. A post-action Installed DOM no longer contained the fixture.
- The sole Incognito Stremio tab was closed through Chrome's tab-switcher UI;
  Chrome then reported only one normal tab and no Incognito Stremio tab.
- `adb reverse tcp:7000` was removed.
- The temporary CDP forward `tcp:9222` was removed. Pre-existing unrelated
  ADB forwards were left untouched.
- The exact fixture Node process was stopped.
- Host TCP port `7000` had no listener after stop.
- Device loopback connection to TCP `7000` returned `Connection refused`.
- Chrome automatic rotation was restored after the temporary portrait test.
- The passive observer exited after its bounded collection window.

## Change Audit and Remaining Matrix

- Production/test LOC: `0`.
- Repository change: this report only.
- Not run: HLS, seek, subtitle selection/rendering, fullscreen, lifecycle,
  next-track, torrent, and FermataX WebView playback parity.

## Checkpoint

Phase 5B4 is complete. The next authorized work, if any, is **Phase 5B5 only**:
define the separate acceptance scope and controls for the remaining playback
matrix. Do not infer those items from this direct-MP4 handoff result.
