# EQ-1 Unified Audio Effects Profile Report

## Result

**Status: PASS**

- Baseline: `8a90266d fix(audio): extract and correct legacy audio effects`.
- Resulting commit: this report's accompanying `feat(audio): add unified audio effects profile`
  commit.
- Production and test scope is limited to unified configuration ownership. No EQ-2 executor
  work is included.

## Files

Added:

- `fermata/src/main/java/me/aap/fermata/media/audio/AudioEffectsProfile.java`
- `fermata/src/main/java/me/aap/fermata/media/audio/AudioEffectsProfileRepository.java`
- `fermata/src/main/java/me/aap/fermata/media/audio/LegacyAudioEffectsSnapshot.java`
- `fermata/src/main/java/me/aap/fermata/media/audio/MigrationState.java`
- `fermata/src/main/java/me/aap/fermata/ui/fragment/AudioEffectsPrefsBuilder.java`
- `fermata/src/test/java/me/aap/fermata/media/audio/AudioEffectsProfileRepositoryTest.java`
- `fermata/src/test/java/me/aap/fermata/media/audio/AudioEffectsProfileArchitectureTest.java`

Modified:

- `fermata/src/main/java/me/aap/fermata/ui/fragment/PlaybackPrefsBuilder.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/ControlPanelView.java`

`THIRD_PARTY_NOTICES.md` is unchanged. EQ-1 incorporates no external DSP code.

## Profile And Persistence

`AudioEffectsProfile` is immutable and schema version 1. It owns an enabled flag, Equalizer
enabled flag, 10-band curve, model-only `preampDb`, Bass Boost state/strength, Loudness
state/gain, and Virtualizer state/strength/mode. The canonical curve is stored in whole dB at:

`31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz`.

`AudioEffectsProfileRepository` is the sole persistence authority. It stores each canonical
band as a profile key in the application-wide `fermata` preference store. The normal portable
backup captures that store through `AndroidBackupStateStore`; no separate backup contributor or
preference file is needed. Newer profile schemas fail closed rather than being overwritten by an
older repository.

Defaults are legacy-compatible: master and optional effects are disabled, the canonical curve is
flat, preamp is 0 dB, strengths are zero, and Virtualizer mode is Android's `AUTO` value.

## Migration Safety

Only global legacy preferences are considered. Their original values are copied into an immutable
`LegacyAudioEffectsSnapshot` persisted alongside the profile:

- Raw `EQ_BANDS`, native `EQ_PRESET`, and serialized `EQ_USER_PRESETS` are retained verbatim as
  rollback data and set `PENDING_NATIVE_TOPOLOGY`.
- EQ-1 never converts raw band positions or a native preset index to canonical frequencies.
  When topology is pending, the new profile has a flat curve and does not enable its Equalizer.
- Self-contained global values for master enablement, Bass Boost, Loudness (`VOL_BOOST_*`), and
  Virtualizer are copied exactly. A snapshot without unresolved topology is `DORMANT`.
- Saving a new unified profile preserves the snapshot and changes its state to `DORMANT`; it does
  not delete or rewrite the legacy rollback data.
- Per-track and per-folder legacy stores are neither read, selected, merged, nor deleted by the
  repository. They are dormant from the new profile model's point of view.

The prior `AudioEffectsLegacyApplier` remains unchanged during EQ-1 as the temporary compatibility
executor for existing playback. It continues to honor legacy track/folder/global state only until
EQ-2 supplies the single new runtime backend. The unified profile is intentionally not applied to
an audio session in this phase, so there is no duplicate effect application.

## Settings

The normal editable route is now:

```text
Settings -> Playback -> Audio & Equalizer
```

It has master enablement, portable Equalizer curve controls, and Bass Boost, Volume Boost/Loudness,
and Virtualizer configuration. It initializes from `AudioEffectsProfileRepository` and requires no
active `MediaEngine`, `PlayableItem`, audio session, or hardware Equalizer topology. Preamp is a
persisted model field but intentionally has no UI until a backend can apply it correctly.

The Control Panel audio-effects menu entry was removed. The old engine-coupled fragment/view stay
compiled as dormant compatibility code but are no longer reachable through the normal user UI.

## Verification

- Focused repository and architecture tests: PASS.
- `:fermata:testAutoDebugUnitTest`: PASS; 176 XML result files, no failures or errors.
- `:fermata:testMobileDebugUnitTest`: PASS; 165 XML result files, no failures or errors.
- `:fermata:packageAutoReleaseUniversalApk`: PASS.
- Release APK: `fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk`.
- `apksigner verify --verbose --print-certs`: PASS; one signer, APK Signature Scheme v3 verified.
- `git diff --check`: PASS.

Hotspots:

| File | Before EQ-1 | After EQ-1 |
| --- | ---: | ---: |
| `MediaSessionCallback.java` nonblank LOC | 2174 | 2174 |
| `YoutubeWebView.java` nonblank LOC | 1248 | 1248 |
| `YoutubeMediaEngine.java` nonblank LOC | 1121 | 1121 |

`aauto.aar` SHA-256 before and after:

```text
99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B
```

No DynamicsProcessing, WebAudio, session-0/global-output processing, controller, hardware curve
mapper, or player-engine changes were added.

## Deferred

- Native session backend and one runtime controller: EQ-2.
- Native topology-aware migration of raw legacy Equalizer data: EQ-3.
- Stremio WebAudio: WEBEQ-A.
- YouTube WebAudio: WEBEQ-B.
- Session-0 research only: EQ-X.
