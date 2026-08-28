# Phase 8A: Active-Playback MediaSession Diagnosis

## Result

**BLOCKED_PREEXISTING_ADDON_SET.** No reproduction, fixture installation,
production change, test change, or MediaSession remediation was started.

The Phase 8 instruction defines a protected baseline of six addons:
`Cinemeta`, `YouTube`, `WatchHub`, `Public Domain Movies`, `OpenSubtitles v3`,
and `Local Files`. The normal hosted Addons UI on physical device `15c36230`
instead displayed those entries plus a separate legacy `OpenSubtitles` addon.
This is a pre-existing account-state difference, not a Phase 8 fixture. Removing
it would change user state and is outside the granted cleanup authority.

Production LOC: `0`.

Test LOC: `0`.

Documentation LOC: this report only.

## Preflight Evidence

- Worktree: `codex/stremio-web-only` at `854e4e37` before this report; clean.
- Device `15c36230` was connected. Its installed FermataX package reports
  version `2.0.1`, version code `304`, last updated from the signed Web-only
  release smoke build.
- The active build still uses the current `StremioWebMediaSessionBridge`,
  `MediaSessionCallback` control-only ownership path, action allow-list and
  fragment release-on-switch flow. There is no source difference in those
  files from the Phase 1C bridge fix commit `6136d195` through the Phase 7
  documentation-only HEAD.
- Phase 1C and Phase 6D disagree only at runtime: the former observed a
  `PLAYING` claim after a direct fixture while the latter observed direct Range
  playback with `NONE`. The required causal reproduction was not run because
  the protected addon baseline failed first.
- No ADB reverse rule was present. Existing forwards `9223` and `5277` were
  observed but not created, modified or removed by this phase.

## Stale Test Transport Cleanup

Preflight found a prior test artifact process at
`C:\Users\ttanh\AppData\Local\Temp\fermatax-phase0b-stream-server`, launched
with `--help` and listening on `42000` and `11470`. It was not a user-configured
Stremio endpoint and no fixture referred to it. The process was stopped; its
listeners were then absent. No validation port `7000`, `7001` or `7002`, reverse
rule, fixture process, tracker, seeder or webseed was active.

No account data, cookies, tokens, credentials, local storage, stream URL or
media-source content was read. The visible Addons UI was only scrolled to
observe the installed cards.

## Cleanup State

- No fixture addon was installed.
- No server, tracker, seeder, webseed, validation forward or reverse rule was
  created.
- The stale Phase 0B helper was stopped and its listeners are closed.
- The existing account state was not modified.
- The worktree contains this documentation-only report pending its dedicated
  local commit.

## Required Decision

Do not continue to the controlled MediaSession reproduction, Phase 8B, 8C, 8D
or 8E until the installed-addon baseline is explicitly resolved.

**Option A - preserve seven addons (recommended):** treat the observed legacy
`OpenSubtitles` as protected pre-existing state, update the prospective baseline
for Phase 8 only, and continue without modifying any user addon.

**Option B - restore the requested six-addon baseline:** use the visible
Stremio Addons UI to uninstall only the legacy `OpenSubtitles` addon, verify the
remaining six entries, then resume Phase 8A. This deliberately changes account
state and therefore requires explicit approval.

## Continuation - Option A Accepted And Reproduction Completed

The user explicitly selected **Option A** on 2026-08-28. The legacy
`OpenSubtitles` addon therefore remained protected; the Phase 8 baseline is the
following seven pre-existing addons, in their existing order:

1. `Cinemeta`
2. `YouTube`
3. `WatchHub`
4. `Public Domain Movies`
5. `OpenSubtitles v3`
6. `Local Files`
7. `Legacy OpenSubtitles`

This continuation did not remove, configure or reorder any of them. It used one
visible-UI, local direct-MP4 fixture named `Fermata Lifecycle Validation`, then
removed that fixture through the visible Stremio Addons UI before cleanup.

## Physical Reproduction Evidence

**Result: `PASS_CONTROL_RESTORED`.** The first causal boundary is restored:
the hosted Player emitted an active control claim and FermataX applied it to its
native MediaSession.

- Physical device: `15c36230` (Redmi Note 8), FermataX `2.0.1` (version code
  `304`).
- The fixture catalog, detail and stream were selected exclusively through the
  visible hosted Stremio UI. No Core dispatch, DOM automation, storage access,
  cookie/token access, or stream URL extraction was used.
- The hosted route entered `#/player/...`; the report intentionally redacts the
  route payload and media address.
- The fixture recorded the expected safe request sequence: metadata and stream
  discovery, then media `HEAD 200` and video `GET 206`.
- The hosted Player displayed `Pause`, and its visible time advanced from the
  initial load to `00:00:38` (then `00:00:53` before the visible pause action).
- `dumpsys media_session` observed the Fermata media session as `active=true`,
  `PLAYING`, action mask `518` (`play`, `pause`, and `play/pause`), with metadata
  description `Fermata Lifecycle MP4`.
- The normal visible pause action changed the session to `PAUSED` while retaining
  the same metadata and action mask. The normal FermataX Back action returned to
  the hosted detail route, after which the session became inactive with state
  `NONE`, no actions and no metadata. The fixture recorded its ranged video
  request as closed.

This is direct physical evidence that supersedes the *active-claim absent* part
of the Phase 6D observation. It does **not** prove Android Auto/DHU host-button
delivery: no DHU or vehicle control was used in this continuation. The Phase 1C
and Phase 6D disagreement is therefore classified as an observed runtime/control
variance, not a source-code regression.

## Account And Runtime Cleanup

- `Fermata Lifecycle Validation` was uninstalled through the visible Addons UI.
  A fresh visible-UI traversal confirmed the seven protected addons above and
  confirmed that the fixture was absent.
- The Phase-created `adb reverse tcp:7000` mapping was removed.
- The local fixture server (PID `38888`) and temporary logcat collector (PID
  `21360`) were stopped. No listener remains on port `7000`.
- Existing unrelated ADB forwards `tcp:9223` and `tcp:5277` remained present and
  untouched. No reverse mapping remains.
- The exact fixture directories under local Temp were identified after process
  shutdown. The environment rejected their deletion by policy, so they remain
  inert only: no server process, listener, reverse rule, installed fixture or
  account-state change remains. No broad Temp deletion was attempted.

## Scope And Next Gate

Production LOC: `0`.

Test LOC: `0`.

Documentation LOC: this continuation only.

Phase 8A is complete. Phase 8B may proceed independently with one alternate-
audio HLS fixture and the same visible-UI/account-cleanup contract. Phase 8C
must test `nexttrack` only if an active episode session actually advertises it;
Phase 8D remains separately conditional on an approved, fully local
self-owned server-backed fixture.
