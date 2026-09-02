# EQ-2 Native Session Backend Report

## Result

**Status: PASS**

- Baseline: `5a66540dbc8a3c0c40f4256756ff63fb976021ce`
  (`feat(audio): add unified audio effects profile`).
- Scope: native `MediaPlayer`, ExoPlayer, and VLC session effects only.
- The implementation and this report are committed together in the EQ-2 commit.

EQ-2 replaces the normal playback use of `AudioEffectsLegacyApplier` atomically. It does not
change the EQ-1 persistence model, raw hardware-topology migration, web players, or `aauto.aar`.

## Runtime Authority

Before EQ-2, `MediaSessionCallback` applied legacy track, parent, and playback-control
preferences to engine-owned `AudioEffects` objects. Each engine created those objects itself.

After EQ-2, one callback owns one `AudioEffectsController`:

```text
AudioEffectsProfileRepository
        -> AudioEffectsController
        -> NativeSessionAudioEffectsBackend
        -> current real engine audioSessionId
```

The controller listens only for unified profile preference changes, binds when the shared
playback lifecycle reports an engine prepared or started, releases its previous backend before a
session replacement, and unbinds immediately before the engine release path. Closing the media
session callback closes the controller and removes its preference listener.

`AudioEffectsLegacyApplier` and `MediaEngine.getAudioEffects()` remain compiled for dormant UI,
rollback, and legacy unit-test compatibility, but `MediaSessionCallback` has no normal runtime
call to `AudioEffectsLegacyApplier.apply`. The native engines no longer create `AudioEffects`.

## Session Lifecycle

| Engine | EQ-2 session source | Rebind behavior |
| --- | --- | --- |
| MediaPlayer | `MediaPlayer.getAudioSessionId()` | Bind at prepared and start; invalid IDs do not create effects. |
| ExoPlayer | A generated `AudioManager` session assigned to the player, then read from the player | Prepared/start bindings detect an actual session change and replace the backend. |
| VLC | `VlcEngineProvider.getAudioSessionId()` | Invalid `AudioManager.ERROR` values do not create effects; a later valid session binds at start. |

Every backend accepts only an ID greater than zero. Session `0` is rejected before any
`AudioEffect` is constructed. Old session effects are released before the old engine is released.

## Processing Behavior

`NativeEqualizerCurveMapper` maps EQ-1's canonical `31` through `16000 Hz` curve to each native
Equalizer's reported center frequencies using log-frequency interpolation. It converts dB to
millibels only after interpolation and clamps every level to `getBandLevelRange()`.

Each effect is independently created and capability-tracked. An unavailable Equalizer, Bass
Boost, Loudness Enhancer, Virtualizer, or DynamicsProcessing instance does not stop the remaining
effects. A failure while disabling an effect immediately releases that effect rather than leaving
a failed resource attached to the session.

- **Preamp:** `DynamicsProcessing.setInputGainAllChannelsTo()` on the real session only, for
  nonzero preamp values when DynamicsProcessing is available.
- **Limiter:** EQ-2 does not claim a limiter because no validated production limiter tuning was
  established. `LIMITER` is therefore never advertised by the backend.
- **Positive preamp:** blocked fail-closed without an active limiter.
- **Loudness:** blocked when it would stack with enabled positive EQ without a limiter. A stored
  EQ curve does not block loudness while Equalizer itself is disabled.
- **Bass Boost / Virtualizer:** apply existing Android effect behavior only when the device reports
  usable strength support. Virtualizer remains unavailable on current Android versions where that
  platform path is unsupported.

No fake preamp is implemented by shifting Equalizer bands.

## Manifest And Reuse Audit

Session-bound `AudioEffect` and `DynamicsProcessing` use the current player session and do not
require a new core `MODIFY_AUDIO_SETTINGS` declaration. No core manifest permission was added.
The merged universal release manifest still contains that permission solely from the pre-existing
Web module declaration; EQ-2 does not rely on it.

The implementation is independently written. No code was copied or ported from OpenEQ or
Granular Volume, so `THIRD_PARTY_NOTICES.md` is unchanged.

## Verification

New focused coverage:

- `NativeEqualizerCurveMapperTest`: canonical points, log interpolation, low/high bounds, flat
  curves, hardware clamping, and different native band counts.
- `GainSafetyPolicyTest`: negative and zero preamp, positive preamp with and without limiter,
  loudness plus positive EQ, and disabled EQ with a stored curve.
- `AudioEffectsControllerTest`: invalid session rejection, one backend per session, profile
  update, profile disable, session replacement, unbind, and close.
- `AudioEffectsProfileArchitectureTest`: controller is the only normal callback authority,
  native engines do not create legacy effects, and new backend code cannot construct session-0
  effects.

Build and source gates:

- `:fermata:testAutoDebugUnitTest`: PASS.
- `:fermata:testMobileDebugUnitTest`: PASS.
- `:fermata:packageAutoReleaseUniversalApk`: PASS.
- `apksigner verify --verbose --print-certs`: PASS; one signer, APK Signature Scheme v3 verified.
- Release certificate SHA-256:
  `A8:6D:57:6F:F1:EC:0E:32:45:F5:A6:15:2C:5D:8D:66:B5:DF:8B:B5:10:82:0D:75:DC:61:11:0B:19:C3:AE:B4`.
- `git diff --check`: PASS.
- No `AudioEffectsLegacyApplier.apply` normal runtime call remains.
- No new Equalizer or DynamicsProcessing session-0 construction exists.
- `YoutubeWebView.java` and `YoutubeMediaEngine.java`: no diff.

Hotspots:

| File | Before EQ-2 | After EQ-2 |
| --- | ---: | ---: |
| `MediaSessionCallback.java` nonblank LOC | 2174 | 2174 |
| `YoutubeWebView.java` nonblank LOC | 1248 | 1248 |
| `YoutubeMediaEngine.java` nonblank LOC | 1121 | 1121 |

`aauto.aar` SHA-256 before and after:

```text
99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B
```

## Native Playback Matrix

| Path | EQ-2 evidence | Status |
| --- | --- | --- |
| MediaPlayer | Compile/build plus shared lifecycle and controller tests | PASS (code path) |
| ExoPlayer | Compile/build, generated session assignment, and rebinding path | PASS (code path) |
| VLC | Compile/build, provider session validation, and rebinding path | PASS (code path) |
| Local media, TV/IPTV, Radio, Podcast, Audiobook | No addon-specific EQ code; inherit their selected native engine | PASS (architecture) |
| AA/DHU runtime audio listening test | No DHU session was provisioned in this isolated code phase | BLOCKED_ENVIRONMENT |

The connected physical device was left unchanged. This phase does not claim an observed audible
effect measurement; the runtime listening matrix remains a device-validation follow-up rather
than a substitute for the passing build and lifecycle gates.

## Deferred

- EQ-3 topology-aware legacy raw-band migration.
- WEBEQ-A Stremio.
- WEBEQ-B YouTube.
- EQ-X session-0 research only.

No WebAudio was added. No production session-0 processing was added. No topology migration was
performed. `YoutubeWebView` and `YoutubeMediaEngine` are unchanged.
