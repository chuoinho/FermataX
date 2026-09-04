# Native EQ-A3 Runtime Acceptance

## Status

`NATIVE_EQ_A_PHONE_PARTIAL_AUDIBLE_DSP_EVIDENCE`

Accepted A2 baseline: `0c0cc26e` (`docs(audio): record native EQ A2
acceptance`).  A3 adds the user-facing negative-only Preamp control and its
input contract.  The A3 implementation and this earlier report checkpoint are
committed in `89ad0940` (`feat(audio): expose negative preamp control`).

The phone session/lifecycle evidence is strong.  The phase remains partial
because clean per-engine DSP-output acceptance (including audible or measured
negative-preamp and optional-effect evidence) was not observed, and DHU failed
before a projection session was established.  Neither gap is presented as a
pass.

## Root Cause And Remediation

Before `d77e8826`, FermataX generated and forced an ExoPlayer audio-session ID.
Android could subsequently create the active `AudioTrack` on a different
session, leaving effects attached to an inactive session.

The fix removes the forced `setAudioSessionId`/`generateAudioSessionId` path.
ExoPlayer now owns its actual session and its callback flows through
`ExoPlayerEngine` -> `StreamEngine` -> `MediaSessionCallback` ->
`AudioEffectsController`.  `StreamEngineTest` protects wrapper forwarding and
`ExoPlayerEngineProviderTest` asserts that the synthetic-session calls are
absent.

`MediaPlayer` reports `MediaPlayer.getAudioSessionId()`.  VLC intentionally
owns one provider-created session and passes it to LibVLC with
`--audiotrack-session-id`; its engine reports that same provider session.

## Phone Runtime Evidence

Physical evidence was collected on `15c36230` (`me.app.fermataX.auto.test`) in
the local, untracked `.native-eq-a-temp/` directory.  It is deliberately
retained while the remaining DSP matrix is incomplete.  No media URL is
recorded here.

| Engine | Active AudioTrack and effect session | Active tracks | Verdict |
| --- | --- | ---: | --- |
| MediaPlayer | `24033`; five session-bound effects | 1 | `PASS` |
| ExoPlayer | `24041` initially; `24049`, `24057`, and `24065` across complete replacement cycles; five effects on each active session | 1 | `PASS` |
| VLC | `22321`; five session-bound effects | 1 | `PASS` |

The Android AudioFlinger dump is the physical source of truth for an active
track and effect-session IDs.  `AudioEffectsController` is not separately
exposed as a public runtime diagnostic; its target is verified by the audited
callback path and the effect objects that Android reports on the actual track
session.  A positive, non-zero ID alone was never treated as acceptance.

Representative evidence files:

* `a2-mp-a-start.txt`, `a2-mp-a-resume.txt`, `a2-mp-a-seek.txt`, and
  `a2-mp-b-switch.txt`.
* `a2-exo-a-start.txt`, `a2-exo-a-resume.txt`, `a2-exo-a-seek.txt`,
  `a2-exo-b-switch.txt`, and `a2-exo-cycle-*.txt`.
* `a2-vlc2-a-start.txt`, `a2-vlc2-a-resume.txt`, `a2-vlc2-a-seek.txt`,
  `a2-vlc2-b-switch.txt`, and `a2-vlc2-cycle-*.txt`.

## Lifecycle Matrix

| Check | MediaPlayer | ExoPlayer | VLC |
| --- | --- | --- | --- |
| Start and real-session match | `PASS` | `PASS` | `PASS` |
| Pause/resume | `PASS` | `PASS` | `PASS` |
| Seek | `PASS` | `PASS` | `PASS` |
| Source A -> B | `PASS` | `PASS` | `PASS` |
| Session replacement | Reuse observed; current session remained valid | `PASS`; replacement IDs observed | Reuse observed; `22321` remained valid |
| Stop: no active FermataX track | `PASS` | `PASS` | `PASS` |
| Repeated A -> stop -> B -> stop -> A | `PASS` | `PASS` | `PASS` |

The stop snapshots show no active FermataX `AudioTrack`.  VLC keeps a disabled
`Dynamics Processing` object registered to its provider-owned reusable session
while the application remains alive; it has no active track and is not an
active/stale playback chain.  It must still be rechecked during terminal
service teardown in the remaining AA/lifecycle phase.

The MediaPlayer repeated sequence is physically evidenced by
`a3-mp-cycle-b-validated-*`, `a3-mp-cycle-stop-b-validated-*`,
`a3-mp-cycle-a2-validated-*`, and `a3-mp-cycle-stop-a2-validated-*`.  Each
playing snapshot has one active FermataX track and five effects on its current
session; each stop snapshot has no active FermataX track.

Cross-engine evidence is also physical:
`MediaPlayer 24073` -> `ExoPlayer 24081` -> `VLC 22321`.  Each running stage
had the current engine's effect chain and each stopped stage had no active
FermataX track.  This is a `PASS` for no active stale controller ownership.

