# Phase 8G: Local-Only Torrent Playback Acceptance

## Result

**PASS for the user-configured streaming-server/torrent boundary.** The hosted
Stremio Player rendered a self-owned MP4 delivered by a temporary process-local
streaming server after a visible Stremio stream selection returned a torrent
`infoHash` and file index. This does not introduce a torrent engine, player or
stream URL handling into FermataX.

## Safety Boundary

- Physical device: `15c36230`.
- The media fixture was generated locally and self-owned.
- Tracker, seeder, validation server and addon listened only on loopback.
- DHT, UPnP, public trackers, public listeners, persistence and upload in the
  validation downloader were disabled. The server used an explicit loopback
  peer and a locally generated, non-private torrent so the BitTorrent metadata
  extension could be transferred.
- FermataX production code, tests, `aauto.aar`, account APIs, Core dispatch,
  DOM scripts, token/cookie storage and stream URL extraction were untouched.

## Observed Physical Flow

1. The temporary addon manifest was installed through Stremio's visible Addons
   UI. Its one movie catalog and one stream were selected through the hosted UI.
2. The hosted route entered Player and rendered the generated video. The visual
   test pattern advanced, including after a second open near the end of the
   short media.
3. Server logs recorded the torrent metadata exchange, verified local pieces,
   a direct initial file reader, and ranged readers at both a near-end offset
   and offset zero. Independent preflight observed `HEAD 200` and a bounded
   byte-range `GET 206` with exactly the requested byte count.
4. This proves the chain `Stremio visible stream selection -> configured
   streaming server -> local torrent transport -> hosted HTML5 Player`. It is
   not direct HTTP/HLS evidence relabeled as torrent evidence.

## Fixture And Account Cleanup

- The addon was uninstalled through the visible Stremio UI. Its cached detail
  page showed `Install addons`, confirming it was no longer installed.
- The phase-created `adb reverse` rules for the validation server and addon
  were removed.
- Tracker, seeder, addon server and validation server were stopped. No listener
  remained on the four phase ports.
- The Stremio Streaming settings page retained its loopback entry but showed it
  in an error state after shutdown. It had no visible UI delete action; no
  storage, profile or Core state was changed to force its removal.
- This execution environment rejected recursive deletion of the three exact
  temporary directories after their processes stopped. They are inert and do
  not contain a running listener or ADB mapping.

## Scope And Remaining Gaps

- This gate validates initial playback and reopen/resume behavior only. It does
  not claim torrent subtitles, multiple audio tracks, multi-file selection,
  server restart recovery or `nexttrack`.
- The separate Android Auto/DHU host-control gate remains `PARTIAL` because no
  vehicle host action has yet reached an active Stremio MediaSession.
- Hosted multi-audio selection remains `NOT OBSERVED`; `nexttrack` remains
  `CONDITIONAL_NOT_ADVERTISED` rather than a failure.

Production LOC: `0`.

Test LOC: `0`.
