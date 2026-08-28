# Phase 6A: Acceptance Evidence Reconciliation And Remaining-Gap Baseline

## Result

**COMPLETE: documentation reconciliation only.** This phase did not run ADB,
Chrome, FermataX, a fixture, a server, or playback. It inspected the acceptance
plan and the already recorded physical evidence. Production LOC: `0`. Test LOC:
`0`.

The accepted architecture remains Web-only: FermataX hosts
`https://web.stremio.com/#/`; Stremio Web owns account, catalog, stream
selection, HTML5 playback, subtitles and progress; FermataX owns the WebView,
fullscreen/Back integration, lifecycle and the control-only MediaSession shim.
There is no Stremio Core, `jlibtorrent`, native torrent engine, native player,
stream extraction or Fermata-hosted streaming server. A torrent or infohash
stream remains the responsibility of a streaming server selected and configured
by the user in Stremio Web.

## Status Vocabulary

- **PASS**: the required result was observed on the required physical surface.
- **PARTIAL**: at least one required scenario remains unobserved.
- **NOT OBSERVED**: the relevant event or fixture has not been observed.
- **BLOCKED**: an explicit prerequisite or scope decision is missing.
- **CONDITIONAL_NOT_ADVERTISED**: an optional upstream action was not registered;
  this is not a failure.
- **NOT APPLICABLE**: intentionally outside the approved Web-only architecture.

## Reconciled Acceptance Matrix

| Acceptance item | Status | Physical evidence and final interpretation |
| --- | --- | --- |
| Web-only registration and hosted entry | PASS | The Web-only build and physical hosted Stremio UI are recorded in Phase 1 and P5B5. This is not a native Stremio implementation. |
| Authenticated session and installed-addon preservation | PASS | P1C used the visible signed-in session and verified the normal six-addon list before and after the temporary fixture lifecycle. |
| Direct HTTP MP4 | PASS | P5B5 F2 observed catalog -> detail -> stream -> Player, visible `Pause`, `HEAD`, ranged media `GET`, and `206`. |
| HLS | PASS | P5B5 F8 observed normal selection, Player `Pause`, manifest and segment requests, and `PLAYING` MediaSession state. |
| Seek | PASS | P5B5 F4 observed a visible seek from about `00:00:22` to `00:00:48`, a new byte range and `206`, while playback remained `PLAYING`. |
| Explicit WebVTT subtitle selection | PASS | P5B5 subtitle follow-up observed `OFF` -> `English`, selected UI state, and a rendered cue at `00:00:04`. |
| Fullscreen and ordered Back | PASS | P5B5 fullscreen follow-up observed one Android Back exit browser fullscreen while retaining the hosted Player and active playback. |
| Native MediaSession decoration | PASS | P1C observed the hosted page's active `PLAYING` claim and metadata after normal stream selection. |
| ADB play/pause/toggle | PASS | P1C records physical control transitions through FermataMediaService. |
| Home/reopen, screen-off/wake, addon switch | PASS | P1C physically exercised all three. Home/reopen restored Player and `PLAYING`; screen-off/wake safely released Chromium's decoder to `NONE` without invented resume, crash or ANR; addon switching released and later restored hosted ownership safely. |
| Renderer-loss recovery | PASS | P1C physically sent `Page.crash`, observed `FermataWebClient.onRenderProcessGone` -> `FermataWebView.recoverRenderProcess`, then a newly rendered hosted detail route and fresh fixture meta/stream requests without app crash or ANR. |
| Hosted catalog/search/library/settings sweep | PARTIAL | Catalog/detail and Addons were observed, but all four hosted sections have not been swept as a bounded physical acceptance run. |
| Unaffected-addon regression | PARTIAL | Ownership release/re-entry was observed on switch to YouTube, but no focused sweep covers the other unaffected addons. |
| Android Auto/DHU host transport/reconnect controls | PARTIAL | Hosted UI/ownership was observed; a dedicated DHU or vehicle-host input and reconnect acceptance run remains. |
| Audio-track selection | NOT OBSERVED | No approved multi-audio fixture has run through the normal hosted player UI. |
| `nexttrack` | CONDITIONAL_NOT_ADVERTISED | The observed Stremio page did not register `nexttrack`; no failed action is inferred. Test only if a repeatable episode flow actually advertises it. |
| Configured streaming-server/torrent transport | BLOCKED | This needs a separately approved, reproducible server-backed validation decision. Direct HTTP/HLS fixture success is not torrent evidence. |
| Fermata-native torrent transport or player | NOT APPLICABLE | This would violate the approved Web-only architecture and is not a remediation path. |

## Historical Evidence And Supersession

Historical reports remain intact; this phase changes no prior test result.

| Historical boundary | Final reconciliation |
| --- | --- |
| P5B3 Guest/Core pre-Player routing failure | Preserved as historical Guest-session evidence. P5B4 later reached the Player/direct-MP4 boundary and supersedes it as the final routing conclusion. |
| P5B4/P5B4A route investigation | Complete historical evidence only. P5B5 supplies the required FermataX Player/request evidence, so neither phase is reopened. |
| P5B5 initial fullscreen Back failure | Preserved as the original defect evidence. The P5B5 fullscreen follow-up physically passed after the narrow repair and is the final F6 outcome. |
| P5B5 initial subtitle gap | Superseded by the explicit subtitle-selection follow-up, which physically passed F5. |
| Earlier renderer-loss wording marked unverified or blocked | Superseded by P1C's final physical `Page.crash` -> `onRenderProcessGone` -> `recoverRenderProcess` evidence. Renderer recovery is PASS. |

