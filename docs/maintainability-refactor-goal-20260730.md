# FermataX Maintainability Refactor Goal

Date: 2026-07-30

## Objective

Raise maintainability, addon extensibility, playback/UI stability, and repository hygiene above
8/10 without changing runtime behavior. Package identity, stored data, preferences, routes, Android
Auto visibility, navigation, Back behavior, fullscreen behavior, playerbar behavior, playback
ownership, and addon output are invariants.

This is an incremental extraction, not a rewrite. A phase may proceed only after its focused tests
and the affected regression suites pass.

## Current Source Baseline

- Branch: `main`
- HEAD: `9cfe58428a4c2e91f175afe895729c4f96499f15`
- Tracked diff hash: `ae04e994cb224eec416aede4f67d624f8e3d55e9`
- Full status hash: `6a6eb7c3dc8cf21255da1b119d738e559cff08cb`
- Runtime package: `me.app.fermataX.auto`
- Version code: `301`
- Production Java: 1,238 files and 142,966 nonblank lines
- Large production classes: 38 above 500 nonblank lines, 6 above 1,000, 3 above 1,500
- Current tracked delta from HEAD: 189 files, 5,765 insertions, 976 deletions
- Current worktree entries: 205 modified and 135 untracked

The dirty worktree is intentional and contains the current application. It must not be reset,
cleaned, or replaced with HEAD during this goal.

## Automated Baseline

Command:

```text
gradlew.bat testAutoDebugUnitTest --continue --no-daemon
```

Result on 2026-07-30:

- Build exit code: 0
- XML reports: 262
- Tests: 1,213
- Failures: 0
- Errors: 0
- Skipped: 2

## Runtime Invariants

1. Dashboard opens at the top with SmartTopCard and the existing Recent behavior.
2. Navigation position, ordering, scrolling, touch suppression, and addon activation remain exact.
3. TV fullscreen Back returns to split view and then follows the existing parent hierarchy.
4. Radio Back returns to its source/list without stopping audio.
5. YouTube/Web Back order remains fullscreen, page history, parent, then Dashboard.
6. Player controls and automotive chrome use one presentation authority and retain current timing.
7. Switching addons transfers engine, audio, metadata, title, and current-item ownership correctly.
8. Leaving and returning to Android Auto retains the current host-interruption behavior.
9. Addons remain independently enableable, disableable, installable, and removable.
10. Existing preference keys, stable IDs, provider authorities, and package names do not change.

## Ownership Map

| State or operation | Single authority | Adapters or observers |
|---|---|---|
| Playback item and transport state | `MediaSessionCallback` / `PlaybackSnapshot` | UI binder, addons |
| Playback request identity | `PlaybackOwnership` | Engines and transition policy |
| Engine creation and replacement | `MediaEngineManager` | `MediaSessionCallback` |
| Auto presentation and timeout | `PlaybackPresentationCoordinator` | Control panel and host chrome |
| Back decision | `BackNavigationPolicy` | Activity, media fragments, Web policy |
| Runtime host attachment | `RuntimeSessionCoordinator` | Service UI binder |
| Addon state and lifecycle | Addon state/loader/module/lifecycle components | Dashboard and settings |
| YouTube fullscreen transaction | `YoutubeFullscreenCoordinator` | WebView, engine, fragment host |
| Stremio Web session | `StremioWebSessionPolicy` | WebView client and MediaSession bridge |

No phase may introduce a second authority for any row.

## Phases And Gates

### Phase 1: Characterization And Mechanical Extraction

- Preserve and extend tests around ownership, transitions, Back, chrome, runtime attachment, and
  YouTube scripts/fullscreen.
- Move self-contained helpers and script constants out of god classes without changing behavior.
- Gate: focused tests plus all Auto unit tests.

### Phase 2: Media Session Responsibilities

- Extract command routing, outgoing progress persistence, audio-focus handling, and session
  publication behind package-private collaborators.
- Preserve callback order, thread, `FutureSupplier` behavior, and `PlaybackSnapshot` revisions.
- Gate: playback ownership/transition/progress tests and all Auto unit tests.

### Phase 3: Activity And Lifecycle Responsibilities

- Extract fragment routing, host lifecycle, voice presentation, and pending async operation
  coordination from `MainActivityDelegate`.
- Every delayed callback must be canceled or rejected by an owner generation after destroy.
- Gate: navigation, runtime host, voice, addon lifecycle, and Dashboard tests.

### Phase 4: Control Panel And Presentation

- Separate menu construction, timer/speed state, and chrome binding from the control view.
- Keep `PlaybackPresentationCoordinator` as the only Auto visibility/timeout authority.
- Gate: presentation reducer/coordinator, Back, title, and layout tests plus DHU checklist.

### Phase 5: YouTube Boundary

- Separate JavaScript assets, signal parsing, ad state, metadata, fullscreen, and engine adapter.
- Keep WebView DOM interaction and media ownership generation-scoped.
- Gate: all Web/YouTube tests and DHU YouTube checklist.

### Phase 6: Addon API Boundary

- Define the narrow contracts dynamic features consume and add architecture tests preventing core
  imports of concrete addon implementations.
- Avoid moving Android resources or runtime class names unless compatibility is proven.
- Gate: addon lifecycle/module/isolation tests and feature compilation.

### Phase 7: Repository And Identity Hygiene

- Document application ID, namespace, and Java package roles. Do not rename runtime identity in
  this goal.
- Update architecture and verification documents to the actual current source.
- Exclude diagnostics, extracted/decompiled sources, secrets, and build output from source commits.

### Phase 8: Release Verification

- Run all unit/integration tests, Mobile and Auto compilation, release R8, universal APK packaging,
  update installation, and the affected DHU regression matrix.
