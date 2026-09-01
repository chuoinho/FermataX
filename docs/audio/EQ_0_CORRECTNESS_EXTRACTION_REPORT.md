# EQ-0 Correctness Extraction Report

## Scope

- Extracted legacy audio-effect preference application from `MediaSessionCallback`.
- Corrected manual Equalizer band application to use the effect's band count, not preset count.
- Clamped manual band values to the current hardware range.
- Made AudioEffect construction and release transactional so a partial failure releases every
  already-created effect.

## Preserved behavior

- Legacy preference precedence remains track, then parent folder, then playback controls.
- Built-in presets continue to use `getNumberOfPresets()` and `usePreset()`.
- No-enabled-profile behavior still disables all available legacy effects.
- There are no changes to settings, preference keys, player engines, Android Auto resources,
  YouTube, Stremio, manifests, or `aauto.aar`.

## Verification

- `MediaSessionCallback` nonblank lines: 2276 before, 2174 after.
- `YoutubeWebView` nonblank lines: 1248 before and after.
- `YoutubeMediaEngine` nonblank lines: 1121 before and after.
- `./gradlew.bat :fermata:testAutoDebugUnitTest :fermata:testMobileDebugUnitTest`: PASS.
  - Auto: 174 test result files, zero failures or errors.
  - Mobile: 163 test result files, zero failures or errors.
- `./gradlew.bat :fermata:packageAutoReleaseUniversalApk`: PASS.
  - Output: `fermata/build/outputs/apk_from_bundle/autoRelease/fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk`.
  - `apksigner verify --verbose`: one signer, APK Signature Scheme v3 verified.
- `aauto.aar` SHA-256: `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`;
  its Git object matches `HEAD`.
- `git diff --check`: PASS.

## Added coverage

- `AudioEffectsLegacyApplierTest` proves manual values are bounded by the native band topology
  and hardware range.
- `AudioEffectsCreationTransactionTest` proves acquired resources release in reverse order on
  rollback and remain owned after commit.

## EQ-0 decision

PASS. EQ-1 may begin only as a separate, audited change. This phase deliberately does not add a
unified profile, session controller, DynamicsProcessing, global output effects, or WebAudio.