## DSP Matrix

| Check | MediaPlayer | ExoPlayer | VLC |
| --- | --- | --- | --- |
| Equalizer UI profile/session binding | `PASS` | `PASS` | `PASS` |
| 1 kHz `0 -> -10 -> 0` with clean engine-specific acceptance | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Negative preamp `0 -> -6 -> 0` profile/UI | `PASS` | `PASS` | `PASS` |
| Master off/on | `NOT_OBSERVED` | `NOT_OBSERVED` | `UI_STATE_AND_SESSION_ONLY` |
| Bass boost, loudness, virtualizer functional acceptance | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |

The normalized `Preamp` row is visible between `Enable` and `Equalizer` in the
correct signed test package.  The normal UI was observed at `0`, changed to
`-6` through the signed numeric input, and restored to `0`
(`a3-preamp-signed-input.*`, `a3-preamp-signed-restored.*`).  Its range is
`-15..0`, so positive preamp remains fail-closed.  An earlier apparent missing
row was a test procedure defect: the installed package was
`me.app.fermataX.auto.test`, while the rebuilt APK was
`me.app.fermataX.auto.debug`.  Installing the signed `.test` release proved
the original direct preference row renders correctly
(`a3-preamp-visible.*`).

The earlier profile-restoration observation is retained as historical
evidence only. The current exact profile snapshot and its status are recorded
in the A3 fast-closure section below. No measured signal capture or human
listening record was performed. Consequently this report makes no claim that
a `-10 dB` edit or the `-6 dB` preamp edit produced an audible or measured
output delta; a future subjective run must be labelled
`AUDIBLE_DSP_ACCEPTANCE`, and instrumented signal capture is required for a
measured-DSP claim.

## Addon Coverage

The physical fixture used the Folders/local-media path with the app's normal
per-item engine selection, so it is `PHYSICAL_PASS` for that native path.
Other native sources which resolve through `MediaEngineManager` to the same
MediaPlayer, ExoPlayer, or VLC providers are `COVERED_BY_SHARED_ENGINE_PATH`,
not individually physically tested.  WebView-based add-ons (Stremio Web,
YouTube, and generic browser), Cast, and any custom renderer/decoder remain
outside Native EQ-A and inherit no result from this matrix.

## AA/DHU

`desktop-head-unit.exe --usb=15c36230 --input=touch` was started for the final
gate.  The process exited before creating a projection session; device process
inspection showed Android Auto components but no DHU host activity.  No AA
playback, session-match, DSP, pause/resume, disconnect, or reconnect claim was
made.  This is `BLOCKED_ENVIRONMENT`, not an application failure.

On 2026-09-03 a controlled retry used the local 720p DHU configuration and an
ADB `tcp:5277` forward.  The DHU process remained live, but the phone exposed
only Android Auto's `Material3SettingsActivity`, not a projection host/session.
The DHU process was stopped and both `adb forward --list` and `adb reverse
--list` were empty after cleanup.  The final classification remains
`BLOCKED_ENVIRONMENT`.

## Automated And Release Validation

* `:fermata:testAutoDebugUnitTest`: `PASS` after the A3 Preamp UI change.
* `:exoplayer:testAutoDebugUnitTest`: `PASS` after the A3 Preamp UI change.
* `:vlc:testAutoDebugUnitTest`: `PASS` after the A3 Preamp UI change.
* `ArchitectureBoundaryTest`: included in the fresh Fermata suite; 0 failures.
* Release tasks: `:fermata:assembleAutoRelease`
  `:fermata:packageAutoReleaseUniversalApk`; current universal artifact is
  `fermata/build/outputs/apk_from_bundle/autoRelease/fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk`.
* `apksigner verify --verbose` on that universal artifact: APK Signature Scheme
  v3 is `true`.
* `fermata/lib/auto/aauto.aar` SHA-256:
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

The raw `assembleAutoRelease` APK is v2-only.  This is expected for that
intermediate artifact; the release-delivery universal APK is the bundle-derived
artifact above and is the one validated for v3.

`git diff d77e8826 -- modules/web fermata/src/main/java/me/aap/fermata/addon/web
fermata/src/main/java/me/aap/fermata/media/audio modules/exoplayer modules/vlc`
was empty.  Native EQ-A2 introduced no WebAudio, YouTube, generic WebView, or
`aauto.aar` change after the focused ExoPlayer fix.

## Audit Rounds

1. **Architecture:** ExoPlayer is now framework-session owned; MediaPlayer and
   VLC report their actual session authority.  No forced ExoPlayer session call
   remains.
2. **Session match:** all three engines have an observed active AudioTrack with
   effects attached to that exact session.
3. **Lifecycle/regression:** source handoff, pause/resume, seek, stops, the
   ExoPlayer replacement sequence, and one forward cross-engine transition are
   supported by physical evidence.  The incomplete DSP/AA items above remain
   open rather than inferred.

