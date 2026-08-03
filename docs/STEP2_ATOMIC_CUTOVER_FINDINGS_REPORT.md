# Step 2 findings — atomic `playPreparedItem()` engine cutover

## Scope and evidence

This is an audit of the current worktree, not an implementation report. The
cutover code is currently present in `MediaSessionCallback` and
`PlaybackEngineLeaseController`; no source file was changed for this report.
Line references below are to the current files at audit time.

## Requirement-by-requirement verdict

| # | Verdict | Evidence and finding |
|---|---|---|
| 1 | MATCH | `PlaybackEngineLeaseController.mapDisposition()` maps `PREEXISTING`, `BORROWED`, `OWNED_NEW`, and `NO_CANDIDATE` to the lease dispositions at lines 136–143. `MediaSessionCallback` obtains `EngineSelection` and delegates through `select()` at lines 2019–2021. |
| 2 | MATCH | The shared `engine` slot is assigned only at controller line 85, after `canInstall()` and exact-token `bindEngine()` checks at lines 65–80. Candidate creation/selection uses local values; the callback does not write the slot before acceptance checks. |
| 3 | MATCH | `canInstall()` calls `ownsCapturedRequest()` before mutation (lease lines 166–176). That check covers terminal state, request revision, captured engine slot, captured `StateToken`, and the captured pending/active owner (lines 262–269); transport additionally requires the captured active owner and original slot (lines 171–175). |
| 4 | MATCH | Rejected disposal is limited to a lease-owned new candidate, not the captured slot, not the live slot, and not an ownership reference (lease lines 248–255), and closes only through `disposeRejected()` (controller lines 129–133). Audio-focus failure detaches the slot in `tryClaimFailure()` before publishing failure (controller lines 114–119); `candidate.close()` occurs only after `publishPlaybackFailure()` succeeds (callback lines 2063–2069). |
| 5 | MATCH | Position mutation occurs after `Accepted` is obtained. `candidate.setPosition(pos)` is guarded by `isCurrent(accepted)` immediately before and after the call (callback lines 2032–2043). |
| 6 | MATCH | Regular mode uses `ownership.bindEngine(captured.preCreationOwner(), candidate)` (controller lines 73–80), the exact-token overload. Transport mode does not bind a new engine; `PlaybackEngineLease.accept()` validates the retained active owner and original slot (lease lines 196–203). No unchecked transport bypass is present. |
| 7 | MATCH | Unsupported selection follows `tryClaimUnsupported()` → `FailureClaim` → `publishPlaybackFailure()` at callback lines 2023–2029. The controller validates the captured request and state before detaching the slot (controller lines 100–110). |
| 8 | MATCH | Audio focus is requested only after an `Accepted` and a final current check (callback lines 2057–2061), then the lease is revalidated after a granted result (line 2071). Failure uses `tryClaimFailure()` and the common failure publisher (lines 2063–2069). |
| 9 | MATCH | There is one `publishPlaybackFailure(FailureClaim, PlaybackStateCompat)` at callback lines 2009–2012. `rollbackFailure()` performs exact-token rollback and forwards `RollbackResult.restoredRevision()` to the live revision (controller lines 121–127); the callback then cancels the matching transition, publishes the error, and consumes the claim (callback lines 2010–2012). A failed rollback stops before publication. |
| 10 | MATCH | Video attachment uses `accepted.candidate()` consistently: current checks precede view lookup, surface clearing, attachment, and the final post-attachment check (callback lines 2050–2055). No shared `engine`/local `eng` mix remains in this sequence. |
| 11 | MATCH | `PlaybackTransition` has no engine field; it stores only item/snapshot/position state (lines 12–18 of `PlaybackTransition.java`). Accepted leases are checked immediately before and after `setPlaybackState()` (callback lines 2131–2133). Captured revision is explicitly passed to `publishPlaybackTransition()` (line 2073) and then to `publishPreparationMetadata()` (line 2134), whose async callback validates that revision (lines 2138–2151). |
| 12 | MATCH | The final prepare is `candidate.prepare(accepted.target())`, preceded by `isCurrent(accepted)` (callback lines 2073–2074). There is no shared-field engine lookup in this final operation. |
| 13 | DEVIATION (pre-existing adjacent changes) | The gatekeepers are not byte-for-byte untouched in the current worktree: `acceptsEngineCallback()` now has a terminal guard, diagnostic recording, and the `usesTokenBackedAuthority()` helper (callback lines 1857–1878 and 1897–1899); `ownsEngineState()` also uses the extracted `acceptsEngineState()` decision (lines 1889–1895). These changes are outside the cutover sequence and are covered by the earlier gatekeeper/predicate phase, but the literal “unmodified” requirement is not met. |
| 14 | MATCH | Pure decisions retain independent signatures and are delegated to `PlaybackPreparedItemDecisions` (queue action lines 2035–2049; surface predicate line 2016; queue publication lines 2076–2082; transition/metadata decisions lines 2120–2135). Step 3’s extraction is consistent with the cutover; no lease arguments were added to the pure predicates. |
| 15 | MATCH | `PlaybackEngineLeaseControllerTest.sameGenerationHandoffDuringRealPlayPreparedItemPreservesWinner()` (lines 116–155) reflectively invokes the real seven-argument private `MediaSessionCallback.playPreparedItem()` (lines 145–148), injects a real callback/controller/ownership fixture, makes `createEngineSelection()` reentrantly replace the winner, and verifies the winner remains installed while the outer candidate is closed (lines 150–155). This is not merely an isolated lease helper test. |
| 16 | NOT VERIFIED for Step 2 | The recorded GitHub CI run (`CI #6`, run 30808678424) predates the current uncommitted cutover changes; its successful Mobile/Auto/hotspot gates therefore cannot establish the full regression suite for this exact Step 2 snapshot. Local Mobile/Auto and hotspot tests have been run during prior work, but no CI run containing the current cutover commit was found. |
| 17 | DEVIATION / GAP | `PlaybackEngineLeaseControllerTest.normalNonReentrantSequencePreservesObservableEngineOperations()` (lines 37–65) verifies controller-level position/video/focus/prepare behavior, and the real-flow handoff test verifies the race path. No test compares a normal `playPreparedItem()` invocation against the pre-cutover implementation’s observable behavior. The requirement’s specific non-race equivalence evidence is therefore absent. |