## Minimum Remaining Phase Order

The sequence keeps independent risks separate and never introduces a native
player or torrent engine.

| Phase | Objective | Environment | Safety gate | PASS / FAIL criterion | Cleanup | Decision checkpoint |
| --- | --- | --- | --- | --- | --- | --- |
| 6B | Sweep hosted catalog, search, library and settings. | Already-authenticated FermataX device; visible UI only. | No addon/server/fixture changes; no ADB forwarding, Chrome/CDP, DOM/Core/storage/account API, external player, code changes or clears. | PASS only if all four sections render and work, Back stays hosted, and no crash/ANR/external launch occurs. Otherwise record the exact visible boundary. | Return to starting route; leave account, addons and server unchanged. | Decide whether a hosted UI defect needs a separately scoped diagnosis; do not patch here. |
| 6C | Regression sweep of unaffected FermataX addons. | Physical FermataX device. | Use existing user content only; preserve current playback/account state and do not alter Stremio configuration. | PASS only for every predeclared unaffected addon flow; any regression is a discrete FAIL with observed reproduction. | Exit each addon normally and confirm no stale Stremio MediaSession claim. | Decide whether an observed regression is owned by the Web shell or the unrelated addon before any code change. |
| 6D | Validate Android Auto/DHU host transport and reconnect behavior. | DHU or vehicle host plus physical device. | Do not infer host input from ADB; no fixture/server changes unless separately approved. | PASS only if actual host controls reach the current advertised MediaSession action and reconnect has the documented safe state. | Disconnect host cleanly; remove only phase-created host tooling. | Decide whether absent host action is `CONDITIONAL_NOT_ADVERTISED`, host-limited, or a Fermata defect. |
| 6E | Validate multi-audio selection. | Physical device plus separately approved direct multi-audio fixture. | Fixture must be temporary, local, removable and must not alter existing addons. | PASS only for visible selection and audible/current-track confirmation through the hosted player. | Uninstall fixture through normal UI; stop server, remove reverse/forwards, close port and remove temporary artifacts. | Decide whether the hosted player exposes a supported multi-audio path. |
| 6F | Validate `nexttrack` only if it is advertised. | Physical device plus an approved repeatable episode flow. | Begin only after observing `nexttrack` registration; otherwise record `CONDITIONAL_NOT_ADVERTISED` and do not manufacture an action. | PASS only if actual registered action advances the hosted episode flow once without stale ownership. | Restore initial route and leave account/addons unchanged. | Decide whether no registration remains expected upstream behavior. |
| 6G | Decide and, only if approved, validate a user-configured streaming server/torrent path. | Physical device plus a separately approved reproducible server-backed environment. | No native engine, Core, URL extraction, credentials, profile edits or persisted server capability in FermataX. | PASS only for real server-backed playback with an explicit agreed matrix; direct fixture evidence cannot substitute. | Remove approved temporary server/fixtures and verify account state. | Decide whether the user-configured server boundary is sufficiently reproducible for release acceptance. |

## Phase 6B Prompt

```text
Continue at E:\\Chatgpt\\fermata-stremio-web-only.

Perform only Phase 6B - Hosted UI sweep. Do not start Phase 6C or any later
phase, and do not change production or test code.

Goal: obtain physical-device evidence for the four hosted Stremio Web sections:
catalog, search, library and settings.

Environment: use the already-authenticated FermataX device and only FermataX's
visible Stremio UI.

Safety gates:
- Do not add, remove, reorder or configure addons.
- Do not start a fixture or server; do not create ADB reverse/forward rules.
- Do not use Chrome, CDP, DOM/Core dispatch, evaluateJavascript, storage,
  cookies, tokens, account APIs, an external player, clear-site-data or app-data
  operations.
- Do not change code, tests or Stremio settings. Do not start playback.
- Do not read or record account credentials, tokens, cookies, storage or URLs.

For each of catalog, search, library and settings, use normal visible UI to
confirm it renders and accepts one bounded ordinary interaction. Use Back only
through normal Android/FermataX navigation and verify it remains in the hosted
Stremio surface. Do not infer PASS from source code or a previous route.

PASS only when all four sections render and are usable, Back remains hosted,
and no crash, ANR or external-app launch occurs. On failure or block, stop at
the exact visible boundary, record only privacy-safe observable evidence, and
do not patch or retry by changing state.

Return to the initial route. Leave the account, addon list and any
streaming-server setting unchanged. Write
docs/stremio/web-only/reports/PHASE_6B_HOSTED_UI_SWEEP_REPORT.md with the
observed result, safety/cleanup evidence and production/test LOC. Run
git diff --check. If the report is complete, commit documentation only locally.
Do not push, merge or create a PR.
```

## Files And Validation

- Updated acceptance source: `05_TEST_ACCEPTANCE.md`.
- Updated stale traceability status: `08_TRACEABILITY_MATRIX.md`.
- Updated prior P5B5 reports only to label their superseded wording; their
  historical observations remain preserved.
- New reconciliation report: this file.

`git diff --check` and the documentation-only change audit are required before
the Phase 6A documentation commit.
