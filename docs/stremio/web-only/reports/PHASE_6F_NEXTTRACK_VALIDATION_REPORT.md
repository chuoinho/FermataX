# Phase 6F: Conditional `nexttrack` Validation

## Result

**NOT OBSERVED.** The temporary series fixture rendered two episodes through
visible Stremio UI, but it did not produce an active hosted Player/MediaSession
from which the upstream `nexttrack` advertisement could be judged. No next
input was dispatched.

## Fixture And Physical Flow

- Physical device: `15c36230`.
- The prospective seven-addon baseline was present before the phase.
- One local fixture, `Fermata Nexttrack Validation`, exposed a Series catalog,
  one Season 1 item and exactly two visible episodes through normal Addons and
  Discover UI.
- The only phase-created device exposure was `adb reverse tcp:7002 tcp:7002`.

The selected S1E1 route initially made a stream request that the first fixture
revision did not decode correctly. The fixture was corrected locally to decode
the percent-encoded episode route, without changing FermataX production or test
code. The hosted episode selector remained on the prior failed stream state and
did not issue a fresh stream request after that correction. It consequently did
not enter an active Player route.

`dumpsys media_session` before the phase already exposed non-active action bits
from the Fermata session while its state was `NONE`. Those bits are not evidence
that Stremio advertised `nexttrack` for this series. Treating them as an active
episode-level next action would be incorrect. No ADB media-next command, UI
script, Core dispatch or JavaScript call was sent.

The required classification is therefore `NOT OBSERVED`, rather than PASS,
FAIL, or `CONDITIONAL_NOT_ADVERTISED`. A conditional verdict requires an active
episode session whose upstream action registration is physically observable.

## Cleanup And Audit

- The fixture was uninstalled through visible Addons UI; the seven-addon
  prospective baseline was restored.
- The TCP 7002 reverse and fixture process were stopped. Host TCP 7002 had no
  listener and device loopback returned `connection refused` afterward.
- Pre-existing forwards and all existing addons were left unchanged.
- Phase captures were removed from the worktree. The fixture directory is inert
  in system Temp because the available command safety policy did not permit
  deletion; it has no live process, port, reverse rule or installed-addon
  reference.
- No account identity, token, cookie, credential or storage data was read.

Production LOC: `0`.

Test LOC: `0`.

Documentation LOC: this report only.

## Decision Checkpoint

A future rerun must begin with a fixture that produces a fresh active Player
request for S1E1. Only if the physical active session then advertises
`nexttrack` may one real next input be sent. Direct MP4 evidence and inactive
session action masks do not substitute for that gate.
