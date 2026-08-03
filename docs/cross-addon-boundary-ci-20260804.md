# Cross-addon boundary CI guard — 2026-08-04

## Decision and current state

Every directory under `modules/` is an Android dynamic-feature module discovered by
`settings.gradle`. The allowed dependency direction is feature module to the `:fermata`
application/core API, `:utils`, Android/JDK APIs, and external libraries. A feature module
must not import implementation classes owned by another feature module or declare a
Gradle project dependency on another feature module.

The authoritative package ownership map contains all 16 current modules: audiobook,
cast, chat, exoplayer, gdrive, mlkit, opusmt, podcast, radio, sftp, smb, stremio, tv, vlc,
web, and whisper.

Before implementation, a fresh scan of every Java/Kotlin source file under every module
source set found exactly zero cross-addon imports. Every module `build.gradle` depended
only on `:fermata` and `:utils`; cross-addon Gradle project dependency count was also zero.
No exception or suppression is required.

## Enforcement

`ArchitectureBoundaryTest.addonModulesDoNotImportSiblingImplementations` now:

- fails if the package ownership map and physical `modules/` directories diverge;
- fails if a module declares source outside its owned package prefix;
- scans every Java and Kotlin file under every module source set, including tests;
- handles normal imports, static imports, and Kotlin aliased imports;
- reports the exact file, line, source module, target module, and offending import;
- rejects sibling `project(':module')` dependencies in module build files.

The existing GitHub Actions step is renamed `Architecture boundary guards` and explicitly
runs both the hotspot baseline and cross-addon boundary methods. No allowlist or blanket
suppression file exists.

## Fail-proof verification

Verification temporarily adds a fake `chat -> stremio` import in a scratch test source,
confirms the guard fails with that exact file/import in its diagnostic, removes the probe,
and confirms the same command passes. The scratch violation is never committed.

Hosted-runner evidence is supplied in the task delivery report for the implementation
commit after GitHub Actions completes.
