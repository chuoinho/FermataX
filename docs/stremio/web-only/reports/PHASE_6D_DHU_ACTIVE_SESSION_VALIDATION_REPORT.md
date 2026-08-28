# Phase 6D: DHU Active-Session Validation

## Result

**BLOCKED_PREEXISTING_ADDON_SET.** This report supersedes neither the historical
6D `BLOCKED_NO_ADVERTISED_HOST_ACTION` baseline nor any earlier playback
evidence. It records the later active-session preflight boundary only.

## Preflight Evidence

- Worktree was clean before Phase 6D activity.
- Physical device `15c36230` was connected.
- No TCP 7000 listener, no TCP 7000 reverse rule and no validation fixture
  process was present. The only existing ADB forwards were pre-existing and
  were not changed.
- The Android SDK contains DHU. It was not started because fixture installation
  was blocked before an advertised hosted playback action could exist.
- The visible Stremio Installed list did not match the protected six-addon
  baseline. It contained the required six entries plus a pre-existing legacy
  OpenSubtitles addon alongside `OpenSubtitles v3`.

The approved fixture procedure permits exactly one temporary addon only after
the protected baseline is confirmed. Installing it into this changed state
would violate that account-protection gate. No fixture was installed, no
existing addon was altered, and no Stremio setting was changed.

## Cleanup And Audit

- Temporary UI screenshots were deleted immediately after the observation.
- No fixture/server/DHU process, validation port, ADB reverse/forward, playback
  state, account data, cookie, credential or storage data was created or read.
- Production LOC: `0`.
- Test LOC: `0`.
- Repository change: this report only.

## Decision Required

The next action would change account state and therefore requires an explicit
choice from the user:

1. **Remove the pre-existing legacy OpenSubtitles addon through the visible
   Stremio UI**, restoring the documented six-addon baseline. This is the
   recommended path only if the extra addon is unintended.
2. **Preserve the current addon set and approve it as the new protected
   baseline** for validation. This avoids removing user state, but changes the
   existing six-addon cleanup contract and must be explicitly accepted.

No Phase 6D/6E/6F fixture, server or DHU test will start until one option is
selected. Phase 6G configuration is also deferred: it shares the same
single-temporary-addon account-protection rule.
