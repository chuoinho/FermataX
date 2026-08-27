# Phase 5B4: Guest/Core Addon Activation and Routing Validation

## Final Status

**`BLOCKED_NO_SAFE_UI_TARGET` after catalog routing.**

This report corrects the prior Phase 5B4 conclusion. The earlier
`GUEST_PERSISTENCE_OR_HYDRATION_FAILURE` classification was based on activating
the Add-addon dialog's submit control, which opens the addon-detail modal, not
its separate `Install` control. The real `Install` control was subsequently
used once through Chrome's normal UI.

After that real install and one normal reload, the fixture remained in the
canonical Installed list, appeared as `fermata-local` in Discover's source
selector, and received a catalog request. Guest/Core collection hydration and
catalog routing are therefore observed. The run could not safely select the
fixture's metadata card: the Chrome Incognito content surface became opaque to
the permitted UI channels, leaving no bounded, visible Stremio control to
activate. No DOM scripting, deep link, Core dispatch, storage inspection, or
coordinate guess was used to bypass that boundary.

This is an automation-observability blocker, not evidence of a Chrome renderer,
FermataX player, HTML5 renderer, metadata-routing, stream-routing, or media
network failure.

## Environment and Preconditions

- Worktree: `E:\\Chatgpt\\fermata-stremio-web-only`, branch
  `codex/stremio-web-only`; clean before the report update.
- Device: physical ADB device `15c36230`, Android `16` (SDK `36`).
- Browser: Chrome `151.0.7922.173`, fresh Incognito Guest only. No Stremio
  account was used, read, or modified.
- FermataX was not opened.
- The pre-existing direct-stream fixture was left protocol-identical on host
  loopback `127.0.0.1:7000` and reached only through
  `adb reverse tcp:7000 tcp:7000`.
- Its request log contains only timestamp, method, query-free path, Origin,
  `Sec-Fetch-*`, status, and lifecycle information. It contains no cookies,
  authorization, tokens, referers, query data, or response bodies.

## Observed Guest/Core Evidence

1. The actual `Install` control inside Stremio's addon-detail modal was used
   once through the standard Chrome/Stremio UI.
2. `phase5b5-after-install2.xml` records canonical Addons content containing
   `Installed`, `Fermata Local Validation`, and `Uninstall`.
3. Chrome's normal reload command was used once in the same Incognito Guest
   session. `phase5b5-post-real-reload.xml` records the same three values,
   proving the fixture survived reload in the active collection.
4. `phase5b5-selector2.xml` records `fermata-local` in the Discover source
   selector. The Board snapshots also contain `Fermata Local Validation -
   Movie` and `Fermata Local MP4`.
5. The fixture log records a browser-originated catalog request after the
   selector became active:

   | Boundary | Observation |
   | --- | --- |
   | Manifest | `GET /manifest.json` -> `200` |
   | Active Guest collection | Installed UI retained after reload |
   | Discover selector | `fermata-local` present |
   | Catalog | `GET /catalog/movie/fermata-local.json` -> `200` |
   | Meta | Not attempted: no safe visible metadata target |
   | Stream | Not reached |
   | MP4 / Range / `206` | Not reached |
   | Player surface | Not reached |

The catalog request carries Origin `https://web.stremio.com`,
`Sec-Fetch-Mode: cors`, `Sec-Fetch-Site: cross-site`, and
`Sec-Fetch-Dest: empty`; it finished normally with status `200`.

## Blocker Evidence

- Chrome's tab URL stayed on normal Stremio routes (`#/calendar`, then `#/`)
  when browser Back was used, so navigation itself remained functional.
- The permitted Android UI dump exposed only Chrome chrome and one opaque
  `SurfaceView` / `Lượt xem trên web` node for page content. It exposed no
  selectable Stremio card, despite the earlier safe snapshots proving the
  fixture exists.
- Screen capture likewise showed an opaque content surface. This can be an
  Incognito capture/accessibility restriction; it does not prove that the
  Chrome renderer is broken on the physical display.
- Passive CDP was limited to Page/Network/console/exception events. It showed
  route navigation but no post-catalog metadata, stream, or media request.
- Continuing would require a constructed route, DOM automation, Core dispatch,
  storage access, or unbounded coordinate guessing. Each is outside the Phase
  5B4 safety rules.

## Cleanup Status

Cleanup is deliberately **not** performed. The fixture has no currently safe
normal-UI `Uninstall` target because the content surface cannot be inspected or
operated reliably. Per the phase rule, the run stops before destructive cleanup
rather than clearing browser/app storage or guessing at controls.

At stop:

- fixture process PID `50960` is listening only on `127.0.0.1:7000`;
- `adb reverse tcp:7000 tcp:7000` remains active;
- the device can still reach the loopback fixture;
- passive observer PID `32612` and its temporary `tcp:9222` forward remain
  available solely to preserve diagnostic state.

No fixture protocol, production, or test source was changed.

## Change Audit and Remaining Matrix

- Production/test LOC: `0`.
- Repository change: this correction report only.
- Not run: metadata selection, stream selection, direct MP4 playback and
  Range validation, HLS, subtitles, seek, fullscreen, lifecycle, next-track,
  and torrent.

## Checkpoint

**One required decision before Phase 5B5:** provide a human-visible Chrome
content surface or explicitly authorize a different, standard-UI-only input
path that can reliably select the visible `Fermata Local MP4` card. Once that
is available, continue from the existing Guest session, make exactly one
metadata and one stream/play selection, then stop at the first actual MP4
request. Do not start Phase 5B5 while this direct-MP4 handoff boundary remains
unobserved.
