# Open on Car — execution status (2026-09-20)

## Scope and evidence boundary

- **Execution branch:** `codex/open-on-car`
- **Implemented commit range reviewed:** `b018edc2..3621272b`
- **Current/base checkpoint:** `3621272bb75e4f91c475dc3c8baa4a53b7f328b1` — `Harden Stremio lifetimes and document blocked car-transfer gate`
- **This report change:** documentation only. No Gradle task, APK build, signing, install, sideload, device, DHU, AA, MIRROR, or runtime launch was run for this report.

The PASS entries below are limited to the automated commands explicitly recorded in Task reports 1–7. They prove deterministic JVM/Node behavior at the stated layer; they are **not** device, WebView, AA, audible-playback, or transfer acceptance. A scope is PASS only where a cited automated check exists. A runtime-required item with no recorded run is **NOT RUN**, not inferred from unit tests.

## Automated evidence recorded

| Area | Focused evidence | Full/regression evidence | Meaning and limit |
|---|---|---|---|
| Mode/readiness (Task 1) | `:fermata:testAutoDebugUnitTest --tests '*OpenOnCarModeTest' --tests '*AutomotiveConnectionStateTest' --tests '*AutomotiveNavigationControllerTest'` — BUILD SUCCESSFUL | Included in later Fermata auto suites | Mode, raw connection and controller state only; no device UI or lifecycle proof. |
| Typed controller bridge (Task 2) | `:fermata:testMobileDebugUnitTest --tests me.aap.fermata.auto.AutomotiveNavigationControllerTest`; `:fermata:testAutoDebugUnitTest --tests me.aap.fermata.auto.AutomotiveNavigationControllerTest` — BUILD SUCCESSFUL | Included in later Fermata suites | Guards/tokens and typed dispatch semantics only. |
| Generic Web reducer (Task 3) | `:web:testAutoDebugUnitTest --tests '*WebUrlSync*Test'` — BUILD SUCCESSFUL (11 tests, 0 failures/errors) | Superseded by Task 4 web suites below | Pure policy/reducer; no Android callback or SPA proof. |
| Generic Web binding/destination (Task 4) | `:web:testAutoDebugUnitTest --tests '*FermataWebClient*Test' --tests '*WebBrowserFragmentBindingTest' :fermata:testAutoDebugUnitTest --tests '*OpenOnCarModeTest' --tests '*AutomotiveNavigationControllerTest' --console=plain` — BUILD SUCCESSFUL | `:web:testAutoDebugUnitTest :fermata:testAutoDebugUnitTest --console=plain` — BUILD SUCCESSFUL; recorded web 256 (0 failures/errors/skips), Fermata 1043 (0 failures/errors, 2 skipped) | Real adapter seams in JVM tests; fixture/device SPA and destination runtime remain unrun. |
| Phone strip (Task 5) | `:fermata:testMobileDebugUnitTest --tests me.aap.fermata.ui.view.PhoneOpenOnCarStripControllerTest --tests me.aap.fermata.ui.view.PhoneOpenOnCarStripLayoutTest --tests me.aap.fermata.auto.OpenOnCarModeTest --tests me.aap.fermata.ui.view.PhoneBottomMenuControllerTest` — BUILD SUCCESSFUL | `:fermata:testMobileDebugUnitTest` — BUILD SUCCESSFUL; 1,015 tests, 0 failed, 2 skipped | Unit/layout contract only; no screenshot/device confirmation. |
| Native media and YouTube (Task 6) | Recorded focused Fermata/YouTube routing suites — BUILD SUCCESSFUL; `node modules/web/src/test/youtube-selection-script.cjs` — 4/4 | `:fermata:testMobileDebugUnitTest :fermata:testAutoDebugUnitTest :web:testMobileDebugUnitTest :web:testAutoDebugUnitTest --continue --console=plain -q` — BUILD SUCCESSFUL; Fermata mobile 1029/auto 1066, Web mobile/auto 268 each, all 0 failures/errors (Fermata suites 2 skipped each). Native regression command for TV/radio/podcast/audiobook/cast also recorded green. | Routing/lease and known click handling only; not physical playback or surface timing. |
| Stremio safety checkpoint (Task 7) | Focused Stremio policy/bridge/open-on-car suites — GREEN; `node modules/web/src/test/stremio-control-script.cjs` — 12/12; YouTube regression 4/4 | Same four-module command as Task 6 — BUILD SUCCESSFUL; Fermata mobile 1037/auto 1074, Web mobile/auto 284 each, 0 failures/errors (Fermata suites 2 skipped each) | Safety hardening and synthetic fixtures only. It does **not** implement or prove Stremio transfer, route portability, or phone silence. |

