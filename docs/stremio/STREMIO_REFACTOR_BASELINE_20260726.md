# Stremio Refactor Baseline

Captured: 2026-07-26

## Scope

This is the behavior-preserving baseline for the Stremio addon refactor. The baseline
covers the current `modules/stremio` implementation and the shared FermataX contracts
it calls. Unrelated dirty worktree changes are intentionally excluded from this scope.

## Source Backup

- Archive: `E:\Chatgpt\fermata-backups\FermataX-stremio-refactor-baseline-20260726-221659.zip`
- Contents: `modules/stremio/` and `docs/stremio/`
- SHA-256: `428CF3764A0DB7B8AB43D27F413F541648731C95C634FF0E48FCF841C99418A5`

## Current Size

- Production Java files: 276
- Production Java lines: approximately 25,654
- Test files: 102
- Largest classes:
  - `StremioPresentationGateway`: approximately 1,593 lines
  - `StremioSessionGatewayAdapter`: approximately 988 lines
  - `StremioRepository`: approximately 937 lines
  - `StremioFragment`: approximately 893 lines
  - `StremioPresentationAdapter`: approximately 748 lines

These measurements identify refactor hotspots. They are not standalone success criteria.

## Verified Contracts

- Addon entry point remains `me.aap.fermata.addon.stremio.StremioAddon`.
- Addon capability remains dashboard, navigation, Stremio and voice search.
- Existing route stable IDs do not contain transport URLs.
- Source identity remains immutable and source-scoped.
- Protocol responses remain separated from presentation models.
- Progress ownership rejects stale generations.
- Source disable/remove invalidates provider availability without deleting unrelated addons.
- Subtitle selection remains scoped by video key.
- Torrent cache cleanup remains bounded to its owned directory.
- Universal APK contains Stremio resources, including the Stremio presentation layouts and icon.

## Test Evidence

Commands:

```text
./gradlew :stremio:testAutoDebugUnitTest
./gradlew :stremio:testMobileDebugUnitTest
./gradlew :fermata:compileAutoReleaseJavaWithJavac :stremio:lintVitalAnalyzeAutoRelease :fermata:packageAutoReleaseUniversalApk
```

Results:

- Auto unit tests: 452 tests, 0 failures, 0 errors.
- Mobile unit tests: 452 tests, 0 failures, 0 errors.
- Auto release Java compilation: pass.
- Stremio release lintVital analysis: pass.
- Universal APK packaging: pass.
- Universal APK SHA-256: `11F5887E80F72D78A0FE7B3B8676DADFA54E3D55D628BC15AD292D933D2D583D`.

New characterization coverage in this baseline:

- Route-cache eviction reloads an evicted route instead of returning stale content.
- Details-page subtitle action keeps an Android Auto hit target of at least 48dp.

## Refactor Rules

1. Preserve public addon, MediaLib, playback, subtitle and database contracts.
2. Keep `StremioAddon` and the existing dynamic-feature registration unchanged.
3. Extract responsibilities behind the existing facades before changing call sites.
4. Do not replace all `FutureSupplier`/`CompletableFuture` usage in one phase.
5. Do not move raw provider URLs into route or UI state.
6. Do not change Back, playerbar, top bar, SmartTopCard or MediaSession ownership.
7. Delete code only after a caller search and focused regression test prove it is unused.
8. Run focused and regression tests after every phase.

## Phase 0 Exit

Phase 0 is complete. The next phase is async/lifecycle boundary extraction. No production
behavioral rewrite should begin until this file and the source backup are preserved.
