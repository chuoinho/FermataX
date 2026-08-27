# Phase 5B4: Guest/Core Addon Activation and Routing Validation

## Final Classification

**`GUEST_PERSISTENCE_OR_HYDRATION_FAILURE`** at the active-addon collection
boundary.

The one permitted standard-UI installation succeeded and the current
`#/addons?addon=...` detail route retained an `Uninstall` surface after one
browser reload. That reload preserved the detail route itself, so it did not
prove the addon had hydrated into the active Guest collection. On returning to
the canonical `#/addons` route, the fixture was absent from Installed addons;
Discover consequently had no fixture catalog and emitted no `catalog`, `meta`,
`stream`, or MP4 request. The failure therefore precedes stream selection,
Player creation, and HTML5 media loading.

This is not evidence of a FermataX player, Android media, or renderer failure.

## Environment and Preflight

- Worktree: `E:\\Chatgpt\\fermata-stremio-web-only`, branch
  `codex/stremio-web-only`.
- Device: physical ADB device `15c36230`.
- Android: release `16`, SDK `36`.
- Chrome: `151.0.7922.173`.
- Chrome was used only in a new Incognito Guest tab. No Stremio account was
  signed in and no account data, cookies, tokens, storage, or credentials were
  accessed.
- FermataX was not opened.
- The retained Phase 4 direct-stream fixture was started unchanged on
  `127.0.0.1:7000` and exposed only through `adb reverse tcp:7000 tcp:7000`.
  Its server SHA-256 before the run was
  `F5E74A953A64F8C60F390121A41541E76781728EF874B2AB7221DC5A9749194D`.
- The fixture request log records only timestamp, method, query-free path,
  Origin, `Sec-Fetch-*`, status, and response lifecycle. It does not record
  cookies, authorization, tokens, referer, response bodies, or sensitive query
  material.

## Guest, LNA, and UI Preconditions

1. The prior Incognito Stremio tab was closed in Chrome's tab UI. A new
   Incognito tab was created through Chrome's standard menu.
2. `web.stremio.com` was opened through Chrome's address bar. Chrome displayed
   its Local Network Access prompt; the visible `Allow` control was used before
   the Guest Board completed initialization.
3. Stremio rendered its normal anonymous Guest Board/Home. A direct Chrome
   address-bar navigation to the regular `#/addons` UI also rendered normally.
   This confirmed the earlier black/immersive surface was not reproduced by the
   minimal Home-to-Addons route and was not used as a Core-routing result.

## Installation and Hydration Evidence

1. In the standard Addons page, `Add addon` opened the normal external-link
   dialog.
2. The loopback manifest was entered in that dialog. The button was activated
   once by normal keyboard focus and `Enter`; no script, Core dispatch, DOM
   automation, or retry was used.
3. The fixture observed exactly one new manifest request from Stremio:

   | Field | Observation |
   | --- | --- |
   | Method/path | `GET /manifest.json` |
   | Status | `200` |
   | Origin | `https://web.stremio.com` |
   | `Sec-Fetch-Mode` | `cors` |
   | `Sec-Fetch-Site` | `cross-site` |
   | `Sec-Fetch-Dest` | `empty` |
   | Lifecycle | `finish`, then `close_after_finish` |

4. Stremio displayed `Fermata Local Validation` and `Uninstall` in the addon
   result/detail UI.
5. Chrome's standard reload command was used exactly once in the same
   Incognito session. The fixture received one further manifest `GET 200` with
   the same safe request shape. The URL remained `#/addons?addon=...`, and its
   reloaded detail still displayed the fixture and `Uninstall`. This proves
   reload of the detail route, not collection persistence.

## Routing Boundary Evidence

After reload, the canonical Addons, Board, and Discover UI were opened through
their visible Stremio navigation controls. The canonical Installed list did
not expose the fixture. In Discover, the catalog selector exposed the existing
Cinemeta and Public Domain Movies entries, but did not expose the fixture
catalog. A selector interaction produced no fixture request.

| Request boundary | Evidence |
| --- | --- |
| Manifest | PASS: initial install and one reload both returned `200` |
| Detail route | PASS: query-backed addon detail showed fixture and `Uninstall` |
| Active Guest collection | FAIL: canonical Installed list omitted fixture |
| Catalog | Not reachable: fixture absent from active collection and selector |
| Meta | Not reached |
| Stream | Not reached |
| MP4 / Range / `206` | Not reached |
| Player surface | Not reached |

The query-backed detail result and canonical collection therefore diverged. No
storage, profile, or Core state was inspected or modified to infer a cause.

## Cleanup

- No safe `Uninstall` target remained in the normal Installed list. Instead of
  clearing storage or dispatching a Core action, the entire test-created
  Incognito tab was closed through Chrome's tab UI, which discarded the Guest
  partition. Chrome then showed only the pre-existing standard tab.
- `adb reverse tcp:7000` was removed.
- The exact fixture process created for this run was stopped.
- Host TCP 7000 no longer listened.
- A device loopback `toybox nc` connection attempt returned `Connection
  refused`.
- Temporary ADB UI captures and forwards were created only for this run; no
  production or fixture source was changed.

## Change Audit and Remaining Matrix

- Production/test LOC: `0`.
- This report is the only repository change for the completed Phase 5B4 run.
- Not run: metadata, stream selection, direct MP4, HLS, seek, subtitles,
  fullscreen, lifecycle, next-track, and torrent.

**Checkpoint for the next phase:** diagnose why the query-backed addon detail
does not hydrate the addon into the Chrome Guest active collection. Keep all
work in a Chrome Incognito Guest session; do not modify FermataX production
code, fixture protocol, browser security, or account storage until the active
addon collection boundary is observed.
