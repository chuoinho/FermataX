# Phase 8F: Remaining Acceptance Evidence

## Result

This phase closed every remaining test that has a safe, reproducible physical
surface. Release readiness remains **PARTIAL** because a host-control path is
not available and the separately governed torrent transport remains blocked.

| Boundary | Result | Physical evidence |
| --- | --- | --- |
| Multi-audio selection | NOT OBSERVED | Dual-audio HLS played and rendered, but no selector was exposed. |
| Episode nexttrack | CONDITIONAL_NOT_ADVERTISED | Episode 1 Player and Episode 2 overlay were visible; native session did not advertise `nexttrack`. |
| DHU host input | PARTIAL | DHU connected through the existing transport but remained `Waiting for phone`; no projection or host input occurred. |
| Streaming-server/torrent | BLOCKED | P8D's server injects public trackers and the local seeder cannot produce transferable metadata. |

## Execution Evidence

- Device: physical Android device `15c36230`.
- Addon account state: each temporary fixture was installed and uninstalled
  exclusively through visible Stremio UI. The final installed set is the
  protected seven-addon baseline; no pre-existing addon was removed, reordered
  or configured.
- The HLS fixture advertised English and Vietnamese audio, entered the hosted
  Player, rendered video and requested master/video/audio media through the
  WebView. No audio selector was present.
- The two-episode fixture opened Episode 1 in the hosted Player, displayed a
  next-video card for Episode 2 and made a ranged MP4 request. Its native
  session had no registered `nexttrack`, so no synthetic event was sent.
- DHU connected to the already-present ADB transport on `5277`, but its host
  window stayed at `Waiting for phone`. The device did not enter an Android
  Auto projection UI, so a DHU click would not be a valid control test.
- A fresh lifecycle fixture playback showed a visible Player but a native
  MediaSession in `NONE`; this is an observed runtime variance relative to the
  earlier P8A active-claim evidence. It does not erase that observed P8A PASS,
  but it prevents a new host-control claim.

## Cleanup

- Each temporary addon was visibly uninstalled and the final UI showed exactly
  seven `Uninstall` entries and no validation-fixture name.
- The phase-owned node processes and DHU process were stopped.
- Phase-created reverse mappings for ports `7000`, `7001` and `7002` were
  removed. No listener remains on those ports.
- Existing forwards `9223` and `5277` were preserved.
- Fixture directories under local Temp could not be recursively removed because
  the execution environment rejected the deletion command. They contain no
  running process, listener, reverse mapping or installed addon reference.

## Change Audit

Production LOC: `0`.

Test LOC: `0`.

Documentation only: this report and acceptance reconciliation.

## Release Decision

Do not advertise unobserved audio selection, `nexttrack`, DHU host control or
torrent playback as release-complete functionality. The Web-only release may
be considered only with those explicit limitations retained in its release
gate; a fully unconditional release requires a functioning Android Auto host
surface and a separately approved streaming-server/torrent environment.
