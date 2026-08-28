# 03 - Implementation Plan

## Phase 1A - Hosted addon

- Register one `stremio_fragment` from `:web` only when `WEB_STREMIO=true`.
- Exclude legacy `:stremio` from that build graph.
- Use a dedicated preference namespace and official hosted URL.
- Reuse generic WebView fullscreen, renderer-recovery and Back behavior.

## Phase 1B - Player validation

- On a physical device with a user-configured server, play a Stremio Web HTTP MP4 and HLS stream.
- Enter/exit browser fullscreen without reload or position reset.
- Verify Back exits fullscreen before history and no external-player chooser appears.

## Phase 1C - Lifecycle validation

- Background/resume, screen lock/unlock, renderer recovery and addon switching.
- Verify Stremio login and server settings persist across app restart.
- Verify server errors do not crash or ANR FermataX.

Do not add native playback, transport, torrent or DOM-automation code to satisfy a missing Phase
1B fixture. Those alternatives are outside this architecture.

## Current Acceptance Baseline

The Phase 1 implementation and direct playback/lifecycle acceptance work are
complete. The reconciled physical evidence and the remaining independent
acceptance phases are maintained in `05_TEST_ACCEPTANCE.md` and
`reports/PHASE_6A_ACCEPTANCE_RECONCILIATION_REPORT.md`. They do not change the
Web-only architecture defined above.

Phase 8E re-verified the signed universal sideload artifact and hosted entry on
the physical device. P8G later closed the separately governed
streaming-server/torrent boundary with a self-owned loopback-only fixture.
P8H completed the DHU host-control acceptance gate with a real host media-card
pause/resume transition. The current torrent record is
`reports/PHASE_8G_LOCAL_ONLY_TORRENT_PLAYBACK_REPORT.md`; the final DHU record
is `reports/PHASE_8H_DHU_HOST_CONTROL_REPORT.md`. The observed upstream Player
does not advertise a multi-audio selector, and the observed episode session did
not advertise `nexttrack`; neither is a Web-only FermataX capability to add or
claim without an upstream-advertised surface.
