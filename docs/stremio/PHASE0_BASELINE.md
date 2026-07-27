# Stremio Phase 0 Baseline

Captured: 2026-07-21

## Source State

- Repository: `E:\Chatgpt\fermata`
- Branch: `main`
- HEAD: `8ad7a27ea409f634040915548155208068c5facf`
- Version code: `299`
- Version name: `2.0.1`
- Worktree: dirty before Stremio runtime implementation; existing user/goal changes are preserved.
- The initial status count was 81 changed/untracked paths after Phase 0 fixture/test additions. This
  count is evidence only and must not be used to revert unrelated files.

## Toolchain

- Gradle: `9.5.1`
- Android Gradle Plugin: `9.2.1`
- Java: Microsoft OpenJDK `17.0.19`
- Compile SDK: `36`
- Target SDK: `36`
- Minimum SDK: `28`
- Host: Windows 11 amd64

## Baseline Verification

Command:

```powershell
.\gradlew.bat :fermata:testAutoDebugUnitTest --no-daemon
```

Result: `BUILD SUCCESSFUL` in approximately two minutes, 157 actionable tasks, four executed and
153 up-to-date. The run included the newly added Phase 0 progress characterization test because
the sub-agent completed that test while the build was configuring. Production runtime code was not
changed by that test addition.

One environmental warning was observed: Android analytics settings were temporarily locked by
another process. It did not affect compilation or tests.

## Existing Artifact Size Reference

The newest pre-Stremio runtime artifacts already present in the workspace were measured without
rebuilding or modifying them:

| Artifact | Bytes |
| --- | ---: |
| Auto release AAB (`me.app.fermataX.auto`) | 174,366,441 |
| Auto release APK output (`me.app.fermataX.auto.test`) | 8,863,249 |
| Universal APK generated from the Auto release bundle | 329,725,815 |

The direct APK and bundle-derived universal APK are different packaging products and must not be
compared as if they contained the same dynamic features. Phase 8 repeats the same artifact commands
and records per-module contents before attributing any size delta to Stremio.

## Phase 0 Artifacts

- `docs/stremio/STREMIO_ADDON_GOAL.md`
- `docs/stremio/REFERENCES.md`
- `modules/stremio/src/test/resources/stremio/`
- `fermata/src/test/java/me/aap/fermata/media/service/PlaybackProgressOwnershipCharacterizationTest.java`

## Baseline Rule

Every Stremio phase must compare its focused and regression results to this baseline. Existing
dirty files may be changed only when the phase explicitly owns them and the change has a focused
test. No cleanup or unrelated formatting is allowed during Stremio implementation.
