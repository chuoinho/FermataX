# Phase 5B4: Guest/Core Routing Validation

## Status

**`BLOCKED_CHROME_INCOGNITO_INTERACTION_SURFACE`** before fixture installation.

This run did not reach any of the request-boundary classifications defined for
Phase 5B4. It established a fresh Android Chrome Incognito Guest session and
granted Local Network Access (LNA) before the Stremio Guest home had completed
initialization. The Stremio Guest home then rendered normally. Before the
standard Addons UI could be reached, the Chrome page transitioned to an
unusable black/immersive web surface. Android accessibility exposed only the
Chrome container, and the standard UI had no safe, visible Addons control.

The fixture was deliberately **not installed**. Consequently, no catalog,
metadata, stream, or media request was created in this run. The result does not
implicate the fixture protocol, Stremio Core routing, the FermataX player, or
HTML5 rendering.

## Environment and Preflight

- Worktree: `E:\\Chatgpt\\fermata-stremio-web-only` on branch
  `codex/stremio-web-only`, clean before the report was created.
- Device: physical ADB device `15c36230`.
- Android: release `16`, SDK `36`.
- Chrome: `151.0.7922.173`.
- FermataX was not opened.
- The retained direct-stream fixture was the unchanged Phase 4 fixture, bound
  to loopback only. Its safe request log contained no entry from this run.
- Before setup, TCP 7000 was held by the exact fixture `node server.mjs`
  process and `adb reverse tcp:7000 tcp:7000` existed. Both were traced to the
  retained Phase 4 fixture rather than an unrelated service.

## Guest and LNA Evidence

1. The pre-existing Stremio Incognito tab was closed through Chrome's tab UI.
   A fresh Incognito tab was created through Chrome's standard menu.
2. `https://web.stremio.com` was opened with the Chrome address bar. Chrome
   displayed its standard request for `web.stremio.com` to access local-network
   applications and services.
3. LNA was granted through the visible `Allow` button before Guest home
   rendered. The resulting Stremio UI identified the session as `anonymous`.
4. The Guest Board/Home displayed normal upstream content. No sign-in, account
   data, cookie, token, local storage, or Core dispatch API was accessed.

## Blocker Evidence

- While dismissing the unrelated upstream "Streaming server is not available"
  prompt and trying to expose the normal left navigation, the Stremio page
  entered an immersive black surface.
- Chrome DevTools was used only passively to observe the top-level page route;
  it showed `https://web.stremio.com/` and later the UI-entered
  `#/addons` route. No `Runtime.evaluate`, DOM read, storage read, or dispatch
  was used.
- Android's Incognito screenshot capture was black, as observed in Phase 5B2.
  After the transition, accessibility likewise contained only the Chrome web
  container rather than actionable Stremio controls.
- Filtered logcat showed Chrome sandbox-process lifecycle noise but no
  attributable Stremio exception, console exception, CORS/LNA denial, ANR, or
  Chrome main-process crash. This is insufficient to diagnose the transition.

Because the Addons UI was not safely reachable, no install submission was sent.
The fixture log confirms that its last manifest request predates this run; the
only fixture activity during setup was the local protocol listener already
running at preflight. There is therefore no valid evidence for any requested
chain boundary:

| Boundary | Observed result |
| --- | --- |
| Manifest install | Not attempted |
| Catalog | Not attempted |
| Meta | Not attempted |
| Stream | Not attempted |
| MP4 GET / Range / `206` | Not attempted |
| Player surface | Not reached |

## Cleanup

- The test-created Incognito Stremio tab was closed with Chrome's tab UI.
  Chrome then showed only the pre-existing standard tab; no Incognito tab
  remained.
- The fixture was never installed, so no uninstall target existed.
- `adb reverse tcp:7000` was removed.
- The traced fixture `node` process was stopped.
- Host TCP 7000 no longer listened.
- A device loopback `toybox nc` check returned `Connection refused`.
- The temporary CDP forward (`tcp:9222`) and device-side UI dump files were
  removed.
- No fixture response, production code, or test code was modified.

## Change Audit and Next Checkpoint

- Production/test LOC: `0`.
- This report is the only repository change from this phase.
- Not run: fixture install/hydration, catalog, metadata, stream selection,
  Player/MP4 handoff, HLS, seek, subtitles, fullscreen, lifecycle, next-track,
  and torrent.

**Proposed Phase 5B5 checkpoint:** first reproduce and identify the Android
Chrome Incognito black/immersive transition with a strictly UI-only navigation
trace, without fixture installation or production-code changes. Do not retry
the Phase 5B4 install until that interaction surface is stable.