## Remaining Gates And Next Checkpoint

1. Run the controlled `0 -> -10 -> 0`, negative-preamp, master, and optional
   effect acceptance for each engine; preserve a clean UI/profile restoration
   record and classify sound only as `AUDIBLE_DSP_ACCEPTANCE` without capture.
2. Restore a functioning AA/DHU host, then perform representative native
   session match, pause/resume, disconnect, and reconnect checks.
3. Only after these gates pass, remove `.native-eq-a-temp/` and
   `/sdcard/Download/FermataX-NativeEQ-A/`, verify no ADB forwarding/reverse
   resources remain, and update this report to a final closed status.

## Native EQ-A3 Fast DSP Closure

### Baseline And Scope

This closure checkpoint used `89ad0940d7e052fad3b1485554d1750eaa90b98c` on
`15c36230` (`me.app.fermataX.auto.test`, Android API 36 / Android 16). There
was no production or test-source diff after that commit; only the deliberately
untracked `.native-eq-a-temp/` evidence directory existed before this report
update. The accepted A2 lifecycle/session matrix was not repeated.

The device's `screenrecord --help` exposes display capture only and has no
audio-capture option. No trusted PCM capture route is available and no human
listener supplied controlled A/B confirmation. Therefore this checkpoint did
not alter the profile, start playback, or claim `AUDIBLE_DSP_ACCEPTANCE` from
UI, AudioFlinger, or effect-session evidence alone.

### Original And Restored Profile

The visible normal-UI snapshot is stored locally as
`a3sprint-profile-main-current.*`, `a3sprint-eq-top-current.*`, and
`a3sprint-eq-bottom-current.*`. It records the following exact profile before
any test action:

| Setting | Observed value |
| --- | --- |
| Master | `ON` |
| Equalizer | `ON` |
| Preamp | `0 dB` |
| 31, 62, 125, 250, 500 Hz | all `0 dB` |
| 1, 2, 4, 8, 16 kHz | all `0 dB` |
| Bass boost | `OFF` |
| Volume boost | `OFF` |
| Virtualizer | `OFF` |

Because a valid audible observer was unavailable, no setting was changed. The
restored profile is therefore exactly the original profile; this is visibly
verified by the same snapshot set, including all ten bands and the three
optional effects.

### Shared Native Backend Audit

The normal native path is source-proven without adding instrumentation:

`MediaPlayerEngine`, `ExoPlayerEngine`, and `VlcEngine` each expose their
current engine-owned session through `getAudioSessionId()`; `StreamEngine`
delegates that session. `MediaSessionCallback` calls
`AudioEffectsController.bind(...)` at prepare/start and rebinds on a changed
session. The controller creates the single
`NativeSessionAudioEffectsBackend`, which applies EQ, preamp, BassBoost,
LoudnessEnhancer, and Virtualizer only to a positive session ID. This proves
one shared native backend authority, not functional DSP output on every route.

On this Android 16 device, the backend deliberately does not construct a
`Virtualizer` on API 35 or newer. It is consequently
`UNSUPPORTED_DEVICE_CAPABILITY` for this phone-side run, rather than a failed
effect.

### Final Phone DSP Matrix At This Checkpoint

| Check | MediaPlayer | ExoPlayer | VLC |
| --- | --- | --- | --- |
| Session sanity | `PASS` (accepted A2 physical evidence) | `PASS` (accepted A2 physical evidence) | `PASS` (accepted A2 physical evidence) |
| EQ 1 kHz `0 -> -10 -> 0` | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| DSP evidence type | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Preamp `0 -> -6 -> 0` | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Master `OFF -> ON` | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| BassBoost | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Loudness | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Virtualizer | `UNSUPPORTED_DEVICE_CAPABILITY` | `UNSUPPORTED_DEVICE_CAPABILITY` | `UNSUPPORTED_DEVICE_CAPABILITY` |
| Playback regression | `PASS` (accepted A2 lifecycle matrix) | `PASS` (accepted A2 lifecycle matrix) | `PASS` (accepted A2 lifecycle matrix) |

`COVERED_BY_SHARED_NATIVE_BACKEND` in the optional-effect rows is an
architecture coverage classification, not a functional acceptance result. It
must not be promoted until one representative engine has an audible
ON/OFF/ON observation.

### Automated, Release, And Cleanup Evidence

