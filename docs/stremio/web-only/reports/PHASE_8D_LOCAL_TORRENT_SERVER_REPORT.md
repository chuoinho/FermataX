# Phase 8D: Local Torrent/Server Decision

## Result

**BLOCKED_NO_REPRODUCIBLE_SERVER_BACKED_TORRENT_FIXTURE.** Direct HTTP/HLS is
not re-labeled as torrent evidence.

The available local stream-server binary matches its recorded SHA-256
`90B7A14B282A9E649FBA5ADB6112C2C191DBA884EC762F8650F45CCF06E17E09`.
Running it with `--help` did not produce an observable configured endpoint or
usable server flow; it was stopped immediately without a listener. Local
WebTorrent/tracker packages alone do not form the required server-backed
Stremio path. No user server setting, account data, firewall, public tracker,
DHT, native engine, Core or FermataX production code was changed.

P8D can pass only with an explicitly approved, reproducible local streaming
server accepting a real self-owned torrent/infohash response and delivering its
bytes to the hosted Player. FermataX must not add its own torrent runtime or
player to close this external transport gap.

Production LOC: `0`.

Test LOC: `0`.
