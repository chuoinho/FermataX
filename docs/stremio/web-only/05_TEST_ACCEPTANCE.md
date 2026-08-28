# 05 - Acceptance Matrix

## Status vocabulary

- **PASS**: required evidence was observed on the required physical surface.
- **PARTIAL**: some, but not every, required scenario was observed.
- **NOT OBSERVED**: no claim is made because the relevant event was absent.
- **BLOCKED**: a prerequisite or an explicit scope decision is still required.
- **CONDITIONAL_NOT_ADVERTISED**: the optional upstream action was not registered;
  this is not a failure.
- **NOT APPLICABLE**: deliberately outside the approved Web-only architecture.

| Requirement | Evidence required | Current state |
|---|---|---|
| One Web-only addon, no legacy feature | Gradle projects/dependency graph | PASS |
| Addon is enabled and opens hosted origin | Physical device UI | PASS |
| Existing authenticated Web session remains | Physical device avatar/session UI | PASS |
| Signed Web-only universal release package | Approved signing certificate, Web-only Gradle graph, immutable `aauto.aar`, device smoke | PASS (Phase 7) |
| Catalog/search/library/settings sweep | Physical device manual sweep of all four hosted sections | PASS (Phase 6B hosted UI sweep) |
| Android WebView Media Session compatibility | Physical device DevTools and logcat | PASS |
| ADB play/pause/toggle control | Physical device trailer and `dumpsys media_session` | PASS |
| Active hosted Stremio MediaSession claim | Physical direct-stream fixture reaches Player; UI state, progressing position and `dumpsys media_session` agree | PASS (Phase 8A: active `PLAYING` session, control actions and title metadata observed) |
| Next control | Physical device only when Stremio registers `nexttrack` in an active episode session | CONDITIONAL_NOT_ADVERTISED (P8C reached Episode 1 Player and its Episode 2 overlay, but the current native MediaSession advertised no actions and no `nexttrack`; no synthetic next was sent) |
| Inline HTTP MP4/HLS playback | Physical device with working server | PASS (P5B5 direct MP4 and HLS fixture) |
| Seek | Physical device MP4 fixture plus changed range request | PASS (P5B5 F4) |
| Fullscreen without reload/position loss | Physical device video fixture | PASS (P1B/Phase 5B5 preserved hosted Player and observed position) |
| Back order | Physical device video fixture | PASS (P5B5 fullscreen exit preserves Player/playback) |
| Subtitle selection | Physical device video fixture | PASS (P5B5 explicit `OFF` -> `English`, rendered WebVTT) |
| Audio-track selection | Physical device fixture with multiple advertised audio tracks | NOT OBSERVED (P8B dual-audio HLS reached Player, loaded the default English rendition and visibly rendered video, but did not expose a selector for the advertised Vietnamese rendition) |
| Background/recovery/switching | Physical device lifecycle matrix | PASS (P1C Home/reopen, lock/wake, renderer recovery and addon switching) |
| Android Auto/DHU host media controls | DHU or vehicle-host input reaches current Stremio claim | PARTIAL (DHU connected through the pre-existing ADB transport but remained `Waiting for phone`; no Android Auto projection surface or host input reached FermataX. A fresh lifecycle playback retry also observed native session `NONE`, so it could not be used as a valid control target.) |
| No impact to other addons | Focused physical regression sweep across unaffected addons | PASS (Phase 6C entry/render/switch baseline; not a substitute for each addon's playback suite) |
| Torrent stream through a user-configured Stremio streaming server | Physical visible stream selection, local torrent metadata/piece transfer, ranged server response and hosted Player rendering | PASS (P8G self-owned local-only fixture; historical P8D blockers are superseded) |
| Fermata native torrent transport/player | Architecture review | NOT APPLICABLE (approved architecture is hosted Stremio Web plus user-configured server) |

`PASS` requires observed evidence, not source-code inference. Historical blockers
in Phase 5B3 and earlier are retained as evidence, but the direct MP4/HLS
Player boundary is superseded by the complete P5B5 physical matrix.
Renderer-loss recovery is PASS under P1C; earlier documents that say it was
unverified describe their own earlier test boundary.

## Release Gate

The signed universal Web-only package passed Phase 7 build, dependency, immutable-input and
bounded physical smoke checks. This is a packaging and regression gate, not evidence that the
remaining playback-host boundaries work. The release-readiness state is therefore **PARTIAL**:

- `PARTIAL`: DHU or vehicle-host input has not yet been observed reaching the
  now-proven active Stremio MediaSession claim.
- `NOT OBSERVED`: hosted multi-audio selector.
- `CONDITIONAL_NOT_ADVERTISED`: episode `nexttrack` was not registered by the
  observed native MediaSession; this is not a failure.
- `PASS`: P8G observed the separately governed streaming-server/torrent path
  using a self-owned, loopback-only fixture. This does not change the Web-only
  architecture or claim any native Fermata torrent capability.

The legacy `modules/stremio` source tree remains in the repository for non-Web-only builds, but
`-PWEB_STREMIO=true` excludes it from the Gradle graph and release APK. The web-only artifact has
no legacy native torrent payload. This scope distinction is intentional and prevents an
over-broad claim that the repository contains no legacy code at all.