On this checkpoint, `:fermata:testAutoDebugUnitTest`,
`:exoplayer:testAutoDebugUnitTest`, and `:vlc:testAutoDebugUnitTest` completed
successfully (all tasks up-to-date; no failures). `ArchitectureBoundaryTest`
remains included by the Fermata focused suite. There was no production/test
source change, so the previously verified universal release evidence is reused:
APK Signature Scheme v3 is `true` and the immutable `aauto.aar` SHA-256 remains
`99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

`adb forward --list` and `adb reverse --list` were both empty at cleanup. No
AA/DHU attempt was made; its independent status remains
`AA_NATIVE_EQ_BLOCKED_ENVIRONMENT`. The untracked local evidence remains in
place until a final audio-observer checkpoint can close the phone gate.

### Verdict And Narrow Next Checkpoint

Phone status remains `NATIVE_EQ_A_PHONE_PARTIAL_AUDIBLE_DSP_EVIDENCE`.
`NATIVE_EQ_A_PHONE_FULL_PASS` cannot be claimed without controlled human
audible confirmation (or an objective output capture) for EQ, preamp, and
master on all three accepted engine paths, plus one representative optional
effect run where supported. No product defect was demonstrated, so no source
change was made. AA/DHU remains `AA_NATIVE_EQ_BLOCKED_ENVIRONMENT`.

## Native EQ-A4 Manual Audible Final Closure

### Checkpoint Result

Baseline was `49a7e0c8`. This was a manual-listening checkpoint on
`15c36230` using the retained local 45-second tone files. Production and test
source remained unchanged. The existing A2 lifecycle and session-replacement
matrices were not repeated, and AA/DHU was not retried.

MediaPlayer was selected as the preferred audio engine. A live FermataX media
session was observed in `PLAYING` state and the A2 session-match evidence
remains the accepted quick-sanity evidence. With the tone playing, the user
explicitly confirmed that 1 kHz at `-10 dB` was clearly quieter than `0 dB`,
then explicitly confirmed the return to the baseline loudness after restoring
1 kHz to `0 dB`. This is `AUDIBLE_DSP_ACCEPTANCE`, not a measured claim.

The user was unavailable for further listening before Preamp, Master,
ExoPlayer, VLC, BassBoost, and Loudness checks could be completed. Those rows
remain `NOT_OBSERVED`; no result is inferred from UI or AudioFlinger state.

| Check | MediaPlayer | ExoPlayer | VLC |
| --- | --- | --- | --- |
| Session sanity | `PASS` (accepted A2 physical evidence) | `PASS` (accepted A2 physical evidence) | `PASS` (accepted A2 physical evidence) |
| EQ 1 kHz `0 -> -10 -> 0` | `AUDIBLE_DSP_ACCEPTANCE` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Preamp `0 -> -6 -> 0` | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Master `ON -> OFF -> ON` | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| BassBoost | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Loudness | `NOT_OBSERVED` | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Virtualizer | `UNSUPPORTED_DEVICE_CAPABILITY` | `UNSUPPORTED_DEVICE_CAPABILITY` | `UNSUPPORTED_DEVICE_CAPABILITY` |
| Playback regression | `PASS` (accepted A2 lifecycle matrix) | `PASS` (accepted A2 lifecycle matrix) | `PASS` (accepted A2 lifecycle matrix) |

### Profile Restoration And Cleanup

The phase-start profile was Master `ON`, EQ enabled with all ten canonical
bands at `0 dB`, Preamp `0 dB`, BassBoost enabled at strength `148`, and
Volume boost/Virtualizer disabled. Before ending the checkpoint, visible UI
verification restored Master `ON`, Preamp `0 dB`, and all ten EQ bands
including 1 kHz to `0 dB`. BassBoost, Volume boost, and Virtualizer were not
modified during A4, so their initial state remains intact. Playback was
stopped. `adb forward --list` and `adb reverse --list` were empty.

The verified universal-release evidence is reused: APK Signature Scheme v3 is
`true` and `aauto.aar` is
`99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

### Final Status

Phone status remains `NATIVE_EQ_A_PHONE_PARTIAL_AUDIBLE_DSP_EVIDENCE`.
The only next Native EQ task is to resume the short human-listening matrix at
MediaPlayer Preamp; no architecture, code, lifecycle, release, or AA/DHU work
is required. AA/DHU remains `AA_NATIVE_EQ_BLOCKED_ENVIRONMENT`.

## Native EQ-AA1 DHU Runtime Acceptance

### Baseline And Scope

The AA-only checkpoint used `f1f98018` on `15c36230` (Redmi Note 8, Android
16). The required historical baseline `49a7e0c8` is an ancestor of that
checkpoint. The projected DHU host was already working: `desktop-head-unit.exe`
was live and the device retained its `tcp:5277` forward. FermataX opened in the
DHU and showed the two local NativeEQ-A tone fixtures. No phone audible work,
source edit, test edit, fixture-content edit, reverse mapping, or temporary
server was performed.

The exact initial profile remains the retained A4 baseline: Master `ON`, EQ
enabled, all ten bands `0 dB`, Preamp `0 dB`, BassBoost enabled at strength
`148`, and Volume boost and Virtualizer disabled. This checkpoint did not
change an EQ, preamp, master, or optional-effect preference.

### AA Entry Gates

