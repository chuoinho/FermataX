# Phase 7: Final Acceptance And Release Audit

## Result

**RELEASE READINESS: PARTIAL.** The signed Web-only universal APK builds, verifies, installs and
passes the bounded physical smoke path. It is not promoted to overall PASS because several
independent acceptance boundaries remain unobserved or blocked.

Production LOC: `0`.

Test LOC: `0`.

Documentation LOC: this report and the acceptance/status documents only.

## Build And Identity Evidence

The following command completed successfully with `WEB_STREMIO=true` and the approved local
upload credentials:

```text
.\gradlew.bat -PWEB_STREMIO=true projects :web:testMobileDebugUnitTest
  :fermata:packageAutoReleaseUniversalApk --no-daemon
```

- `:web:testMobileDebugUnitTest`: PASS.
- The resolved project graph contains `:web` and does not contain `:stremio`.
- Universal release packaging and the release-identity verification task: PASS.
- APK: `fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk`.
- APK signer SHA-256: `A8:6D:57:6F:F1:EC:0E:32:45:F5:A6:15:2C:5D:8D:66:B5:DF:8B:B5:10:82:0D:75:DC:61:11:0B:19:C3:AE:B4`, matching the approved upload certificate.
- Immutable `aauto.aar` SHA-256: `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

## Architecture And Dependency Audit

The `WEB_STREMIO=true` build graph excludes `:stremio`; its release runtime dependency report
contains no legacy `jlibtorrent`/libtorrent dependency and the APK has no corresponding native
payload. `modules/stremio` and its catalog entries remain in the repository for non-Web-only
builds, so this report makes no false filesystem-wide absence claim.

The active Stremio implementation is `StremioWebAddon` plus the official hosted Web UI. It has no
Stremio DOM scraping, stream URL extraction, native player, embedded streaming server, Stremio
Core or torrent engine. The origin-scoped MediaSession compatibility bridge injects a bounded
control shim and dispatches only `play`, `pause` and, if registered, `nexttrack`; it neither reads
page content nor credentials, cookies, storage or media URLs. The generic WebView retains its
pre-existing JavaScript interface, which is not a Stremio-specific extraction path.

## Physical Release Smoke

- Physical device: `15c36230`.
- The signed universal APK installed successfully with `adb install -r`.
- Launch reached `me.aap.fermata.ui.activity.MainActivity`.
- Dashboard -> Stremio opened the hosted `#/addons` route inside FermataX's WebView. No external
  application launch occurred.
- Dashboard -> YouTube selected the YouTube addon while FermataX remained in `MainActivity`.
- The MediaSession was `NONE` when no playback was active, which is the expected safe state for
  this bounded smoke and confirms no stale active Stremio claim was left behind.
- No FermataX crash, ANR or renderer fatal event was observed in the collected logcat window.

The smoke deliberately did not start playback, alter the account, addons or Stremio settings, or
create a fixture, streaming server, forward or reverse rule.

## Remaining Acceptance State

| Boundary | Status | Reason |
| --- | --- | --- |
| Direct MP4/HLS, seek, explicit WebVTT subtitles, fullscreen/Back | PASS | Physical P5B5 evidence. |
| Native MediaSession decoration and ADB play/pause/toggle | PASS | Physical P1C evidence. |
| Lifecycle, renderer recovery and addon switching | PASS | Physical P1C evidence. |
| Catalog/search/library/settings and focused unaffected-addon sweep | PASS | Physical Phase 6B/6C evidence. |
| Stremio active MediaSession plus DHU host controls | PARTIAL | Direct hosted playback reached Range `206`, but the active MediaSession claim remained `NONE`; no valid DHU control test was run. |
| Hosted multi-audio choice | NOT OBSERVED | A real two-track MP4 rendered, but no hosted selector was exposed. |
| `nexttrack` | NOT OBSERVED | Phase 6F did not reach an active episode Player/session, so upstream action registration could not be observed. |
| User-configured streaming-server/torrent transport | BLOCKED | No separately approved reproducible server-backed torrent environment exists. |
| Fermata native torrent/player | NOT APPLICABLE | It violates the approved Web-only architecture. |

## Cleanup

- No Phase 7 fixture, server, media asset, forward or reverse rule was created.
- Validation ports `7000`, `7001` and `7002` remain unused by Phase 7; no Phase 7 process remains.
- Temporary UI-dump captures remain outside the worktree in local Temp because the available
  command-safety policy rejected their deletion. They contain only the bounded visible UI
  hierarchy, have no process, port, reverse rule or installed-addon reference, and are not
  release inputs.
- The worktree contains documentation changes only.

## Decision

Ship-ready packaging is proven, but release acceptance remains **PARTIAL**. Do not use this phase
as evidence for DHU controls, audio selection, next-track behavior or torrent/server playback.
Those boundaries require independently approved physical evidence.
