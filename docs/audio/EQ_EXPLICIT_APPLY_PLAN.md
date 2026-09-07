# EQ Explicit Apply Plan

## Baseline

- Repository: `E:\Chatgpt\fermata`
- Main baseline: `83f32f02a59ec12ab8ba5b592f5e6b1f6d622d77`
- Isolated branch: `codex/eq-explicit-apply`
- Worktree: `E:\Chatgpt\fermata-eq-explicit-apply`
- Baseline gate: `.\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest`
- Baseline result: `BUILD SUCCESSFUL`, 176 actionable tasks
- Immutable artifact to verify unchanged: `fermata/lib/auto/aauto.aar`, SHA-256 `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`

## Feasibility Findings

1. `AudioEffectsPrefsBuilder` currently receives `AudioEffectsProfileRepository.getUserEditableStore()`. That wrapper writes through to the persisted store, so every settings edit broadcasts to `AudioEffectsController`.
2. `AudioEffectsProfileRepository.save()` already writes the complete portable profile in one `PreferenceStore.Edit`; it is the correct atomic persistence authority and must remain the only persistent profile store.
3. `AudioEffectsController` owns the session-bound backend. `STANDARD_LIVE` can accept the profile without backend replacement. `INITIAL_ONLY` is construction-only when Dynamics Processing supplies the EQ, so the old backend must be released and a new playback engine/session must be prepared.
4. `MediaSessionCallback` already owns playback request revisions, `PlaybackOwnership`, engine callbacks, stop/release, position lookup, and the normal prepare lifecycle. The explicit flow can be added there without changing source routing, WebAudio scope, addons, or the native archive.
5. `FermataServiceUiBinder` exposes `MediaSessionCallback` to settings, so the UI can invoke Apply through the existing service binding rather than introducing a second runtime authority.
6. `docs/` is ignored by default. The two requested documents will be force-added at commit time.

## Design Decisions

### Draft and persistence

- Add a small in-memory `AudioEffectsDraft` backed by `BasicPreferenceStore`.
- Seed it from `AudioEffectsProfileRepository.load()` when the settings tree is built.
- Use that one draft store for Audio & Equalizer and every child preference set, including the curve view and conditions.
- Draft broadcasts only refresh settings UI. They never reach the persistent profile store or runtime controller.
- Apply snapshots the latest draft, calls `AudioEffectsProfileRepository.save()` once, and then requests runtime application. Draft edits made while Apply is busy remain pending and are coalesced to the newest snapshot.
- On settings teardown, discard the draft and cancel/ignore UI callbacks. Navigating between the parent and child preference sets does not teardown the draft.

### Runtime apply

- `MediaSessionCallback` owns a monotonic apply generation and cancels/invalidates any prior apply transaction when a new Apply, stop, next/source switch, Android Auto disconnect, or shutdown occurs.
- Every asynchronous position lookup and replacement callback checks terminal state, apply generation, playback request revision, current engine identity, current source identity, and playback ownership before it may mutate state.
- With no active native source, Apply persists and applies the profile to any existing backend as appropriate, without a restart.
- With an active `STANDARD_LIVE` native backend, Apply updates the backend in place and preserves transport state.
- With an active `INITIAL_ONLY` native backend, Apply captures the current seekable position and playing/paused intent, releases the old backend and engine through the normal safe lifecycle, creates a fresh engine and fresh session-bound backend configured from the committed profile, then prepares the same item at the captured position. A live `StreamItem` resumes at the live edge. A stale transaction cannot resurrect the old item or engine.
- A control-only owner has no native engine/source and is never restarted. WebAudio bridges continue to observe the existing persisted profile store and stay within their existing scope/security boundary.

### Master disable safety

- The master disable action remains an emergency bypass: turning it off bypasses the currently bound native backend immediately and persists only the disabled master state as an explicit safety exception. Other draft edits are not committed by that action.
- Re-enabling the master switch in the draft never re-enables a stale Dynamics Processing instance. It takes an explicit Apply, which creates a fresh initial-only backend when required.
- Leaving settings after re-enabling but without Apply discards that re-enable; no runtime callback silently restores the stale chain.

### Failure behavior

- A failed backend apply or failed fresh-engine transaction leaves the committed profile as saved but reports runtime failure to the settings UI; it is never reported as successfully applied.
- Old engines/backends are not reused after an initial-only replacement attempt. The transaction either commits the fresh owner or leaves the new attempt failed without stale callbacks restoring prior content.

## Planned Files

- Create `fermata/src/main/java/me/aap/fermata/media/audio/AudioEffectsDraft.java`.
- Modify `fermata/src/main/java/me/aap/fermata/ui/fragment/AudioEffectsPrefsBuilder.java` and `PlaybackPrefsBuilder.java` to consume one draft and expose Apply/working/failure feedback.
- Modify `fermata/src/main/java/me/aap/fermata/ui/fragment/SettingsFragment.java` to own and discard the settings-lifetime draft.
- Modify `fermata/src/main/java/me/aap/fermata/media/service/MediaSessionCallback.java` and `AudioEffectsController.java` for explicit runtime application and guarded initial-only replacement.
- Modify `fermata/src/main/res/values/strings.xml` and `fermata/src/main/res/values-vi/strings.xml` with English/Vietnamese Apply and status resources.
- Add focused unit tests for draft isolation/discard/save and runtime apply transitions, extending existing audio/controller/service test locations only as needed.
- Add `docs/audio/EQ_EXPLICIT_APPLY_IMPLEMENTATION_REPORT.md` after implementation and verification.

## Test-First Matrix

1. Draft edits do not change the repository or backend; discard restores the persisted profile; Apply saves all fields atomically.
2. Initial-only Apply uses a fresh backend with the latest profile, releases the old backend, preserves playing and paused seekable playback, resumes live streams at the live edge, and rejects stale A->B position callbacks.
3. Repeated Apply coalesces to the latest edit; stop/disconnect/shutdown invalidates pending work; backend and engine failures report failure.
4. Standard-live native Apply updates in place with no restart.
5. No active native source, control-only owner, and existing scoped WebAudio paths do not restart or change routing.
6. Existing audio/controller/ownership tests remain green.

## Gates

- Focused affected tests first, each observed failing before implementation.
- `.\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest`
- Affected engine/audio/service tests if not covered by the full gate.
- `git diff --check`
- SHA-256 verification of `fermata/lib/auto/aauto.aar` against the baseline hash.

## Explicit Non-Goals

- Do not enable limiter or session-0 DSP.
- Do not change `aauto.aar`, `aauto` behavior, addons, source routing, WebAudio scope/security, or remote control ownership.
- Do not claim physical-device or audible acceptance from unit tests.
