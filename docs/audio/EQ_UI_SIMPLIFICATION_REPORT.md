# EQ UI Simplification Report

Date: 2026-09-08

Worktree: `E:\Chatgpt\fermata-eq-explicit-apply`

Branch: `codex/eq-explicit-apply`

Baseline: `42f20184192c85469eb11ac90c2df46e3c0e0013`

The parent worktree `E:\Chatgpt\fermata` was not touched. No agents, merge,
push, APK build, sideload, or device operation was performed.

## Implementation

- Removed the separate EQ enable switch. The master audio-effects switch is the
  only runtime enable gate for all ten vertical EQ bands.
- Kept the reset action and renamed it to `Reset EQ to 0` / `Đặt EQ về 0`.
  Reset changes only the ten canonical band preferences.
- Applied legacy compatibility at explicit Apply time: a saved profile with
  `equalizerEnabled=false` is normalized to `true`, including while master is
  off. Constructing or entering the screen does not write the committed profile.
- Removed the Flat/Custom status and collapsed Additional effects row. Preamp,
  Bass Boost, and Loudness controls are directly attached to the screen content.
- Virtualizer controls are created only when the active bound native backend
  reports `VIRTUALIZER`. No OS-version-only capability inference was added;
  stored Virtualizer preferences remain untouched when the control is hidden.
- Added one read-only capability getter through `AudioEffectsController` and
  `MediaSessionCallback`. No backend, DSP, WebAudio, or MediaSession callback
  behavior was otherwise changed.
- Added axis-aware band touch arbitration: vertical movement stays with the
  band, while horizontal movement releases the parent-intercept lock so the
  horizontal band strip can scroll. Page scrolling outside tracks remains
  available.
- Switched the screen and Apply actions to themed `AppCompatButton` instances
  and theme-derived secondary text colors for readable enabled/disabled states.

Changed source and test paths:

- `fermata/src/main/java/me/aap/fermata/media/audio/AudioEffectsDraft.java`
- `fermata/src/main/java/me/aap/fermata/media/audio/AudioEffectsController.java`
- `fermata/src/main/java/me/aap/fermata/media/service/MediaSessionCallback.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsScreenView.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsBandView.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsApplyView.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsScreenLayoutPolicy.java`
- `fermata/src/main/res/values/strings.xml` and the supported `values-*` string files
- `fermata/src/test/java/me/aap/fermata/media/audio/AudioEffectsDraftTest.java`
- `fermata/src/test/java/me/aap/fermata/media/audio/AudioEffectsControllerTest.java`
- `fermata/src/test/java/me/aap/fermata/media/audio/AudioEffectsProfileArchitectureTest.java`
- `fermata/src/test/java/me/aap/fermata/ui/view/EqualizerCurveGeometryTest.java`

## Verification

Focused final gate:

```text
.\gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.media.audio.AudioEffectsControllerTest.exposesCapabilitiesOnlyFromTheActiveBackend --tests me.aap.fermata.media.audio.AudioEffectsDraftTest --tests me.aap.fermata.media.audio.AudioEffectsProfileArchitectureTest --tests me.aap.fermata.ui.view.EqualizerCurveGeometryTest --no-daemon --console=plain
BUILD SUCCESSFUL in 20s
33 tests, 0 failures/errors, 0 skipped
```

Full requested unit gates:

```text
.\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest --no-daemon --console=plain
BUILD SUCCESSFUL in 29s
```

The full run produced 931 Fermata tests with 0 failures/errors and 2 skipped,
plus 217 Web tests with 0 failures/errors and 0 skipped.

Additional checks:

- `git diff --check`: passed with no output.
- `MediaSessionCallback.java`: 2275 nonblank lines, within the recorded 2276-line limit.
- Immutable artifact `fermata/lib/auto/aauto.aar` SHA-256:
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.
- TDD red evidence included the legacy EQ-off-with-master-off test failing before
  the unconditional apply normalization; the focused suite passed after the fix.

## Physical Acceptance

Not performed. No emulator, phone, Android Auto DHU, TalkBack session, rotary
input device, screenshot inspection, or audible playback validation was
available. Parent review still needs to verify:

- button contrast in the project's light and dark themes, including disabled Apply;
- vertical band drag versus horizontal strip scroll and vertical page scroll;
- ten-band readability and endpoint accuracy at phone and automotive dimensions;
- visible Preamp/Bass Boost/Loudness rows and absence of Virtualizer on a backend
  without the reported capability;
- preservation of stored Virtualizer settings when its controls are hidden;
- master-off bypass, Apply normalization of an old EQ-off profile, Cancel, and
  retry behavior on physical playback.

The Virtualizer capability is sampled when the screen is constructed. If the
active backend changes while the screen remains open, reopening the settings
view is the current refresh boundary; no callback/listener growth was added for
that case.
