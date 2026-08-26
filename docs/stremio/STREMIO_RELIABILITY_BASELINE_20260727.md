# Stremio Reliability Baseline

> Status: HISTORICAL BASELINE for the retired native Stremio implementation. The current
> implementation authority is [`web-only/README.md`](web-only/README.md).

Captured: 2026-07-27

This baseline starts the reliability program in
`STREMIO_RELIABILITY_IMPLEMENTATION_GUIDE.md`. It supersedes the older refactor-completion report
as evidence for new reliability work. The older report remains valid only for the
behavior-preserving extraction it actually verified.

## Workspace identity

- Workspace: `E:\Chatgpt\fermata`
- Branch: `main`
- HEAD: `8ad7a27ea409f634040915548155208068c5facf`
- Worktree paths changed/untracked before this reliability implementation: 178
- `modules/stremio` and `docs/stremio` are currently untracked as a whole.
- No existing dirty path may be reverted or reformatted outside an explicitly owned phase.
- Implementation-guide SHA-256:
  `C26DDC7483DE9884A9D2E3B7C069A64570581AEB54D76A1813B7EC8EEC7BB236`

## Pinned reference evidence

| Reference | Commit |
| --- | --- |
| Stremio Core | `eeb89ff8c7f401b50c435933dab399daa956dc35` |
| Harbor | `cfdafb95528315a8bd37997abbfbed9ff27dab35` |
| jlibtorrent | `169b7a8f09ba99a683536a77de1978cc014e6b09` |
| Stremio Web | `daf74b0ec973054c94de9f0f8271b3234bd26c43` |
| stremio-addon-client | `7c66830cfc1a8e749373d9df0bb105c7dad33bfd` |
| NuvioMobile | `b1c9d08435a5b7d7487b30bbf181cb48830c2458` |

All six detached revisions were resolved locally under
`C:\Users\ttanh\AppData\Local\Temp\fermata-stremio-research`. License and permitted-use rules are
recorded in `REFERENCES.md` and the implementation guide.

## Current automated baseline

Command:

```powershell
.\gradlew.bat :stremio:testAutoDebugUnitTest :stremio:testMobileDebugUnitTest --no-daemon
```

Result:

| Variant | Test classes | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Auto Debug | 88 | 468 | 0 | 0 | 0 |
| Mobile Debug | 88 | 468 | 0 | 0 | 0 |

Gradle reported `BUILD SUCCESSFUL`. The tasks were up-to-date, but the result XML files were
inspected directly to obtain the counts above.

## Proven current contracts

The current suites provide direct evidence for:

- source add/edit/remove transactions and stale snapshot rejection;
- bounded HTTP/cache, DNS/redirect/SSRF and sensitive-header policies;
- source-scoped identity and transport redaction;
- catalog/meta parsing, Discover pagination and route-cache eviction;
- provider failure isolation and deterministic ranking in the current stream aggregator;
- cancellation and late-result rejection in several source, browse and protocol paths;
- stable subtitle candidate identity, UTF BOM/archive normalization and source invalidation;
- session ownership, process-restore identity and 100 deterministic/randomized A/B/C transitions;
- loopback HTTP range parsing, adaptive requested-range window and requested-byte progress;
- P2P failure mapping and terminal failure state;
- Back/navigation/UI resource contracts already covered by FermataX tests.

These tests prove their named contracts only. They do not prove live provider compatibility,
first-frame rendering, live torrent readiness, subtitle rendering on a real engine, or DHU behavior.

## Reliability gap matrix

| Guide requirement | Current evidence | Baseline decision |
| --- | --- | --- |
| Owned operation scope | `RequestGeneration` and `StremioCall` exist, but ownership is local and inconsistent | Incomplete; Phase 1 |
| Typed failure/recovery | HTTP and playback have partial typed errors; many state paths catch and ignore exceptions | Incomplete; Phase 1 |
| Explicit provider load state | UI/presentation uses multiple booleans and result shapes | Missing; Phase 2 |
| Multi-ID request routing | `StreamAggregationRequest` carries one content/video ID | Missing; Phase 2 |
| Incremental provider snapshots | `StreamAggregator` publishes an interactive snapshot and one final batch | Incomplete; Phase 2 |
| PlaybackAttempt owner | No attempt state machine or single playback resource owner exists | Missing; Phase 3 |
| First-frame evidence | `MediaEngine.Listener` has no first-frame callback | Missing; Phase 3 |
| Bounded decoder fallback per attempt | Core engine fallback exists but is not bound to a Stremio attempt state | Incomplete; Phase 3 |
| Alert-driven torrent wait | `TorrentStreamLease` sleeps 150 ms; engine handle lookup sleeps 100 ms | Contradicted; Phase 4 |
| Readiness before player handoff | Loopback range waits after player creation | Contradicted; Phase 4 |
| Truthful P2P progress domains | Requested-byte progress exists, but preparation/readiness/whole completion ownership is not one model | Incomplete; Phase 4 |
| One subtitle session | Aggregator, bridge, playback resource, selection store and engine share ownership | Contradicted; Phase 5 |
| Format support consistency | ASS/SSA/TTML are marked supported but are not engine-readable | Contradicted; Phase 5 |
| Finite page state | Several deadlines exist, but no single finite state model proves every spinner terminates | Incomplete; Phase 6 |
| Isolated config process | Config WebView policy exists in the addon process | Missing; Phase 6 |
| Full fault-injection matrix | Narrow cancellation/timeout tests exist | Incomplete; Phase 7 |
| Live Mobile/DHU/P2P acceptance | Old reports explicitly list this as pending | Missing; Phase 7 |

## Known-defect regression ownership

Observed defects are assigned to architectural tests rather than one-off fixes:

- old film/frame/title survives a stream switch: `PlaybackAttemptSupervisor` transition tests;
- P2P black screen and unexplained Back: readiness/first-frame/failure-action tests;
- misleading 100% or regressing percentage: separated P2P progress-domain tests;
- subtitle list delays and selected subtitle does not render: `StremioSubtitleSession` partial,
  materialization, parsing and current-engine attachment tests;
- infinite spinner/provider stall: finite provider/page state deadline tests;
- external catalog/stream mismatch: multi-ID and manifest capability contract tests;
- state leak to another addon: cross-addon ownership tests in Phase 7.

## Phase write boundaries

1. Phase 1 initially owns only `modules/stremio/.../lifecycle`, a new `failure` package and explicit
   adapter call sites. It must not change UI resources or shared Fermata engine behavior.
2. Phase 2 owns protocol planning, identity and stream/provider aggregation.
3. Shared `MediaEngine` changes are reserved for Phase 3 and must be optional default callbacks.
4. Torrent classes are not behaviorally modified before Phase 4.
5. Subtitle classes are not consolidated before Phase 5.
6. Config process/manifest and page UI state changes are reserved for Phase 6.

## Phase 0 conclusion

The current code is a clean test baseline but does not satisfy the reliability goal. Phase 1 may
begin. No production behavior was changed while creating this baseline.
