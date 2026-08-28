# Phase 8B: Alternate-Audio HLS Validation

## Result

**NOT OBSERVED (`SELECTOR_ABSENT`).** The initial local-fixture retry did not
reach Player. The later physical continuation did reach the hosted Player and
proved dual-audio HLS playback, but Stremio Web did not render an audio-track
selector on this device. A selection therefore could not be performed or
claimed.

## Evidence And Cleanup

- Device `15c36230` retained the protected seven-addon baseline before and after.
- The fixture declared `English` and `Vietnamese` HLS audio renditions and a
  video variant. Host-only preflight received HTTP 200 for its manifest and
  master playlist; the hosted origin requested the addon manifest.
- No DOM/Core dispatch, deep link, storage access, stream extraction, external
  player, native selector, or inferred selection was used.
- Restarting FermataX retained the protected account baseline but removed the
  local-only fixture state before a hosted Player route could be selected.
- Server PID `22448` was stopped, `adb reverse tcp:7001` was removed and port
  `7001` closed. Existing forwards `9223` and `5277` were unchanged.
- The exact Temp fixture directory is inert because environment policy refused
  deletion; no broad Temp cleanup was attempted.

Production LOC: `0`.

Test LOC: `0`.

## Physical Continuation: Player Reached

The continuation used the same self-owned, loopback-only HLS fixture through
the visible Addons, catalog, detail and stream UI. It did not use Core actions,
DOM automation, storage access, account APIs or an external player.

- The HLS master playlist advertised two distinct renditions: `English` and
  `Vietnamese`.
- The hosted Player opened and visibly rendered video. Fixture evidence shows
  the master playlist, video playlist, English audio playlist and media
  segments were requested successfully.
- At the observed start of playback, the FermataX MediaSession was
  `active=true` and `PLAYING`; this proves the HLS Player path was real, rather
  than a catalog-only result.
- The visible Player controls exposed no audio-language or audio-track menu.
  The Vietnamese rendition was not selected because there was no supported UI
  action with which to make that choice.
- The fixture was uninstalled through the visible Addons UI. The protected
  seven-addon baseline was then re-observed.
- The phase-created server was stopped and `adb reverse tcp:7001` was removed.
  There is no listener on port `7001`; pre-existing forwards `9223` and `5277`
  were left untouched.

This continuation supersedes the former `FIXTURE_FLOW_NOT_EQUIVALENT` reason,
but not the `NOT OBSERVED` result: the selector itself was absent.

## Decision

Do not add a FermataX-native audio selector. A future acceptance run needs an
upstream Stremio Web build/device combination that renders a selectable
audio-track control for an active multi-audio stream.
