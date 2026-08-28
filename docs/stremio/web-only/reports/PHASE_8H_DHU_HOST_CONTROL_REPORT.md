# Phase 8H: DHU Host-Control Acceptance

## Result

**PASS.** The Android SDK Desktop Head Unit (DHU) media card controlled the
currently active hosted Stremio Player through FermataX's real MediaSession.
This is host UI evidence, not an ADB media-key substitute.

## Scope And Safety

- Physical device: `15c36230`.
- Host: Android SDK DHU with the `all_720p.ini` configuration and its visible
  media playback card.
- Test media: a self-owned local direct-MP4 fixture already established for the
  Web-only lifecycle matrix. The report intentionally omits its runtime URL.
- No production or test source changed. No account data, credential, cookie,
  storage, stream URL, DOM, Core dispatch or external player was used.

## Observed Control Matrix

| Step | DHU host UI | Hosted Stremio Player | `dumpsys media_session` |
| --- | --- | --- | --- |
| Baseline | DHU card named `Fermata Lifecycle MP4` showed its pause control. | The physical hosted Player visibly rendered the fixture. | `FermataMediaService`: `PLAYING`, actions `518`, metadata `Fermata Lifecycle MP4`. |
| Pause | One click on the visible DHU media-card pause control changed the card to its play control. | The Player visibly exposed its play control at the stopped frame. | `PAUSED`, actions `518`, same metadata. |
| Resume | One click on the same visible DHU media-card play control restored its pause control. | The Player resumed visible rendering. | `PLAYING`, actions `518`, same metadata. |
| Stop | Player Back was invoked through its visible Stremio UI. | The Player returned to its detail route. | `NONE`, actions `0`, no metadata. |

The pause and resume clicks were generated only against the displayed DHU host
card. No `adb shell input keyevent`, MediaSession shell command or direct page
message was used to drive either transition.

## Interpretation

P8A already proved that a normal hosted stream can make FermataX claim an
active MediaSession. P8H closes the independent delivery boundary: a genuine
DHU host control reached that current claim, altered the hosted HTML5 Player,
and returned the expected native state transition. Earlier reports that marked
DHU input `PARTIAL` or `Waiting for phone` remain historical evidence of the
previous DHU configuration; P8H supersedes only their final host-control
verdict.

## Cleanup

- Playback was stopped through visible Stremio UI and FermataMediaService
  returned to `NONE`.
- The phase-owned Node server was stopped.
- `adb reverse tcp:7000` and the stale temporary `adb forward tcp:9224` were
  removed. The pre-existing forwards `tcp:9223` and `tcp:5277` were preserved.
- TCP ports `7000`, `7001` and `7002` were verified closed.
- No addon or account configuration was changed in this continuation. The
  cached fixture detail showed the standard `Install addons` state rather than
  an installed-fixture control.
- The execution environment rejected recursive removal of exact, verified
  Temp fixture/capture directories. They are inert: no listener, ADB reverse,
  forward rule or running process remains.

## Remaining Capability Disposition

- Multi-audio remains `NOT OBSERVED`: P8B physically played a dual-audio HLS
  fixture, but the upstream Player did not expose an audio-track selector.
- Episode `nexttrack` remains `CONDITIONAL_NOT_ADVERTISED`: P8C reached the
  episode Player but its native MediaSession did not register that action.

Both conditions are upstream-advertisement boundaries, not untested FermataX
control paths. They must remain absent from release feature claims unless a
future upstream session advertises the corresponding selectable control.

Production LOC: `0`.

Test LOC: `0`.
