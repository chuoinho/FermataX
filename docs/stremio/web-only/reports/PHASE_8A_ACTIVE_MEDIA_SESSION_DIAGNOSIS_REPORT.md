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