| Gate | Physical evidence | Status |
| --- | --- | --- |
| AA projection connected | Live DHU window and Android Auto projection processes | `PASS` |
| FermataX launch in AA | DHU displayed `FermataX-NativeEQ-A` and both fixture items | `PASS` |
| Native test content visible | Two MediaStore-backed local tone items rendered in DHU | `PASS` |

### Artifact Gate: Dynamic Engine Providers Are Absent

Before any DSP or lifecycle operation, AA1 required an explicit normal-UI
selection of ExoPlayer. A long press on a fixture item in DHU opened its normal
context menu. The menu contains Repeat, favorites, playlist, bookmark, and
subtitle entries, but has no `Preferred media engine` entry. This is a runtime
observation, not an inference from the source tree.

The installed package confirms why that UI is absent:

| Evidence | Observed value |
| --- | --- |
| Installed package | `me.app.fermataX.auto.test` |
| Installed APK paths | one `base.apk` only |
| Package split list | `splits=[base]` |
| ExoPlayer dynamic-feature split | absent |
| VLC dynamic-feature split | absent |

`MediaItemMenuHandler` intentionally shows `Preferred media engine` only when
`MediaEngineManager.isAdditionalPlayerSupported()` is true. That predicate is
the disjunction of its non-null ExoPlayer and VLC providers. The observed menu
therefore agrees with the installed-package evidence: this test artifact has
only the built-in MediaPlayer route available. It cannot launch ExoPlayer or
VLC through normal UI.

The context-menu screenshot, current DHU screenshot, package-path output, and
the source-condition audit are retained locally in `.native-eq-a-temp/` as
AA1 evidence. The package contains no sensitive media URL in this report.

### ExoPlayer AA Gate

| Check | Result |
| --- | --- |
| Explicit UI selection of ExoPlayer | `BLOCKED_ARTIFACT_MISSING_DYNAMIC_FEATURE` |
| ExoPlayer playback under AA | `NOT_OBSERVED` |
| Active AudioTrack == engine session == effect session | `NOT_OBSERVED` |
| EQ `0 -> -10 -> 0` | `NOT_OBSERVED` |
| Preamp `0 -> -6 -> 0` | `NOT_OBSERVED` |
| Master `ON -> OFF -> ON` | `NOT_OBSERVED` |
| Pause/resume, seek, A -> B | `NOT_OBSERVED` |
| Disconnect/reconnect ownership | `NOT_OBSERVED` |

This is not an ExoPlayer session mismatch and not evidence of an AA-specific
production defect. The required provider simply is not present in the
installed artifact. AA1 therefore did not substitute a MediaPlayer result for
the required ExoPlayer proof, and did not run any DSP/lifecycle matrix on the
wrong engine.

### MediaPlayer And VLC AA Sanity

The built-in MediaPlayer path is available, but the AA1 sequence makes the
ExoPlayer real-session gate mandatory before running the quick MediaPlayer
sanity or the VLC sanity. They are consequently both `NOT_OBSERVED`, rather
than treated as failed.

| Check | MediaPlayer | VLC |
| --- | --- | --- |
| Explicit provider available in current artifact | built-in only | `BLOCKED_ARTIFACT_MISSING_DYNAMIC_FEATURE` |
| AA active-track/session/effect equality | `NOT_OBSERVED` | `NOT_OBSERVED` |
| Shared-backend coverage classification | not applicable until AA session proof | not applicable until provider is installed |

### Optional Effects And Audible Output

No optional effect was toggled. Virtualizer remains
`UNSUPPORTED_DEVICE_CAPABILITY` on Android 16/API 36. No human listener or
objective output capture was available, so no AA DSP-output claim is made.

### Cleanup, Scope, And Release Evidence

