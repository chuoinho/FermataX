# Phase 8B: Alternate-Audio HLS Validation

## Result

**NOT OBSERVED (`FIXTURE_FLOW_NOT_EQUIVALENT`).** A local dual-audio HLS fixture
was installed with normal hosted Stremio UI, but it did not become a selectable
Discover catalog before the local-only fixture state was reloaded. The Player
made no HLS request, so no audio-selector claim is made.

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

## Decision

Do not add a FermataX audio selector. A future retry needs a fixture-install
route whose catalog remains selectable within the active hosted WebView session.
