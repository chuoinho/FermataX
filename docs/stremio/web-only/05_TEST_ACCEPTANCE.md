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
| Catalog/search/library/settings sweep | Physical device manual sweep of all four hosted sections | PASS (Phase 6B hosted UI sweep) |
| Android WebView Media Session compatibility | Physical device DevTools and logcat | PASS |
| ADB play/pause/toggle control | Physical device trailer and `dumpsys media_session` | PASS |
| Next control | Physical device only when Stremio registers `nexttrack` | CONDITIONAL_NOT_ADVERTISED (no physical page registered the action) |
| Inline HTTP MP4/HLS playback | Physical device with working server | PASS (P5B5 direct MP4 and HLS fixture) |
| Seek | Physical device MP4 fixture plus changed range request | PASS (P5B5 F4) |
| Fullscreen without reload/position loss | Physical device video fixture | PASS (P1B/Phase 5B5 preserved hosted Player and observed position) |
| Back order | Physical device video fixture | PASS (P5B5 fullscreen exit preserves Player/playback) |
| Subtitle selection | Physical device video fixture | PASS (P5B5 explicit `OFF` -> `English`, rendered WebVTT) |
| Audio-track selection | Physical device fixture with multiple advertised audio tracks | NOT OBSERVED (Phase 6E physical two-track MP4 reached Player, but hosted UI did not advertise a selector) |
| Background/recovery/switching | Physical device lifecycle matrix | PASS (P1C Home/reopen, lock/wake, renderer recovery and addon switching) |
| Android Auto/DHU host media controls | DHU or vehicle-host input reaches current Stremio claim | PARTIAL (Phase 6D confirmed hosted Range playback, but the native session remained `NONE`; no valid DHU input path yet) |
| No impact to other addons | Focused physical regression sweep across unaffected addons | PASS (Phase 6C entry/render/switch baseline; not a substitute for each addon's playback suite) |
| Torrent stream through a user-configured Stremio streaming server | Separate approved server/fixture decision and physical evidence | BLOCKED (not a Fermata native-player or native-torrent requirement) |
| Fermata native torrent transport/player | Architecture review | NOT APPLICABLE (approved architecture is hosted Stremio Web plus user-configured server) |

`PASS` requires observed evidence, not source-code inference. Historical blockers
in Phase 5B3 and earlier are retained as evidence, but the direct MP4/HLS
Player boundary is superseded by the complete P5B5 physical matrix.
Renderer-loss recovery is PASS under P1C; earlier documents that say it was
unverified describe their own earlier test boundary.
