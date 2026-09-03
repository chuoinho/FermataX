# Native EQ-A3 Runtime Acceptance

## Status

`NATIVE_EQ_A3_PARTIAL_RUNTIME_VALIDATION`

Accepted A2 baseline: `0c0cc26e` (`docs(audio): record native EQ A2
acceptance`).  A3 adds the user-facing negative-only Preamp control and its
input contract.  The implementation is still uncommitted while A3 remains
partial.

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

The profile was restored through the normal UI after test navigation: master
and Equalizer are enabled; Preamp is `0`; visible values are `31=0`, `62=0`,
`125=-12`, `250=0`, `500=0`, `1k=0`, `2k=0`, `4k=-3`, `8k=0`, and `16k=0`.
Bass boost, Volume boost, and Virtualizer remain disabled.  No measured signal
capture or human listening record was performed.  Consequently this report
makes no claim that a `-10 dB` edit or the `-6 dB` preamp edit produced an
audible or measured output delta; a future subjective run must be labelled
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
