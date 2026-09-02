# EQ-3 Topology-Aware Legacy Equalizer Migration Report

## Result

**Status: PASS**

- Baseline: `678963807cc32e8afcac7db73d76649662aaf879`
  (`feat(audio): add native-session processing backend`).
- Result: the commit containing this report, `feat(audio): migrate legacy equalizer topology
  safely`.
- Scope: only deferred global legacy Equalizer conversion. EQ-2 playback lifecycle, effect
  creation, gain policy, preamp policy, and all Web paths remain unchanged.

## Legacy Storage Audit

The audited legacy source is `MediaPrefs`, captured once into
`LegacyAudioEffectsSnapshot` by EQ-1 from the application-wide global preference store only.
Per-track and per-folder preference stores are not read by EQ-3.

| Legacy value | Audited representation | EQ-3 policy |
| --- | --- | --- |
| `AE_ENABLED`, `EQ_ENABLED` | Boolean enable flags | Preserved in the snapshot; the global master state remains in the unified profile. |
| `EQ_BANDS` | `int[]` produced by `Equalizer.getBandLevel()`, therefore Android **millibels** | Eligible only for a matching valid live topology. |
| `EQ_PRESET` | `0` manual; positive is one-based Android system-preset selection; negative is selected user-preset index | Positive/system values remain pending. They are never mapped by index or inspected by temporarily changing a live effect. |
| `EQ_USER_PRESETS` | Ordered strings, `"<millibel> <millibel> ...:<name>"` | Only the actively selected negative preset may be decoded, with the same count and topology requirements as manual bands. All serialized presets remain raw rollback data. |

The old `AudioEffectsLegacyApplier` applied manual and user values directly as native band
levels. EQ-3 therefore uses millibels as source units, converts to dB only during interpolation,
and retains every raw value unchanged.

## Migration Model

```text
LegacyAudioEffectsSnapshot (global raw current curve only)
    + NativeSessionAudioEffectsBackend readable Equalizer topology
    -> AudioEffectsProfileRepository migration transaction
    -> existing AudioEffectsController preference listener
    -> existing NativeSessionAudioEffectsBackend apply path
```

The controller triggers a migration attempt after it creates a real session backend and before it
performs its normal one-time profile application. The backend exposes a read-only topology only;
the repository does not create an `AudioEffect`, touch a playback engine, or apply a profile.
There is no additional runtime authority.

`NativeEqualizerTopology` captures actual Android center frequencies in millihertz, preserving
the platform's precision. A topology is accepted only when it has at least one band, a non-reversed
level range, and positive strictly increasing centers. Malformed topology, absent Equalizer,
empty data, mismatched raw/native band counts, or values outside the current readable native range
leave the snapshot in `PENDING_NATIVE_TOPOLOGY` for a future valid native session.

The inverse mapper uses log-frequency interpolation from native centers to the portable
`31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz` curve. Values beyond the native
endpoints clamp to the nearest endpoint; there is no extrapolation. Interpolated dB values round
half away from zero deterministically and then clamp only to the canonical Settings domain
`[-15, +15] dB`, not to a future device's hardware range.

## User Authority And Persistence

EQ-1 did not persist whether its initial flat canonical profile was generated automatically or
was later deliberately saved by the user. EQ-3 introduces the minimal explicit
`PROFILE_AUTHORITY` marker:

- profile initialization writes `GENERATED`;
- any normal unified Settings edit or `repository.save()` writes `USER_ESTABLISHED`;
- migration is permitted only for `GENERATED` plus `PENDING_NATIVE_TOPOLOGY`;
- a pre-marker EQ-1 profile is `UNKNOWN` and fails closed rather than inferring intent from a flat
  curve.

This deliberately leaves older ambiguous snapshots pending. A direct legacy-to-EQ-3 update is
eligible because initialization records `GENERATED`; users who already explicitly edit EQ-1
settings retain precedence. The raw snapshot is never erased.

On success, one `PreferenceStore.Edit` writes the complete canonical profile and
`MIGRATED` state together. The legacy snapshot keys are not written, removed, synchronized, or
otherwise changed. `MIGRATED` prevents a second conversion; `DORMANT`, `UNKNOWN`, and
user-established profiles are not migrated.

## Intentional Unmigrated Cases

- Android system/native `EQ_PRESET` values, including seemingly valid indices: inspecting them
  would require changing the active native Equalizer and no safe isolated reader exists here.
- A missing or malformed selected user preset.
- Topology/count/range mismatch, no session, no Equalizer, invalid centers, and ambiguous
  pre-marker EQ-1 profile authority.
- All track/folder legacy values.

These cases preserve raw data and do not fall back to Flat, a nearest preset, guessed band
frequencies, or unrelated profile data.

## Tests

Focused new coverage includes:

- raw manual bands for matching, mismatched, empty, and out-of-range data;
- one-, five-, and ten-band native topologies;
- zero, duplicate, descending, and malformed center frequencies;
- exact centers, logarithmic interpolation, lower/upper endpoint clamping, flat, positive,
  negative, and mixed curves;
- millibel-to-dB conversion and `-0.4`, `-0.5`, `+0.4`, `+0.5` rounding;
- selected user-preset parsing and raw retention;
- valid and invalid system preset indices remaining safely pending without inspection;
- generated default migration, explicit unified save/edit protection, already-`MIGRATED`,
  `DORMANT`, and ambiguous pre-marker authority;
- repository migration followed by exactly one existing controller application, with no direct
  migration-to-backend call.

Verification:

- `:fermata:testAutoDebugUnitTest`: PASS.
- `:fermata:testMobileDebugUnitTest`: PASS.
- 349 combined XML result files, zero failures or errors.
- `:fermata:packageAutoReleaseUniversalApk`: PASS.
- `apksigner verify --verbose --print-certs`: PASS; one approved signer, APK Signature Scheme
  v3 verified.
- `git diff --check`: PASS.

## Invariants

| Invariant | Result |
| --- | --- |
| `MediaSessionCallback.java` nonblank LOC | 2174 before and after |
| `YoutubeWebView.java` nonblank LOC | 1248 before and after |
| `YoutubeMediaEngine.java` nonblank LOC | 1121 before and after |
| Android manifest diff | None |
| Web module diff | None |
| `aauto.aar` SHA-256 | `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B` |
| New production session-0 construction | None |
| Normal `AudioEffectsLegacyApplier.apply` call | None |
| `THIRD_PARTY_NOTICES.md` | Unchanged; no copied external DSP code |

No runtime backend redesign, WebAudio, session-0 production processing, track/folder migration,
or legacy rollback-data deletion was introduced. No device sideload was required for this
model-and-storage phase.

## Deferred

- WEBEQ-A Stremio.
- WEBEQ-B YouTube.
- EQ-X session-0 research only.
- Final physical-device and Android Auto/DHU audible validation of the EQ-2 native-session path.
