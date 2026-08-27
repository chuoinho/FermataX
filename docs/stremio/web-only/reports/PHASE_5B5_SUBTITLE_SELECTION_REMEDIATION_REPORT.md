# Phase 5B5 Follow-up: Explicit Subtitle Selection

## Result

**F5 PASS on the physical Android device.** The hosted Stremio player exposed
the normal subtitle menu, accepted an explicit `OFF` to `English` selection,
and rendered the selected WebVTT cue during playback. With F5 and the already
validated F6 follow-up, the Phase 5B5 matrix is complete.

## Scope And Safety

- Worktree: `E:\\Chatgpt\\fermata-stremio-web-only`, branch
  `codex/stremio-web-only`.
- Device: `15c36230`, Android 16.
- Exactly one temporary addon, `Fermata Local Subtitle Validation`, was
  installed through the visible Stremio Addons UI after the pre-existing local
  validation fixture was removed through that same UI.
- The original six addons were unchanged: Cinemeta, YouTube, WatchHub, Public
  Domain Movies, OpenSubtitles v3, and Local Files.
- The fixture was a direct loopback MP4 with one English WebVTT resource. No
  torrent, account API, Core dispatch, DOM script, storage inspection, token or
  cookie access, external player, or production/test-code change was used.

## Observed Physical Flow

1. The normal Stremio Discover catalog selector showed `Fermata Subtitle
   Validation`; its single `Fermata Subtitle MP4` card opened the normal detail
   screen and then the hosted Player through its visible stream row.
2. Fixture records showed the standard subtitle resource and WebVTT retrieval
   in addition to the direct MP4 handoff. Only request method, path class,
   origin, fetch metadata, status, and lifecycle were recorded.
3. The player's visible subtitle control opened `Subtitles Languages`, with
   `OFF` and `English`, plus an English variant named `fixture.vtt`.
4. `OFF` was selected first. The menu visibly changed to `Subtitles are
   disabled` and marked `OFF` as selected.
5. `English` was then selected through the same normal menu. The menu visibly
   marked English and its variant as selected.
6. Playback was moved to the bounded cue interval. At `00:00:04`, the player
   visibly rendered `English validation subtitle` over the video.

This is explicit UI selection evidence, not an inference from VTT prefetching.

## Cleanup Evidence

- Playback was left by navigating to the normal Stremio Addons UI.
- `Fermata Local Subtitle Validation` was removed once through its visible
  `Uninstall` button; the final Installed view showed no temporary fixture and
  the six original addons remained.
- The temporary Node process was stopped, the device-specific
  `adb reverse tcp:7000` mapping was removed, and host TCP 7000 had no
  listener.
- A device loopback probe to TCP 7000 returned `Connection refused`.
- Both fixture directories and the screenshots/XML snapshots created during
  this follow-up were removed from Temp after cleanup. No unrelated user data
  was removed.

## Change Audit

- Production LOC: `0`.
- Test LOC: `0`.
- Repository change: documentation only.
- Deferred: torrent transport, `nexttrack`, renderer-loss recovery, AA/DHU
  hardware controls, HLS quality selection, audio-track switching, and
  persistent resume semantics.
