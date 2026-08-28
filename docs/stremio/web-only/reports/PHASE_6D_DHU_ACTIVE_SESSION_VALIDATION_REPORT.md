# Phase 6D: DHU Active-Session Validation

## Result

**BLOCKED_NO_ACTIVE_MEDIA_SESSION.** The previously recorded account-baseline
blocker was resolved prospectively by the approved seven-addon baseline. The
physical direct-MP4 player boundary passed, but the MediaSession bridge never
reported an active `PLAYING` state. DHU was deliberately not started because
transport input would not be valid evidence without that prerequisite.

## Historical Preflight And Baseline Resolution

The original 6D preflight found the required six addons plus a pre-existing
legacy `OpenSubtitles` addon alongside `OpenSubtitles v3`. That first attempt
correctly stopped before a fixture was installed as
`BLOCKED_PREEXISTING_ADDON_SET`.

The user then selected the approved option to preserve that state. For Phases
6D through 6G, the protected prospective baseline is seven addons: Cinemeta,
YouTube, WatchHub, Public Domain Movies, OpenSubtitles v3, Local Files and the
legacy OpenSubtitles addon. This does not rewrite the documented six-addon
history and no pre-existing addon was removed, reordered or configured.

## Active-Session Evidence

- Physical device: `15c36230`.
- The Android SDK DHU binary was available.
- Before setup, TCP 7000 had no listener and no TCP 7000 reverse rule existed.
  Existing unrelated forwards were retained untouched.
- One local loopback fixture, `Fermata DHU Validation`, was installed through
  the visible Addons UI. Its only media asset was the project-owned 36-second
  H.264/AAC MP4. The phase created only `adb reverse tcp:7000 tcp:7000`.

The visible Stremio flow completed normally:

1. Discover displayed the fixture catalog and its single `Fermata DHU MP4`
   card.
2. The detail page resolved the `Direct MP4` stream through normal hosted UI.
3. The hosted Player route opened, video rendered, and the Player visibly
   displayed `Pause`.
4. The safe fixture log recorded the media request with HTTP Range and status
   `206`.

This proves hosted playback only. No stream URL, account identity, cookie,
credential or storage data is retained in this report.

The mandatory native active-session gate did not pass. Immediately after
playback, and again after a 15-second wait, `dumpsys media_session` identified
the FermataX session but reported `PlaybackState {state=NONE(0), position=0,
...}`. The session advertised actions, but it never made a `PLAYING` claim.
The Auto media service was bound yet did not expose a usable active playback
state. Accordingly, the phase did not start DHU or inject host transport input.

This is **not** `HOST_INPUT_NOT_DELIVERED`, `HOST_UI_NOT_EXPOSED` or
`RECONNECT_STATE_FAILURE`: those classifications require an active claimed
session first. The unresolved boundary is the hosted-player-to-MediaSession
bridge.

## Cleanup And Audit

- Playback was stopped through the visible Player UI.
- The fixture was uninstalled through visible Addons UI and the seven-addon
  prospective baseline was restored.
- The phase-owned reverse was removed. Host TCP 7000 had no listener and the
  device loopback probe returned `connection refused` afterward.
- The fixture process was stopped. DHU was never started. Pre-existing forwards
  were unchanged.
- Phase-owned captures were removed from the worktree. The temporary fixture
  directory is inert in system Temp because the available command safety policy
  did not permit deletion; it has no running process, port, reverse rule or
  installed-addon reference.
- No account data, cookie, credential or storage data was read.

Production LOC: `0`.

Test LOC: `0`.

Documentation LOC: this report only.

## Follow-up Gate

Before a DHU control verdict can be obtained, perform a minimal diagnosis of
why the native MediaSession compatibility bridge remains `NONE` while the
hosted Player is visibly playing. Do not rerun DHU controls until a physical
device shows a matching active `PLAYING` claim and advertised action state.