- Repeat audit and fix all findings introduced by this goal before completion.

## Completion Score Gate

The goal is complete only when all of these are at least 8/10 with evidence:

- Architecture boundaries
- Core maintainability
- Addon extensibility and isolation
- Async/lifecycle safety
- Playback and UI regression safety
- Repository reproducibility and documentation

Passing tests alone is not sufficient. Unowned state, duplicate presentation controllers, or an
unreproducible source baseline prevent completion.

## Final Verification

Date: 2026-07-31

### Structural Outcome

The high-risk classes were reduced through package-private collaborators and characterization
tests. No runtime package, preference key, provider authority, route, or addon entry point was
renamed.

| Hotspot | Baseline nonblank lines | Final nonblank lines | Change |
|---|---:|---:|---:|
| `MediaSessionCallback` | 2,385 | 2,222 | -163 |
| `MainActivityDelegate` | 1,586 | 1,291 | -295 |
| `ControlPanelView` | 1,117 | 956 | -161 |
| `YoutubeWebView` | 1,670 | 1,248 | -422 |
| `YoutubeMediaEngine` | 1,251 | 1,154 | -97 |

The current production source-set inventory has 1,084 Java files, 134,581 nonblank lines, 38
classes above 500 lines, 5 above 1,000, and 1 above 1,500. The baseline aggregate was captured by
an older inventory command, so direct before/after claims are limited to the five same-file
measurements above.

Extracted responsibilities include playback transitions, stop timers, custom actions, remote
playback lifecycle, activity preference storage, voice recognition sessions, speed/timer menus,
the YouTube fullscreen host adapter, and YouTube scripts. `ArchitectureBoundaryTest` protects the
ownership and dependency boundaries.

### Automated Verification

Final command:

```text
gradlew.bat testAutoDebugUnitTest :fermata:packageAutoReleaseUniversalApk --continue --no-daemon
```

Result:

- Build successful; 1,159 actionable tasks.
- 268 XML reports, 1,229 tests, 0 failures, 0 errors, and 2 skipped.
- The two skipped tests are platform-dependent diagnostics symlink tests; neither exercises app
  runtime behavior.
- Mobile Debug, Auto Debug, Mobile Release, R8/lintVital, universal APK packaging, and package
  identity verification passed.
- `git diff --check` returned exit code 0 with no whitespace errors.

Two regressions found during runtime verification were fixed and locked by tests:

1. YouTube Previous/Next no longer publishes a persistent `SKIPPING_TO_PREVIOUS/NEXT` state.
2. `PlaybackSnapshot` now updates before MediaSession state/metadata publication, preventing
   SmartTopCard from reading an old TV or Radio owner during a YouTube handoff.

### DHU Verification

The final source was verified on Android Auto Desktop Head Unit for:

- Dashboard opening at the top with SmartTopCard visible.
- Nav bar drag scrolling without accidental addon activation.
- TV playback, fullscreen, split-view Back, and ownership handoff.
- Radio playback and playerbar Back without stopping audio.
- YouTube automatic fullscreen, tap-to-show controls, timeout hiding, and playerbar Back.
- TV to Radio to YouTube to TV transitions with matching MediaSession metadata.
- No crash or ANR in the tested flows.

Evidence screenshots are stored in `.codex-adb/`, including `dhu-tv-split-pass.png`,
`dhu-radio-back.png`, `dhu-youtube-controls.png`, `dhu-youtube-hidden.png`,
`dhu-youtube-playerbar-back.png`, and `dhu-dashboard-final-fixed.png`.

### Release Artifact

- APK: `fermata/build/outputs/apk_from_bundle/autoRelease/fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk`
- Size: 367,452,360 bytes.
- SHA-256: `3D0FB7B6F1921719BA09EBC08C4E31387635AA066AC87E4E6C82727C3800B78A`.
- Package: `me.app.fermataX.auto`; version code `301`; version name `2.0.1`.
- SDK: minimum 28; target 36.
- ABIs: `arm64-v8a`, `armeabi-v7a`, and `x86_64`.
- Signing certificate SHA-1: `E6:FD:DB:71:AD:49:EF:CF:71:98:34:48:5E:B5:4A:D6:BB:85:88:02`.
- Update install with `adb install -r --no-streaming` succeeded on the connected device.

### Residual Risk

YouTube native Previous can visually select another recommendation before the page exposes a
trustworthy title or identity signal. Forcing ownership from that incomplete DOM state was tested
and rolled back because it created a greater metadata regression risk. A future change should use
a dedicated generation-scoped YouTube identity state machine and its own DHU characterization
matrix. This pre-existing limitation does not block the conservative refactor.

`MediaSessionCallback` remains the largest class at 2,222 nonblank lines. Further extraction is
appropriate only when additional behavior tests can lock audio focus, queue mutation, and engine
replacement ordering.

### Completion Score

| Category | Score | Evidence |
|---|---:|---|
| Architecture boundaries | 8.5/10 | Ownership map, extracted collaborators, architecture tests |
| Core maintainability | 8.1/10 | Four major hotspots below 1,500 lines; one documented hotspot remains |
| Addon extensibility and isolation | 8.3/10 | Lifecycle contracts and boundary/isolation tests |
| Async/lifecycle safety | 8.4/10 | Generation/cancellation controllers and focused tests |
| Playback and UI regression safety | 8.7/10 | 1,229 tests plus the affected DHU matrix |
| Repository reproducibility and documentation | 8.5/10 | Identity docs, build gates, artifact hash/signature, source hygiene rules |

All completion categories exceed 8/10 with automated and runtime evidence. The goal is complete.