## Additional requirement 18 — controller boundary

`PlaybackEngineLeaseController` is a live-state adapter and orchestration
boundary, not a second ownership store. Its `Access` interface exposes the
callback’s terminal flag, request revision, and shared engine slot (lines
12–23); its `LiveState` bridge re-reads ownership and state tokens (lines
25–42); and it owns capture/select/accept, rejection disposal, failure claims,
and rollback revision forwarding (lines 50–134).

The compare-before-install sequence is therefore split between the pure lease
(`canInstall()`/`accept()`, lease lines 166–203) and the live mutation adapter
(`tryAccept()`, controller lines 65–93). The exact bind and slot assignment are
contiguous in that method, but there is no Java synchronization/lock around
the pair. The implementation relies on the documented non-reentrant callback
boundary and throws at line 92 if the lease changes unexpectedly; it does not
provide a lock-based critical section. This is consistent with the design’s
single-thread/non-reentrant assumption, but should be recorded as an
implementation boundary rather than inferred atomicity.

## Final verdict

**Step 2 matches the cutover mechanics with two named gaps:**

1. Requirement 13’s literal “gatekeepers unmodified” condition is not true in
   the current worktree because adjacent gatekeeper/predicate extraction work
   changed those methods.
2. Requirements 16 and 17 are not fully evidenced: no real CI run contains
   this exact cutover snapshot, and no dedicated normal-call equivalence test
   compares against the pre-cutover behavior.

The engine-selection safety protocol itself (ownership mapping, compare before
install, disposal, exact-token binding, failure rollback ordering, accepted
lease guards, and real-flow handoff regression) is present and internally
consistent. Checklist closure should therefore wait only for a CI run on the
cutover commit and an explicit normal-path equivalence test, unless the project
accepts the two evidence deviations above as documented exceptions.