No test-specific process was started. `adb reverse --list` is empty; the
pre-existing working DHU forward remains `15c36230 tcp:5277 tcp:5277` as
required for projection and was deliberately preserved. No test server is
listening on port 7000. The pre-existing immutable artifact evidence is reused:
the universal release APK verified APK Signature Scheme v3 and
`fermata/lib/auto/aauto.aar` remains
`99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

All production and test LOC changes for AA1 are `0`.

### Audit Rounds

1. **AA session authority:** AA projection and FermataX visibility pass, but
   the absent ExoPlayer provider prevents the required active-track/engine/
   effect equality check. No non-zero session was promoted to a pass.
2. **DSP and lifecycle:** not started. This avoids producing lifecycle or DSP
   evidence for MediaPlayer while labelling it ExoPlayer coverage.
3. **Scope and regression:** no WebView, Stremio, YouTube, projection-library,
   `aauto.aar`, production, or test modification was made. The visible profile
   was not changed and the working DHU connection was preserved.

### AA1 Verdict And Smallest Next Checkpoint

`NATIVE_EQ_AA_BLOCKED_ARTIFACT_MISSING_DYNAMIC_FEATURES`

AA1 can resume only after installing an otherwise identical signed test or
universal artifact that includes both `exoplayer` and `vlc` dynamic-feature
splits. The preflight must first verify the package paths list the two splits
and that the normal AA item menu offers `Preferred media engine`; then rerun
only the AA1 ExoPlayer-first matrix from the beginning. Do not change
production code to work around a missing packaged provider.

## Native EQ-AA1 Universal Artifact Rerun

### Scope And Artifact Result

This rerun installed the signed universal release APK on `15c36230` as
`me.app.fermataX.auto.test`, version `2.0.1 (304)`. APK Signature Scheme v3
and the approved signing certificate were verified before installation. The
package manager correctly reports a fused `base.apk` only for a universal APK;
that is not evidence that dynamic providers are absent.

The required functional preflight passed: a long press on a local fixture in
DHU showed `Preferred media engine` with `Default`, `MediaPlayer`,
`ExoPlayer`, and `VLC`. ExoPlayer and VLC were each selected through that
normal UI. This supersedes the previous *artifact missing dynamic feature*
conclusion above, which remains historical evidence for the earlier
non-universal artifact only.

No production or test source was changed. The retained local evidence is under
`.native-eq-a-temp/`; it contains no media URL.

### Physical AA Results

| Check | Observed evidence | Status |
| --- | --- | --- |
| ExoPlayer AA playback | `ExoPlayerImpl` initialized; MediaSession reached `PLAYING`; one active FermataX AudioTrack used session `24505` | `PASS` |
| ExoPlayer native-effect binding | `Loudness Enhancer` and `Dynamics Processing` were attached to session `24505`, the active AudioTrack session | `PASS` |
| ExoPlayer A -> B handoff | Playback advanced from fixture A to B while retaining active session `24505` and its effect chain | `PASS` |
| ExoPlayer pause/resume | Pause was observed with no active track and the effect chain retained; the following playback run used the current session correctly | `PARTIAL` |
| ExoPlayer seek | No trustworthy position-change observation was captured from the short fixture | `NOT_OBSERVED` |
| MediaPlayer quick sanity | Active MediaPlayer AudioTrack used session `24513`; only the system volume-listener chain was visible | `PARTIAL` |
| VLC quick sanity | `libvlc` loaded; one active AudioTrack and the FermataX Loudness/Dynamics effects used session `24489` | `PASS` |
| Projection disconnect | Closing DHU removed the FermataX MediaSession and left no active FermataX AudioTrack | `PASS` |
| Projection reconnect | FermataX reopened at Dashboard; a new VLC run used fresh session `24561` with a fresh Loudness/Dynamics chain | `PASS` |

The engine-owned session is not independently printed by the production build.
The active-track/effect equality above is physical AudioFlinger evidence; the
engine-to-controller callback path remains source-audited. Consequently this
rerun does not claim the stronger three-way runtime equality as independently
observed.

### Effect Availability On This AA Route

For ExoPlayer, MediaPlayer, and VLC, Android AudioFlinger rejected creation of
both effect types below on the Android Auto Remote Submix route:

| Effect | AudioFlinger result | Classification |
| --- | --- | --- |
| Equalizer | `0bed4300-...`, status `-38`, followed by `initCheck -3` | `UNAVAILABLE_ON_TESTED_AA_ROUTE` |
| BassBoost | `0634f220-...`, status `-38`, followed by `initCheck -3` | `UNAVAILABLE_ON_TESTED_AA_ROUTE` |
| LoudnessEnhancer | Created on ExoPlayer and VLC active sessions | `AVAILABLE_ON_TESTED_AA_ROUTE` |
| DynamicsProcessing | Created on ExoPlayer and VLC active sessions | `AVAILABLE_ON_TESTED_AA_ROUTE` |
| Virtualizer | Not constructed on Android 16/API 36 by policy | `UNSUPPORTED_DEVICE_CAPABILITY` |

This is a device/route capability result, not an APK-packaging defect and not
evidence of a session mismatch. No EQ, preamp, master, BassBoost, or loudness
preference was changed in this rerun, so there is no AA audible-output claim.

### Rerun Verdict

`NATIVE_EQ_AA_PARTIAL_ROUTE_CAPABILITY_LIMIT`

The universal artifact and projected playback gates pass. The AA session and
lifecycle evidence is sufficient to rule out the former packaging blocker and
to show no stale active ownership across disconnect/reconnect. Full AA DSP
acceptance remains open because Equalizer/BassBoost are unavailable on this
specific Android 16 Remote Submix route, MediaPlayer's Fermata-owned effect
chain was not visible, and no controlled audible or objective DSP-output test
was performed. No code change is justified by this evidence alone.

## Native EQ-AA2 DynamicsProcessing EQ Fallback Feasibility

### Scope And Safety Boundaries

AA2 evaluated only a temporary, uncommitted `DynamicsProcessing` pre-EQ probe
on the existing Android Auto Remote Submix route. The probe mapped the
canonical 1 kHz control to the closest pre-EQ cutoff and read the applied gain
back from the framework. It did not alter `aauto.aar`, WebView, Stremio,
YouTube, the product UI contract, or permanent production/test source. The
probe and its unit test were removed after the experiment; this report is the
only tracked result. No audible or acoustic-output claim is made.

The tested device was `15c36230` (Redmi Note 8, Android 16/API 36), with the
signed universal `me.app.fermataX.auto.test` v304 package projected through
DHU. The retained local evidence is under `.native-eq-a-temp/aa2/`. The
fixture was local and no reverse mapping or local server was used.

### New-Session Capability

`DynamicsProcessing` can be instantiated on the active FermataX session after
the ordinary Equalizer creation fails on this route. A fresh session accepted
and reported the requested pre-EQ value in both directions:

| Fresh session | Requested 1 kHz | Read-back | Evidence | Status |
| --- | --- | --- | --- | --- |
| `24729` | `-10 dB` | `-10.0 dB` | `live-transition-logcat.txt` | `PASS` |
| `24753` | `0 dB` | `0.0 dB` | `restore0-logcat.txt` | `PASS` |

For both observations the MediaSession was `PLAYING`, and AudioFlinger showed
the `Dynamics Processing` effect on the FermataX session. This establishes
only that a newly created Remote Submix session can accept an initial pre-EQ
configuration.

### Live-Reapply Result

The required dynamic behavior did not pass. While session `24729` remained
`PLAYING`, changing the normal Equalizer UI caused
`AudioEffectsController.onPreferenceChanged -> applyCurrentProfile` to run.
The first write to `DynamicsProcessing.setPreEqBandAllChannelsTo` after the
session's initial configuration threw:

```text
UnsupportedOperationException: AudioEffect: invalid parameter operation
```

The same rejection was observed for both the `-10 -> 0` and `0 -> -10`
attempts. FermataX did not crash and its MediaSession remained `PLAYING`, but
the framework did not provide a successful applied-gain read-back for either
live update. The relevant stack and non-fatal result are retained in
`live-reapply-failure-logcat.txt`.

| Requirement | Result |
| --- | --- |
| Initial `-10 dB` write/read-back | `PASS` |
| Initial `0 dB` write/read-back | `PASS` |
| Live `-10 -> 0` write/read-back | `FAIL_FRAMEWORK_INVALID_PARAMETER` |
| Live `0 -> -10` write/read-back | `FAIL_FRAMEWORK_INVALID_PARAMETER` |
| Playback survives rejected write | `PASS` |
| Stop releases the DP session effect | `PASS` |
| Audible/objective acoustic validation | `NOT_OBSERVED` |

### Cleanup And Verdict

The visible 1 kHz control was restored to `0 dB`. A new 0 dB session applied
and read back `0.0 dB`; after standard media stop, the MediaSession was no
longer playing and AudioFlinger no longer listed effects for session `24753`
(`cleanup-media-session.txt`, `cleanup-audioflinger.txt`). No ADB reverse
mapping was created.

`AA_DP_EQ_FEASIBILITY_FAIL_LIVE_REAPPLY`

This result does not justify adding a `DynamicsProcessing` EQ fallback to the
product. AA remains `NATIVE_EQ_AA_PARTIAL_ROUTE_CAPABILITY_LIMIT`: standard
Equalizer/BassBoost remain unavailable on the tested Remote Submix route, and
the candidate fallback is not safe for live profile changes. The smallest
permitted next step is AA3 design work for a separately auditable route-aware
fallback with explicit live-update capability gating; AA2 makes no production
implementation recommendation and does not implement AA3.

## Native EQ-AA3 Deferred EQ Apply On Next Session

### Baseline, Constraint, And Policy

AA3 began from `74d29b0035471fc4f649d2339a168a4b1ed1f948`. AA2 remains
historical evidence that this Android 16 Remote Submix route accepts a
`DynamicsProcessing` pre-EQ configuration when a fresh effect is created, but
rejects `setPreEqBandAllChannelsTo(...)` while that effect is live with
`UnsupportedOperationException: AudioEffect: invalid parameter operation`.
AA3 does not retry that failing call as control flow.

The product policy is capability based. A conventional framework `Equalizer`
is `STANDARD_LIVE`; a successfully initialized DP fallback is `INITIAL_ONLY`;
a route where neither succeeds is `UNAVAILABLE`. On `INITIAL_ONLY`, an
Equalizer, preamp, or master-profile change saves the one global
`AudioEffectsProfile`, leaves the active playback chain untouched, and marks a
runtime-only pending state. The first such edit displays the localized
non-blocking message: `EQ changes saved. They will apply to the next playback
session.` Further edits update the same profile without another notification.
Only a successful new backend bind clears pending. There is no AA-specific
profile, session-0 fallback, playback restart, or WebView/Stremio/YouTube
change.

`NativeSessionAudioEffectsBackend` builds the complete deterministic canonical
curve and negative preamp into DP's construction configuration. It reports
`INITIAL_ONLY` only after that construction actually succeeds; it otherwise
reports `UNAVAILABLE`. This closes the prior false-capability edge case where
a failed DP construction could have misleadingly promised a next-session
apply.

### Automated Evidence

`AudioEffectsControllerTest` covers standard-phone live updates, deferred
active-session updates, notification coalescing, latest-profile consumption by
a successful new backend, retention of pending after a failed bind, master
bypass, and deferred preamp. `AudioEffectsProfileArchitectureTest` includes a
source-contract check that `INITIAL_ONLY` requires a successful DP bind.

The fresh focused suite completed after the final capability correction:

* `:fermata:testAutoDebugUnitTest`: `PASS`.
* `:exoplayer:testAutoDebugUnitTest`: `PASS`.
* `:vlc:testAutoDebugUnitTest`: `PASS`.

The production-tree search contains no live
`setPreEqBandAllChannelsTo` call. `aauto.aar` remains SHA-256
`99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

