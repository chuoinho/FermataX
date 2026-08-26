# FermataX Stremio P2P Reliability Technical Plan

> Status: HISTORICAL. Native P2P/torrent work is not part of the current Stremio Web-only model;
> see [`web-only/README.md`](web-only/README.md).

Status: implementation complete; DHU/device acceptance pending
Scope owner: `modules/stremio/.../torrent`

## Objective

Make P2P playback bounded, cancellable and deterministic without changing FermataX navigation,
playerbar, Back, Continue Watching or another addon's behavior. Direct HTTP/HLS/DASH playback must
remain independent from this transport.

## Runtime Boundary

P2P owns only:

```text
files/stremio/torrents/<32-character-cache-key>/
```

It must never delete the Stremio database, provider configuration, favorites, progress, subtitles,
catalog cache, or data owned by TV, Radio, YouTube and other addons.

The runtime path is:

```text
StremioDirectPlayableItem
  -> StremioItemGatewayAdapter
  -> StremioTorrentEngine
  -> TorrentHttpServer (127.0.0.1 only)
  -> VLC or ExoPlayer
```

## Ownership Contract

One playback request owns one `PreparedTorrent`. Its identity is `infoHash:fileIndex`; UI and
MediaSession ownership remain guarded by the existing playback request revision/generation.

`PreparedTorrent` owns:

- one jlibtorrent handle;
- one loopback HTTP entry and its read sessions;
- one progress observer;
- one cache directory reference.

Closing or replacing the `RemotePlaybackRequest` must release those resources exactly once. A late
native completion after cancellation must release itself and may not attach to a new engine.

## Progress State Machine

```text
RESOLVING -> FINDING_PEERS -> BUFFERING -> READY -> STREAMING
                                      STREAMING <-> REBUFFERING
any non-terminal state -> FAILED
any state -> released without presenting an error when user initiated
```

Rules:

- `READY` is the only state that carries 100 percent.
- `STREAMING` and `REBUFFERING` carry no percentage.
- `FAILED` is terminal for that HTTP entry.
- Callbacks from a stale engine/source/revision are ignored by core MediaSession ownership checks.

## Cache Policy

Defaults:

| Policy | Value |
| --- | ---: |
| Successful cache TTL | 72 hours |
| Hard retained-data quota | 4 GiB |
| Minimum free-space reserve | 512 MiB |
| Native active downloads | 2 |

Immediate failure cleanup removes a newly created failed cache directory. If a valid directory
existed before the attempt, only owned temporary files are removed so prior resume data is not
destroyed.

Maintenance cleanup runs off the main thread at warm-up, before preparation and after release. It:

1. snapshots active and preparing cache paths;
2. rejects paths outside the dedicated root;
3. ignores symlink targets;
4. removes expired unprotected entries;
5. removes oldest unprotected entries until quota/free-space requirements pass;
6. leaves active, pending and recently used entries untouched.

Cleanup interrupted by process death is retried during the next runtime warm-up.

## Cancellation and Switching

- A new selection cancels an older pending preparation.
- Cancellation is checked before and after native metadata retrieval and before handle activation.
- Native work that ignores thread interruption is still fenced by its cancellation token.
- If cancelled native work completes late, the resulting handle and HTTP entry are released instead
  of being returned to the player.
- Stop, Back and engine replacement close the same `RemotePlaybackRequest`; they do not implement a
  second P2P-specific navigation path.

## Failure Contract

Safe user-facing failures are:

- metadata unavailable;
- no peers;
- data timeout;
- engine unavailable;
- selected torrent file unavailable;
- insufficient free storage.

User cancellation is not an error. Transport failures prevent decoder fallback because preparing
the same unreadable loopback source in another decoder cannot repair the torrent.

## Performance Contract

- No cache scan, metadata fetch or torrent I/O on the Android main thread.
- One runtime-owned `SessionManager`; DHT is warmed once and reused.
- Same torrent identity uses single-flight preparation.
- Metadata deadline remains 20 seconds; complete prepare deadline remains 45 seconds.
- HTTP read sessions are bounded and cancellation closes their active piece windows.
- Piece priority follows the current read/seek window and resets old deadlines.

## Verification Gates

Automated:

- path ownership and symlink safety;
- TTL, quota, free-space and protected-entry cleanup;
- failed-new versus failed-existing cleanup;
- idempotent request release;
- terminal progress behavior;
- Range parsing, buffer scoring and cancellation;
- 10 deterministic A/B/C switches with zero stale ownership.

Device/DHU:

- play a high-peer torrent and observe peer/rate/buffer state;
- switch A -> B while A is resolving and while A is buffering;
- Back/Stop during a Range read;
- retry no-peer and timeout sources;
- restart after process death with partial cache;
- verify TV, Radio, YouTube and direct Stremio playback regressions remain absent.

## Rollback Boundary

All new storage behavior stays behind `StremioTorrentEngine`. Rolling back the P2P changes must not
require a database migration or alter direct Stremio streams. Cache directories remain disposable
and contain no provider credentials.