The full-suite totals differ by task because they were recorded at different commits. They must not be combined into a single current run. No new full suite was run for this documentation-only commit.

## Runtime acceptance matrix

| Scope | Automated evidence | Runtime status | Required runtime observation before PASS |
|---|---|---|---|
| Phone strip | PASS (Task 5 controller/layout tests) | **NOT RUN** | PHONE-only visibility/toggle, disconnect disabled state, reconnect state, fullscreen behavior and screenshots. |
| Generic Web URL sync | PASS (Tasks 3–4 JVM tests) | **NOT RUN** | OFF A; ON must not send baseline A; click B gives B on both; SPA C reaches car; Back B reaches car; one reload; car URL change must not affect phone. Record WebView package/version. |
| Generic Web request safety | PASS (Tasks 3–4 policy/binding tests) | **NOT RUN** | POST body is never replayed; HTTP/TLS failures never report loaded; unsupported schemes rejected; callbacks/lifecycle behavior validated on device. |
| Native media | PASS (Task 6 routing/lease tests) | **NOT RUN** | Explicit media selection targets car only; browsing does not change car; source stays silent; pause/next has one input/one action; prepare/cancel races. |
| YouTube | PASS (Task 6 routing tests and 4-case Node script) | **NOT RUN** | Real-site explicit selection targets car only, phone silence before commit, browse stability, fullscreen/no flicker, gesture variants and single-action controls. |
| Stremio | Safety tests PASS; transfer is unavailable | **BLOCKED / NOT RUN** | See Stremio gate below. No `STREMIO_PLAYER` request, deterministic source signal, source-silence hook, or car receiver has been implemented/proven. |
| AA/MIRROR/layout/fullscreen | Limited strip layout tests PASS | **NOT RUN** | AA and MIRROR UI unchanged; no duplicate player from Home/Settings; fullscreen toolbar and YouTube fullscreen do not flicker; actual host/surface behavior. |
| Disconnect/reconnect | PASS for controller/binding/routing guards | **NOT RUN** | Disconnect during debounce/load/prepare immediately disables switch even with stale visible owner; no late load; reconnect begins OFF; no replay. |

## Stremio feasibility gate — blocked

`docs/stremio/web-only/OPEN_ON_CAR_FEASIBILITY.md` remains authoritative: Stremio Open on Car is **INCOMPLETE / NOT ENABLED**. Generic Web URL synchronization is explicitly not a substitute. Synthetic `media.invalid` tests and hosted-player controls do not establish a selected real source, exact player-route portability into a fresh CAR WebView, progressing destination playback, or silence before transfer commit.

Stremio transfer is unavailable and blocked until all of the following are completed under a separately authorized runtime session: deterministic explicit source-selection signal; exact selected-player portability with existing CAR session; verified Stremio-only pre-commit source silence (including races and alternate paths); single typed dispatch and cancellation/lease guards; and old-owner release before car claim. If portability or silence fails, leave Stremio disabled—do not downgrade to detail URLs or generic synchronization.

## Runtime checklist (not authorized/run here)

1. Obtain explicit user authorization for a **universal** build/sideload run that preserves the current package name, signing identity, and app data. Do not uninstall or clear data.
2. Record device/Android, AA host, WebView package and exact version, build commit/artifact identity, lifecycle trace, request/token/epoch identifiers, and redacted outcome for each matrix row.
3. Run Generic Web scripted traces and error/form/scheme checks; capture only sanitized diagnostics.
4. Run Native and YouTube selection, browse, control, source-silence, fullscreen and lifecycle cases.
5. Run disconnect at debounce, destination load, and native prepare; reconnect with mode OFF and verify no late/replayed work.
6. Verify AA/MIRROR/layout/fullscreen plus Home/Settings do not create a second player.
7. Run the Stremio feasibility sequence only after its adapter prerequisites exist; otherwise retain BLOCKED/NOT RUN.
8. Publish PASS/FAIL/NOT RUN per add-on, WebView version, build, lifecycle state, and request/epoch. Do not mark any test PASS without recorded evidence.

## Log and artifact policy

- Logs, screenshots, reports, preferences and commits must not contain raw URLs, query/fragment payloads, form bodies, cookies, credentials, account/session identifiers, streaming-server addresses, tokens, or real player payloads.
- Use stable redacted identifiers (for example request ID, epoch, source generation, host generation, outcome) and summarize origin/path class only where needed.
- Keep sensitive runtime artifacts out of repository history. A reported `LOAD_DISPATCHED` is dispatch acknowledgement, never evidence of loaded or audible media.

## Release decision

There is **no merge readiness and no feature-completion claim**. Stremio remains blocked/unavailable, mandatory SPA/device/WebView/runtime gates are NOT RUN, and automated checks cannot substitute for those gates.