### Universal Artifact And Physical AA Evidence

The signed universal artifact installed on `15c36230` is
`fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk`, package
`me.app.fermataX.auto.test`, version `2.0.1 (304)`, SHA-256
`30821C05497EF1D95D863DEE37A01D1A7F4756C89736B628E90A284A80502CF1`.
APK Signature Scheme v3 verified. DHU projection used the retained
`tcp:5277` forward and explicit ExoPlayer selection.

The first 45-second fixture attempt is deliberately excluded from acceptance:
it finished before the edit snapshot. A later 10-minute local copy of the same
fixture made the timing unambiguous and was removed/restored after the run.
The retained untracked evidence is under `.native-eq-a-temp/aa3/rerun/`.

| Physical check | Observation | Status |
| --- | --- | --- |
| Session A before edit | ExoPlayer AA `PLAYING`; active track session `25065` with DP effect | `PASS` |
| Active edits | UI committed `1 kHz: 0 -> -3 -> -6 -> -10` while `PLAYING` | `PASS` |
| User explanation | Visible toast on first edit; no toast in later `-6` capture | `PASS` |
| Playback safety | MediaSession remained `PLAYING`; no DP live-write exception or process crash in post-edit logcat | `PASS` |
| Session B after normal stop/start | New active track/effect session `25073`, DP present, latest profile still visible as `-10` | `PARTIAL` |
| Disconnect/reconnect | Closing DHU removed the active track; reconnect resumed a projected session | `PARTIAL` |
| DP numeric read-back in production AA3 artifact | No permanent production read-back seam | `NOT_OBSERVED` |
| Pending clear observed directly | Runtime-only state has no public diagnostic | `NOT_OBSERVED` |
| Same-session track-change pending behavior | Not separately observed | `NOT_OBSERVED` |
| VLC AA sanity | Shared backend only; no AA3 runtime rerun | `COVERED_BY_SHARED_NATIVE_BACKEND` |
| MediaPlayer AA effect chain | Not rerun | `MEDIAPLAYER_AA_EFFECT_CHAIN_NOT_OBSERVED` |

