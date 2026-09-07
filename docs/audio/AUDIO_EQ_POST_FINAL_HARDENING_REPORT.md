# Audio & Equalizer Post-Final Hardening

## Final Status

`AUDIO_EQ_POST_FINAL_HARDENING = PARTIAL`.

The correctness, migration, resource, and EQ-curve work is `PASS`. Native limiter
support remains `PARTIAL`: Android API availability was verified, but no trustworthy
acoustic or clipping measurement was available. The production backend therefore does
not create, advertise, or rely on a limiter capability.

## Baseline / Final HEAD

- Baseline: `948f02d0` (`docs(audio): record unified audio final acceptance`)
- Worktree branch: `codex/eq2-native-session-backend`
- Final commit is recorded after the implementation commits below.
- `fermata/lib/auto/aauto.aar` SHA-256:
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`

## Step 1 - Dead Legacy Cleanup

### Reference Audit

Full production/reference searches found no live caller, XML registration, manifest
entry, fragment factory registration, or migration dependency for the old stack. The
only remaining symbol text is intentional negative architecture-test coverage.

### Deleted Production Files

- `media/engine/AudioEffects.java`
- `media/service/AudioEffectsLegacyApplier.java`
- `ui/fragment/AudioEffectsFragment.java`
- `ui/view/AudioEffectsView.java`

`MediaEngine.getAudioEffects()` and the old `MainActivityDelegate` fragment route were
removed with those owners. Snapshot capture and unified profile migration remain.

### Deleted Tests and Resources

- `AudioEffectsCreationTransactionTest.java`
- `AudioEffectsLegacyApplierTest.java`
- `layout/audio_effects.xml`
- `layout/equalizer_band.xml`
- `audio_effects_fragment` resource ID

### Remaining Legacy Migration Code

`LegacyAudioEffectsSnapshot` and `AudioEffectsProfileRepository` retain legacy source
values only as migration/rollback data. They do not recreate a legacy runtime effect.

**Verdict: `DEAD_LEGACY_AUDIO_STACK = PASS`.**

## Step 2 - Native Preset Migration

### Legacy Encoding and Root Cause

The legacy encoding is `0` for manual bands, positive values for Android system presets
using `legacyPreset - 1` as the Android preset index, and negative values for saved user
presets. A positive system preset had no portable raw band vector, so the former
migration left the unified curve flat and pending without informing the user.

### Resolver Architecture and Safe Resolution

`LegacyEqualizerPresetResolver` is a narrow, package-private boundary. The repository
receives immutable raw levels only; it does not depend on Android audio APIs or a media
engine. `NativeSessionAudioEffectsBackend` resolves a system preset only on a fresh
non-zero-session `Equalizer`, keeps that effect disabled, reads its levels, and releases
normal control back to the unified backend.

### Failure/Fallback Behavior

Resolver absence, exception, invalid band data, and topology mismatch preserve the
legacy snapshot, keep the canonical curve unmodified, and enter
`FALLBACK_NOTICE_PENDING`. Settings consumes the localized notice exactly once before
the state becomes `DORMANT`. An explicit user edit also preserves that pending notice,
so a user is never silently treated as having accepted a replacement curve.

### Physical Evidence and Tests

Unit coverage exercises first/middle/last positive-preset semantics through the
resolver boundary, successful mapping, unavailable/throwing/invalid resolver results,
the one-time notice, and an explicit user override. No test creates a real effect.

No device had an eligible legacy native system preset to validate that configuring a
disabled resolver effect produces no audible transient. This does not weaken the
fallback path, but the transient claim is `NOT_OBSERVED`.

**Verdict: `LEGACY_NATIVE_PRESET_MIGRATION = PASS`; physical transient behavior is
`NOT_OBSERVED`.**

## Step 3 - Limiter

### DynamicsProcessing Topology

Android 35 API inspection confirmed that `DynamicsProcessing.Limiter`,
`Config.Builder.setLimiterAllChannelsTo(...)`, and `Config.isLimiterInUse()` exist.
The existing native backend remains the authority for standard-live, initial-only, and
unavailable routes.

### Capability, Gain Safety, and Physical Results

No limiter is configured and `AudioEffectCapability.LIMITER` is not advertised. The
existing conservative no-limiter `GainSafetyPolicy` remains unchanged. Playback stays
fail-open; a future limiter capability must remain fail-closed if creation or validation
fails.

Phone and AA acoustic/clip validation are `NOT_OBSERVED`; no synthetic or headphone
listening result has been represented as measurement.

**Verdict: `NATIVE_LIMITER = PARTIAL` (`API_CONFIG_PASS`,
`ACOUSTIC_VALIDATION_NOT_OBSERVED`, `NOT_ENABLED_IN_PRODUCTION`).**

## Step 4 - Resources / I18n

### Resource Rename

The typo resource was renamed atomically from `equalier` to `equalizer`. Remaining
occurrences are negative assertions in `AudioEffectsProfileArchitectureTest`; there is
no production reference or compatibility alias.

### Locale Matrix

The default resources and all sixteen locale directories include `audio_equalizer`,
`equalizer`, `preamp`, and `legacy_preset_migration_notice`:

`values`, `values-ar`, `values-de`, `values-es`, `values-fr`, `values-hr`, `values-it`,
`values-ja`, `values-km`, `values-ko`, `values-pl`, `values-pt`, `values-ro`,
`values-ru`, `values-tr`, `values-vi`, and `values-zh-rTW`.

The architecture test enforces the required keys in every shipped locale. There are no
default-only fallbacks for the new notice.

**Verdict: `RESOURCE_I18N_HARDENING = PASS`.**

## Step 5 - EQ Curve

### Architecture and Behavior

`EqualizerCurveView` is a read-only custom view with no new dependency and no separate
state. It reads the same ten persisted canonical bands used by the numeric/seek
controls, lays them out logarithmically from 31 Hz to 16 kHz, clamps the display to
-15..+15 dB, and invalidates from profile-store notifications. Master or EQ disabled
keeps the stored curve visible with reduced opacity.

### Reuse Regression and Physical Evidence

Physical verification on device `15c36230` discovered a recycled preference row could
retain `height = 0` from a hidden band row. It made the curve disappear after EQ was
disabled. `PreferenceView.setViewPreference()` now restores `WRAP_CONTENT`; a new
Robolectric regression test first failed on the old behavior and passes with the fix.

On the signed `me.app.fermataX.auto.test` release build, the curve was observed as:

- neutral when all bands were zero;
- changed immediately after 31 Hz was set to -6 dB;
- retained and muted with Master disabled;
- retained and muted with EQ disabled;
- retained after force-stop/relaunch with Master enabled and EQ disabled.

Evidence screenshots are intentionally untracked under `.native-eq-a-temp/`.

**Verdict: `EQ_CURVE_VISUALIZATION = PASS`.**

## Cross-Backend Regression Matrix

| Surface | Status | Evidence |
| --- | --- | --- |
| Native phone playback/effects | `NOT_OBSERVED` in this hardening run | Historical accepted baseline retained; Settings/profile persistence was observed. |
| Native AA initial-only route | `NOT_OBSERVED` in this hardening run | Historical accepted baseline retained; no route policy was changed. |
| YouTube phone | `NOT_OBSERVED` in this hardening run | Historical accepted baseline retained; no WebAudio source changed. |
| YouTube AA/DHU blob/MSE | `NOT_OBSERVED` in this hardening run | Historical accepted baseline retained; no WebView/AA source changed. |
| Stremio supported path | `NOT_OBSERVED` in this hardening run | Out of scope; no Stremio code changed. |

## Automated Tests

Command:

```text
.\\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest --no-daemon --console=plain
```

- Fermata: 880 tests, 0 failures, 0 errors, 2 skipped
- Web: 217 tests, 0 failures, 0 errors, 0 skipped
- `git diff --check`: clean

## Architecture / Hotspots

The architecture test verifies removed legacy files/routes, resource cleanup, all-locale
string coverage, one profile authority, no session-0 `Equalizer` or
`DynamicsProcessing`, and no legacy factory/application calls.

Current hotspot counts:

- `YoutubeWebView.java`: 1246
- `YoutubeMediaEngine.java`: 1121
- `MediaSessionCallback.java`: 2182
- `MainActivityDelegate.java`: 1139
- `ControlPanelView.java`: 758

No ceiling was changed in this work.

## Audit Round 1 - Correctness

`PASS`: old stack has no production reference; native-system-preset migration either
maps deterministically from a resolver result or preserves data and informs the user;
the fallback is one-time; no profile data is fabricated.

## Audit Round 2 - Architecture / Safety

`PASS`: no session-0 production EQ, no legacy engine resurrection, no second DSP
owner, no generic WebAudio/DRM/URL handling change, no playback restart workaround, no
new dependency, and `aauto.aar` remains byte-identical.

## Audit Round 3 - UX / Release

`PASS`: ten existing controls remain authoritative; the new curve follows stored state;
the hidden-row recycle failure has automated and physical coverage; i18n compiles; the
signed release build installs and launches. Limiter acoustic validation remains
`NOT_OBSERVED` and is not advertised.

## Final Verdict

- `DEAD_LEGACY_AUDIO_STACK = PASS`
- `LEGACY_NATIVE_PRESET_MIGRATION = PASS`
- `NATIVE_LIMITER = PARTIAL`
- `RESOURCE_I18N_HARDENING = PASS`
- `EQ_CURVE_VISUALIZATION = PASS`
- `AAUTO = PASS`
- `ARCHITECTURE = PASS`
- `AUDIO_EQ_POST_FINAL_HARDENING = PARTIAL`

The sole remaining gap is a separately authorized limiter experiment with credible
physical acoustic or PCM clipping measurement. It must not enable the capability until
that evidence exists.
