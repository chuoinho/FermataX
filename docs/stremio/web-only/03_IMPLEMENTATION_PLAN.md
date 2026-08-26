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
