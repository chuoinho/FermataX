# Phase 0A Report: Provenance And Legacy Baseline

## Scope

Capture the reproducible baseline before the Web-only migration. No legacy dependency or runtime
component is removed in this phase.

## Baseline

- FermataX HEAD: `dd3b11ed0499425a382acc897b8e7dd58376d12a`
- App version: `304` / `2.0.1`
- Android SDK: min `28`, target/compile `36`; NDK `29.0.14206865`
- AndroidX WebKit: `1.16.0`
- Hosted upstream baseline documented by the package: Stremio Web `9f2e63b58b5e6ae0a24a1223ca7f0991fef2ba71`,
  observed release `v5.0.0-beta.39`

## Dependency And Native Inventory

The current Gradle project graph contains `:stremio`, `:web`, and a separate `:coreprobe` project.
`modules/stremio/build.gradle` is the only production consumer of the following FrostWire
dependencies:

- `com.frostwire:jlibtorrent:2.0.12.9`
- `com.frostwire:jlibtorrent-android-arm:2.0.12.9`
- `com.frostwire:jlibtorrent-android-arm64:2.0.12.9`
- `com.frostwire:jlibtorrent-android-x86_64:2.0.12.9`

The FrostWire Maven repository and the jlibtorrent ProGuard keep rule likewise exist only to
support the legacy addon. The `modules/stremio` surface contains 519 tracked files, including 445
Java files and 52 XML resources.

`modules-probe/coreprobe` and the CI checkout of `chuoinho/stremio-core-java` are unrelated to the
legacy dynamic feature but are obsolete under the Web-only target. They remain unchanged until the
Phase 8 cleanup gate proves there is no remaining consumer.

## APK Baseline

Command:

```text
./gradlew :fermata:packageAutoDebugUniversalApk --no-daemon --no-parallel --console=plain
```

Result: PASS.

- Artifact: `fermata-2.0.1-me.app.fermataX.auto-auto-debug-universal.apk`
- Size: `380,898,411` bytes (`363.25 MiB`)
- Native libraries: 24 across `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- Legacy jlibtorrent libraries: three, totalling approximately `35.24 MiB`
  (`13,344,104`, `10,543,076`, and `13,054,656` bytes respectively)

## CI And Security Baseline

- CI currently checks out `chuoinho/stremio-core-java` and runs `:coreprobe:lintDebug`.
- The current Web shell enables JavaScript, DOM storage and third-party cookies, cancels SSL errors,
  and already provides renderer-recovery and diagnostics extension points.
- No Stremio-specific external-intent validator exists yet. The Phase 2 implementation must keep
  raw intent/media URLs, tokens, cookies, magnet data, and account data out of diagnostics.
- Stremio Web is GPL-2.0 upstream, but the selected hosted-origin model does not copy or distribute
  its bundle. Its provenance and hosted-use limitation are recorded in the Web-only authority docs.

## Verification

- `:stremio:dependencies --configuration autoDebugRuntimeClasspath` resolved precisely the four
  FrostWire artifacts listed above.
- Repository search found no FrostWire/jlibtorrent production consumer outside the legacy module,
  its build catalog/repository, and its ProGuard keep rule.
- `:fermata:projects` confirmed the baseline graph includes both `:stremio` and `:coreprobe`.
- Universal APK inspection confirmed all three `libjlibtorrent-2.0.12.9.so` ABIs are packaged.

## Impact And Rollback

This is an evidence-only phase. Phone, Android Auto, DHU, user data, runtime lifecycle, and APK
behavior are unchanged. Revert the documentation-only commits to remove this baseline record.

## Known Limitations

Production feasibility of the hosted origin, canonical hash-route capture, and Android
external-player intent handoff remains unproven. They are the mandatory Phase 0B gate.

## Exit Gate

PASS. Legacy consumers and their packaged native cost are fully identified; no deletion has been
performed before migration safety gates.
