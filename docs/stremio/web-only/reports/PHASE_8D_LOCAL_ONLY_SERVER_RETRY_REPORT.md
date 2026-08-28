# Phase 8D: Local-Only Streaming Server Retry

## Result

**BLOCKED_LOCAL_ONLY_SERVER_UNSAFE_AND_FIXTURE_INCOMPATIBLE.** No physical
device playback, Player route, MediaSession, seek, resume or stale-endpoint
claim is made by this retry.

## What Was Established

- The recorded `stream-server-windows-amd64.exe` SHA-256 remains
  `90B7A14B282A9E649FBA5ADB6112C2C191DBA884EC762F8650F45CCF06E17E09`.
- The binary exposes the expected compatibility listener on port `11470`,
  including `/heartbeat`, `/settings`, `/{infoHash}/create` and
  `/{infoHash}/{fileIdx}` routes.
- A temporary self-owned fixture used the upstream stream schema:
  `infoHash`, `fileIdx` and `announce`. It used one LAN tracker and one local
  seeder, with DHT, PEX and LSD disabled in the fixture.
- Before the transfer attempt, the temporary server settings disabled DHT, PEX,
  LSD, background updates and non-loopback torrent listening. The local
  tracker observed the server client and the seeder completing the BitTorrent
  handshake.

## Why The Run Stops

Source audit of the exact server implementation shows that `add_torrent`
unconditionally appends built-in public tracker URLs after applying supplied
trackers. That behavior is incompatible with the approved local-only scope,
even when all exposed DHT, PEX and LSD settings are disabled. The attempt was
stopped immediately after that audit; no further server or device activity was
performed.

Independently, the self-owned WebTorrent seeder did not provide usable torrent
metadata to this EngineFS/libtorrent implementation. The tracker and wire
events prove peer discovery and a handshake, but the server's metadata wait
timed out and its file statistics did not become available. Supplying the same
fixture's torrent metadata directly to the server created an engine but still
did not yield observable file statistics or transferable stream bytes. This
does not prove an application defect and is not treated as server-backed media
evidence.

## Scope And Cleanup

- No temporary addon was installed, so the protected seven-addon account
  baseline was never changed.
- No `adb reverse` rule was added and no device UI, Player, Chrome or DHU test
  was started.
- The temporary server and local tracker/seeder were stopped. Ports `7002`,
  `11470`, `18080` and `42000` are closed.
- The pre-existing server settings file was restored byte-for-byte by moving
  its untouched original aside before the test and moving it back after stop.
- Existing ADB forwards `9223` and `5277` were not modified.
- Temporary runtime directories remain inert where environment deletion policy
  rejects recursive cleanup; no runtime is left listening.

## Decision

P8D remains **BLOCKED**. A further retry requires a separately approved server
build whose torrent backend can be configured to use only the supplied local
tracker, plus a conventional compatible local seeder. It must repeat server
preflight before any device UI or addon installation. FermataX must not gain a
native torrent engine, stream URL extraction or a native player to work around
this external transport boundary.

Production LOC: `0`.

Test LOC: `0`.