AA3 does not claim that the DP curve's `-10 dB` value was read back in the
production build, nor that an audible output delta was measured. The former
AA2 probe remains the accepted fresh-session read-back evidence, but it was
not retained as production code.

### Cleanup And Audit

The exact profile was restored through the normal UI: Master and Equalizer
remain enabled, Preamp is `0 dB`, and every visible canonical band including
31 Hz, 62 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, and 2 kHz was rechecked at
`0 dB`. The temporary ten-minute fixture was removed from the device and the
original 45-second fixture restored; its local copy is retained only in the
untracked evidence directory. Playback was stopped before cleanup.
No reverse mapping was created. The projection forward is retained because it
pre-existed AA3 and is required to reopen DHU.

Audit round 1 confirms phone `STANDARD_LIVE` behavior is covered by focused
tests and the AA route never attempts the known-invalid live DP operation.
Audit round 2 confirms coalescing and bind-success/failure ownership in unit
tests, while physical pending-clear and same-session reuse remain open.
Audit round 3 finds no `aauto.aar`, WebView, Stremio, YouTube, generic web, or
playback-restart change.

### AA3 Verdict

`NATIVE_EQ_AA_PARTIAL_ROUTE_CAPABILITY_LIMIT`

`AA_DEFERRED_EQ_APPLY_PASS` is not claimed yet. The implementation has a
physical safe active-session edit, visible one-time explanation, and a new DP
effect session after stop/start, but lacks production numeric DP read-back and
direct runtime observation of pending consumption. The smallest future gate is
a narrow diagnostic-free physical checkpoint that can expose those two states,
or a deliberately approved test-only observability seam. Do not re-open AA2
live-reapply feasibility.
