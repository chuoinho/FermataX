# EQ Explicit Apply Implementation Report

## Status

Implemented on the isolated local branch `codex/eq-explicit-apply` for parent
independent audit. This is not release approval and no device, sideload, push, or
physical playback operation was performed.

## Baseline

- Repository baseline: `83f32f02a59ec12ab8ba5b592f5e6b1f6d622d77`
- Worktree: `E:\Chatgpt\fermata-eq-explicit-apply`
- Main worktree remained unchanged.
- `fermata/lib/auto/aauto.aar` SHA-256: `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`

## Implementation

- Added a settings-lifetime `AudioEffectsDraft` backed by an in-memory
  `BasicPreferenceStore`. Audio & Equalizer and its child preference screens share
  it; edits stay draft-only, leaving settings discards them, and Apply saves the
  complete profile through the existing repository authority.
- Added existing-pattern Apply, working, applied, and failure feedback in English
  and all supported locale resources. Apply is disabled while an apply is working;
  newer edits remain pending for a later Apply.
- Added explicit runtime application through `MediaSessionCallback` and the
  existing `AudioEffectsController`/playback lifecycle.
- `STANDARD_LIVE` and existing WebAudio paths do not restart. An active
  `INITIAL_ONLY` native source releases the old engine/backend and prepares a
  fresh engine/backend from the committed profile, preserving seek position and
  playing or paused intent; live streams restart at the live edge.
- Apply callbacks require the current apply generation, playback revision, engine,
  source, and ownership. Stop, next/source changes, engine switches, Android Auto
  terminal cleanup, and shutdown invalidate stale work.
- Master disable remains an emergency bypass: it immediately bypasses the bound
  backend and persists only master-off. Re-enable is draft-only until explicit
  Apply, so a stale Dynamics Processing chain is never silently re-enabled.

## Scoped Files

- Runtime: `AudioEffectsController.java`, `AudioEffectsProfileRepository.java`,
  `AudioEffectsDraft.java`, `MediaSessionCallback.java`.
- UI: `SettingsFragment.java`, `PlaybackPrefsBuilder.java`,
  `AudioEffectsPrefsBuilder.java`, `AudioEffectsApplyView.java`.
- Tests: `AudioEffectsControllerTest.java`, `AudioEffectsDraftTest.java`,
  `AudioEffectsProfileArchitectureTest.java`,
  `MediaSessionCallbackAudioEffectsApplyTest.java`.
- Resources: `fermata/src/main/res/values/strings.xml`, Vietnamese strings, and
  the existing supported locale string files for coverage.
- Documentation: `EQ_EXPLICIT_APPLY_PLAN.md` and this report.

## Verification

Exact full gate:

```text
.\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest --no-daemon
BUILD SUCCESSFUL in 29s
176 actionable tasks: 5 executed, 171 up-to-date
```

Focused affected tests, including callback, controller, draft, architecture, and
localization coverage:

```text
.\gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.media.audio.AudioEffectsControllerTest --tests me.aap.fermata.media.audio.AudioEffectsDraftTest --tests me.aap.fermata.media.audio.AudioEffectsProfileArchitectureTest --tests me.aap.fermata.media.service.MediaSessionCallbackAudioEffectsApplyTest --tests me.aap.fermata.architecture.ArchitectureBoundaryTest --tests me.aap.fermata.ui.fragment.LocalizationCoverageTest --no-daemon
BUILD SUCCESSFUL in 37s
157 actionable tasks: 12 executed, 145 up-to-date
```

Additional checks:

- `git diff --check`: passed with no whitespace errors.
- `MediaSessionCallback`: 2,272 nonblank lines; unchanged architecture ceiling is
  2,276.
- Immutable `aauto.aar` hash: matched the required value above.

## Risks and Missing Evidence

- Unit tests cover draft isolation/discard/save, zero-curve replacement,
  fresh-backend replacement, paused/playing/live behavior, stale A-to-B position
  callbacks, stop/source invalidation, failure, standard-live no-restart, and
  no-engine no-restart. They do not establish physical-device or audible
  acceptance.
- The callback is intentionally close to its existing architecture ceiling
  (2,272/2,276); parent review should pay particular attention to the compact
  apply guards and reuse of the normal prepare/ownership lifecycle.
- A runtime failure can leave the newly committed profile saved while reporting
  that it could not be applied. The UI does not claim success in that case.
- No physical playback, Android Auto disconnect, WebAudio remote-owner, or
  hardware audio-effects evidence is available from this run.

## Non-Goals Confirmed

No limiter, session-0 DSP, `aauto.aar`, `aauto` behavior, source routing,
WebAudio scope/security, addons, or remote control ownership changes were made.
