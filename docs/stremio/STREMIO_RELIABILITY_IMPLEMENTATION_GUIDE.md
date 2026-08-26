# FermataX Stremio Reliability Implementation Guide

> Status: SUPERSEDED. This native-addon implementation guide is historical reference only. The
> current normative plan is [`web-only/README.md`](web-only/README.md).

Status: implementation specification

This document is the handoff contract for an implementation agent. The objective is not to fix a
finite list of observed bugs. The objective is to make the Stremio addon complete, deterministic,
cancellable, diagnosable, and isolated from every other FermataX addon.

Known bugs are regression fixtures. They do not define the implementation boundary.

## 1. Non-negotiable product contracts

The implementation must preserve all of the following:

- Native FermataX UI. Do not embed Stremio Web.
- Existing Android Auto navigation, Back, playerbar, top bar, Dashboard and SmartTopCard rules.
- No Stremio split view.
- No Stremio YouTube/`ytId` playback.
- Existing MediaSession ownership and background-audio behavior.
- TV, Radio, Web, YouTube, Podcast and Audiobook behavior.
- The current addon package, registration metadata, stable IDs and persistence schema unless a
  separately reviewed migration is supplied.
- Removing or disabling Stremio must not affect any other addon.

Do not replace jlibtorrent, ExoPlayer, VLC, FermataX `FutureSupplier`, or the complete Stremio
runtime in one rewrite. Extract contracts, characterize existing behavior, then replace one owner
at a time.

## 2. Source acquisition and provenance

Use detached, pinned revisions. Never code against a moving branch.

```powershell
$ref = "$env:LOCALAPPDATA\Temp\fermata-stremio-research"
New-Item -ItemType Directory -Force $ref | Out-Null

function Get-PinnedRepo($name, $url, $commit) {
  $path = Join-Path $ref $name
  if (!(Test-Path (Join-Path $path '.git'))) {
    git clone --filter=blob:none --no-checkout $url $path
  }
  git -C $path fetch --depth=1 origin $commit
  git -C $path checkout --detach $commit
  if ((git -C $path rev-parse HEAD).Trim() -ne $commit) {
    throw "Pinned revision mismatch: $name"
  }
}

Get-PinnedRepo 'stremio-core' `
  'https://github.com/Stremio/stremio-core.git' `
  'eeb89ff8c7f401b50c435933dab399daa956dc35'

Get-PinnedRepo 'harbor' `
  'https://github.com/harborstremio/harbor.git' `
  'cfdafb95528315a8bd37997abbfbed9ff27dab35'

Get-PinnedRepo 'jlibtorrent' `
  'https://github.com/frostwire/frostwire-jlibtorrent.git' `
  '169b7a8f09ba99a683536a77de1978cc014e6b09'

Get-PinnedRepo 'stremio-web' `
  'https://github.com/Stremio/stremio-web.git' `
  'daf74b0ec973054c94de9f0f8271b3234bd26c43'

Get-PinnedRepo 'stremio-addon-client' `
  'https://github.com/Stremio/stremio-addon-client.git' `
  '7c66830cfc1a8e749373d9df0bb105c7dad33bfd'

Get-PinnedRepo 'nuviomobile' `
  'https://github.com/NuvioMedia/NuvioMobile.git' `
  'b1c9d08435a5b7d7487b30bbf181cb48830c2458'
```

Before adapting anything, record this output in the implementation review:

```powershell
git -C <reference-path> rev-parse HEAD
git -C <reference-path> remote get-url origin
Get-Content <reference-path>\LICENSE* -TotalCount 30
```

### 2.1 Copy/port policy

| Source | License at pinned revision | Allowed use |
| --- | --- | --- |
| Stremio Core | MIT | Port state/request semantics and tests to Java; preserve notice |
| Harbor | MIT | Port bounded algorithms and tests; preserve notice |
| jlibtorrent | MIT | Use the published Java API; do not vendor generated bindings |
| stremio-addon-client | MIT | Port protocol matching and request construction |
| NuvioMobile | GPL-3.0 | Port only bounded compatible behavior; preserve attribution |
| Stremio Web | GPL-2.0 | Behavior/UI reference only; do not copy source into FermataX |
| Fermata Xtream APK/Web extraction | Unknown/mixed | Observable behavior only; never copy decompiled code or assets |

For every copied or translated MIT/GPL-3 source block:

1. Add the upstream repository, commit and exact source path to the implementing commit message.
2. Add or update `THIRD_PARTY_NOTICES.md`.
3. Preserve upstream copyright and license text for substantial copied portions.
4. Rewrite integration boundaries for FermataX rather than copying framework/runtime code.
5. Create a local contract test before modifying the adapted algorithm.

Example file header for a substantial adaptation:

```java
/*
 * Behavior adapted from <repository>/<path> at <commit>.
 * Original work licensed under MIT; see THIRD_PARTY_NOTICES.md.
 * Android/FermataX lifecycle integration is a FermataX modification.
 */
```

## 3. Authoritative reference map

### 3.1 Protocol and provider compatibility

Primary semantic authority:

- `https://stremio.github.io/stremio-addon-sdk/protocol.html`
- `https://stremio.github.io/stremio-addon-sdk/api/responses/manifest.html`
- `https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md`
- `https://stremio.github.io/stremio-addon-sdk/api/responses/subtitles.html`

Reference files:

- `stremio-addon-client/lib/stringifyRequest.js`
- `stremio-addon-client/lib/util/isSupported.js`
- `stremio-addon-client/lib/AddonCollection.js`
- `stremio-core/src/types/addon/request.rs`
- `stremio-core/src/types/addon/manifest.rs`
- `stremio-core/src/models/common/resource_loadable.rs`
- `stremio-core/src/models/meta_details.rs`
- `nuviomobile/composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/AddonManifestParser.kt`
- `nuviomobile/composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamParser.kt`
- `nuviomobile/composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamFetchSupport.kt`

Port semantics, not language/runtime structure. FermataX remains typed Java with its existing
bounded HTTP, DNS/redirect validation, cache and encrypted source identity.

### 3.2 Stream aggregation and playback resolution

Reference files:

- `harbor/src/lib/streams/addons.ts`
- `harbor/src/lib/streams/parser.ts`
- `harbor/src/lib/streams/pipeline.ts`
- `harbor/src/lib/streams/preflight.ts`
- `harbor/src/lib/streams/resolve.ts`
- `stremio-core/src/models/meta_details.rs`

Adapt these concepts:

- Provider-local requests execute independently.
- A failed provider does not erase healthy results.
- Results publish incrementally.
- Stream identity is semantic, not title-based.
- A previously selected stream is reusable only after matching the current provider response.
- Expired/authenticated streams are re-resolved, never silently replaced with an unrelated stream.

Do not port Harbor's Tauri, React, mpv, debrid, account or desktop-specific code.

### 3.3 P2P/torrent

Reference files:

- `harbor/src-tauri/src/torrent_engine/selftest/stream_probe.rs`
- `harbor/src-tauri/src/torrent_engine/stream_route.rs`
- `harbor/src-tauri/src/torrent_engine/cache_sweep.rs`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/AlertListener.java`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/SessionManager.java`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/TorrentHandle.java`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/alerts/MetadataReceivedAlert.java`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/alerts/PieceFinishedAlert.java`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/alerts/StateUpdateAlert.java`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/alerts/TorrentErrorAlert.java`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/alerts/FileErrorAlert.java`
- `jlibtorrent/src/main/java/com/frostwire/jlibtorrent/alerts/HashFailedAlert.java`

FermataX currently uses jlibtorrent `2.0.12.9`. Check the API from that exact dependency before
copying code from a newer pinned repository snapshot.

### 3.4 Subtitles

Reference files:

- `harbor/src/lib/subtitles/search.ts`
- `harbor/src/lib/subtitles/autoload.ts`
- `harbor/src/lib/subtitles/parser.ts`
- `harbor/src/lib/subtitles/encoding.ts`
- `harbor/src/lib/subtitles/language.ts`
- `harbor/src/lib/player/subtitle-load.ts`
- `stremio-core/src/types/resource/subtitles.rs`

Port provider aggregation, normalization, ranking and bounded format conversion. Do not copy the
React/mpv subtitle UI or desktop file-picker behavior.

### 3.5 UI behavior only

Use Stremio Web and the supplied Fermata Xtream Web bundle only to inspect:

- loading/partial/empty/error states;
- catalog ordering and pagination;
- details-to-stream flow;
- episode selection and next episode behavior;
- addon install/configure/remove behavior.

Do not import their JavaScript, WASM, styles, routes or bridge protocol.

## 4. Target architecture

The target is one operation model, one playback attempt owner, one subtitle session owner and one
torrent session owner. Facades may delegate, but no second scheduler or shadow state is allowed.

### 4.1 Operation context

Add under `modules/stremio/src/main/java/me/aap/fermata/addon/stremio/lifecycle/`:

```text
StremioOperation.java
StremioOperationScope.java
StremioDeadline.java
```

Required conceptual API:

```java
public final class StremioOperation implements AutoCloseable {
  long id();
  String logicalKey();
  RequestGeneration.Token generation();
  boolean isCurrent();
  void throwIfStale();
  <T extends StremioCall<?>> T own(T call);
  AutoCloseable own(AutoCloseable resource);
  ScheduledFuture<?> deadline(Duration timeout, Runnable action);
  void close(); // idempotent; cancel calls, timers and resources
}
```

Rules:

- Exactly one `StremioOperation` owns each user action or playback attempt.
- All callbacks check `isCurrent()` before changing UI, DB, MediaSession or cache ownership.
- Cancellation completes callers with `CancellationException`; it is not rendered as an error.
- Closing a parent scope closes children in reverse acquisition order.
- No `Thread.sleep()` and no unowned `postDelayed()` loops.
- Do not globally replace `FutureSupplier`; bridge at existing boundaries.

Reuse and extend the existing:

- `net/RequestGeneration.java`
- `lifecycle/StremioCall.java`
- `runtime/StremioRuntime.java`

### 4.2 Typed result and failure model

Add under `failure/`:

```text
StremioFailure.java
StremioFailureMapper.java
StremioRecovery.java
```

Minimum model:

```java
record StremioFailure(
    Code code,
    Phase phase,
    String providerKey,
    boolean retryable,
    UserAction action,
    Throwable cause) {}
```

Codes must cover:

```text
CANCELLED, STALE_RESULT, DNS, CONNECT_TIMEOUT, HEADER_TIMEOUT, BODY_TIMEOUT,
HTTP_AUTH, HTTP_RATE_LIMIT, HTTP_SERVER, REDIRECT_REJECTED, MALFORMED_MANIFEST,
MALFORMED_RESOURCE, UNSUPPORTED_RESOURCE, PROVIDER_DISABLED, NO_RESULTS,
NO_PLAYABLE_STREAM, STREAM_EXPIRED, ENDPOINT_REJECTED, P2P_METADATA_TIMEOUT,
P2P_NO_PEERS, P2P_DATA_TIMEOUT, P2P_FILE_ERROR, P2P_LOW_STORAGE,
PLAYER_CREATE, PLAYER_PREPARE, PLAYER_FIRST_FRAME_TIMEOUT, PLAYER_DECODER,
SUBTITLE_NOT_FOUND, SUBTITLE_DOWNLOAD, SUBTITLE_FORMAT, SUBTITLE_ATTACH,
CACHE_IO, DATABASE, INTERNAL
```

No production `catch (RuntimeException ignored)` is allowed on a state-changing path. A catch must
do one of: map and publish failure, cancel stale work, compensate a transaction, or log a bounded
diagnostic code. Never log provider URLs, tokens, cookies or raw manifests.

### 4.3 Provider resource state

Port the semantics of Stremio Core `ResourceLoadable`, but use a Java model owned by FermataX:

```java
sealed interface ProviderLoadState<T> {
  record Loading<T>(...) implements ProviderLoadState<T> {}
  record Ready<T>(T value, ...) implements ProviderLoadState<T> {}
  record Empty<T>(...) implements ProviderLoadState<T> {}
  record Failed<T>(StremioFailure failure, ...) implements ProviderLoadState<T> {}
  record Cancelled<T>(...) implements ProviderLoadState<T> {}
}
```

Every state contains provider identity, request identity and completion generation. The UI derives
its state from provider states; it must not maintain a second boolean `loading` truth.

### 4.4 Multi-ID content identity

Add `playback/ContentIdentitySet.java` or move it to `browse/` if both browse and playback own it.

Required fields:

```text
type, canonicalId, imdbId, tmdbId, providerIds, videoId, season, episode
```

Required behavior:

- Preserve source-scoped provider IDs.
- Merge only recognized canonical namespaces.
- For each provider, choose IDs matching manifest `types` and `idPrefixes`.
- Do not broadcast every ID to every provider.
- Stable UI/persistence identity remains canonical and contains no URL/credential.

Replace the single-ID assumption in:

- `playback/StreamAggregationRequest.java`
- `browse/BrowseMedia.java`
- protocol request planning in `integration/StremioProtocolRequestPlanner.java`

Use schema migration only if the identity set must be persisted. Prefer deriving it from existing
meta/video records first.

### 4.5 Incremental stream aggregation

Refactor `playback/StreamAggregator.java` without changing its public facade until callers migrate.

Required behavior:

- Maximum four concurrent providers.
- Each provider has an independent deadline and cancellation handle.
- Publish a new immutable snapshot after each provider completion, throttled to 100-200 ms.
- Keep a final completion future for persistence/diagnostics.
- Deduplicate direct streams by normalized target plus required headers.
- Deduplicate torrents by `infoHash + fileIdx`.
- When torrents merge, union tracker sources and retain every contributing provider.
- Never deduplicate by title, provider label or display description.
- A malformed choice is rejected without dropping healthy choices from the same provider.
- Results have a deterministic order independent of callback completion order.

Replace the existing two-snapshot `response()/completion()` behavior with an observable snapshot
contract while keeping compatibility adapters for current callers.

### 4.6 Playback attempt supervisor

Add under `playback/`:

```text
PlaybackAttemptState.java
PlaybackAttempt.java
PlaybackAttemptSupervisor.java
PlaybackAttemptObserver.java
```

State machine:

```text
CREATED -> RESOLVING -> PREPARING -> DATA_READY -> PLAYER_READY -> FIRST_FRAME -> PLAYING
   |           |            |             |              |             |
   +-----------+------------+-------------+--------------+-------------+-> FAILED
   +-----------+------------+-------------+--------------+-------------+-> CANCELLED
PLAYING -> ENDED
```

Only declared forward transitions are accepted. Duplicate engine callbacks are idempotent. A
callback from a closed/old attempt is ignored and counted diagnostically.

The supervisor owns:

- selected `PlaybackDescriptor`;
- remote preparation call;
- `RemotePlaybackRequest` and its release callback;
- P2P prepared lease when applicable;
- subtitle session;
- first-frame timer;
- retry count;
- state published to top bar/player UI.

Switching A to B must synchronously mark A stale and detach its UI identity before B can publish.
Do not wait for asynchronous cleanup before changing ownership, but cleanup must still run.

Decoder fallback is allowed once only, and only when the second engine supports the exact request
profile. Do not retry `NO_PEERS`, auth, unsupported format or a stale attempt.

### 4.7 Engine event bridge

Do not make Stremio own MediaSession. Introduce the smallest optional shared callback necessary for
first-frame evidence:

```java
default void onVideoFirstFrame(MediaEngine engine) {}
```

Candidate integration:

- ExoPlayer: forward Media3 `onRenderedFirstFrame()`.
- VLC: forward the first valid video-output event for the current prepare generation. Verify the
  exact LibVLC event available in the dependency before coding.
- Audio-only playback treats `onEngineStarted()` as first output.

The default method preserves other addons. `MediaSessionCallback` forwards this event only to an
optional item lifecycle contract implemented by Stremio playback items. Do not add Stremio package
references to Fermata core.

First-frame timeout starts after `PLAYER_READY`, not when the provider request starts. Suggested
initial value is 15 seconds. It must be configurable in tests and must cancel on pause/stop/switch.

### 4.8 P2P readiness and alert routing

Refactor these existing classes:

- `torrent/StremioTorrentEngine.java`
- `torrent/TorrentSessionOwner.java`
- `torrent/TorrentPreparationCoordinator.java`
- `torrent/TorrentStreamLease.java`
- `torrent/TorrentHttpServer.java`

Add:

```text
TorrentAlertRouter.java
TorrentReadinessGate.java
TorrentRangeProbe.java
TorrentWaiter.java
```

`TorrentAlertRouter` registers one jlibtorrent `AlertListener` per `SessionManager`, then routes by
info-hash/handle to interested waiters. Listen only for the required alert IDs. Deregistration must
be idempotent and occur before session shutdown.

Replace 100/150 ms polling in `StremioTorrentEngine.awaitHandle()` and
`TorrentStreamLease.await()` with alert-driven wakeups. A low-frequency status sample, no faster
than once per second, may remain for metrics and recovery from dropped alerts.

Readiness sequence:

```text
metadata available
-> requested file selected and validated
-> file priorities applied
-> at least one usable peer, or explicit no-peer deadline
-> head range pieces complete
-> tail/index range pieces complete for containers that need it
-> loopback HEAD/Range probe returns valid Content-Length/Content-Range
-> DATA_READY
```

Do not use a fixed 8-16 MB buffer for every file. Compute the target from piece size, media size,
container and observed download rate, with hard lower/upper bounds. The exact policy must be a pure
function covered by tests.

Progress exposed to UI must separate:

- preparation phase;
- peers/seeds;
- download rate;
- contiguous playable bytes;
- target playable bytes;
- whole-torrent completion.

Never display whole-torrent completion as playback readiness. This prevents 100% -> 9x% jumps.

Cancellation closes the loopback response, waiters and selected handle lease. It does not delete
another active attempt's cache. Cache cleanup only touches the Stremio-owned canonical cache root.

### 4.9 Subtitle session

Add `subtitle/StremioSubtitleSession.java` as the sole owner of subtitle discovery, selection,
download, conversion and attachment for one playback attempt.

It replaces ownership currently split among:

- `subtitle/SubtitleAggregator.java`
- `integration/StremioSubtitlePlaybackBridge.java`
- `item/StremioPlaybackResource.java`
- `item/StremioSubtitleSelectionStore.java`
- engine automatic subtitle selection.

Required flow:

```text
discover provider/embedded candidates
-> publish partial language-filtered list
-> user/default selects descriptor
-> bounded download/decompress
-> encoding detection to UTF-8
-> format sniffing from payload, not extension alone
-> normalize/convert
-> parse validation (non-empty cues, sane timestamps)
-> attach to the current engine/source only
```

When an external subtitle is selected, embedded auto-selection must be disabled for that attempt.
Switching item or stream closes the previous subtitle session and removes its surface/consumer.

Resolve the current contract mismatch in `SubtitleFormat`: ASS/SSA/TTML cannot be marked supported
while `isEngineReadable()` rejects them. Either convert them to normalized SRT or report them as
unsupported consistently. Do not claim support before a parser/converter test passes.

Provider timeout and failure are per-provider. The selection UI must show partial results and a
loading state while remaining providers run.

### 4.10 UI state and lifecycle

Every page uses one of:

```text
LOADING, PARTIAL, CONTENT, EMPTY, ERROR, CANCELLED
```

Rules:

- A spinner always has an operation owner and deadline.
- `EMPTY` is distinct from provider failure.
- Partial content remains usable while slow providers finish.
- Retry creates a new generation; it never revives the old operation.
- Fragment destruction cancels view-bound work, but active media playback remains owned by the
  service/playback attempt.
- Process restore rehydrates identity and progress, never a stale transport URL or torrent handle.
- Top bar title, SmartTopCard and Continue derive from the current canonical item, not the last
  rendered details page.

Keep `StremioFragment` as a facade. Controllers must not each create an independent truth for the
same route or playback state.

### 4.11 Addon configuration isolation

Configuration WebView is not the Stremio runtime. Move it to a dedicated process only after the
playback phases are stable:

```xml
android:process=":stremio_config"
```

Use a separate WebView data-directory suffix, no JavaScript bridge, validated HTTPS/stremio result
URLs, same-origin navigation and bounded completion timeout. Clear cookies/storage/cache when the
configuration session closes. POST/cookies are allowed only inside this isolated session.

## 5. Fault-prevention matrix

The implementation is incomplete unless these classes of failure have explicit ownership and a
test or device scenario:

| Failure class | Required behavior |
| --- | --- |
| Slow/hung provider | Other providers publish; slow call times out and cancels |
| Malformed provider payload | Reject provider/item only; no crash or lost healthy results |
| DNS/redirect/body stall | Typed deadline failure; no unbounded worker/file/cache entry |
| Disable/remove provider mid-call | Source lease invalidates call and result |
| Rapid A -> B -> C selection | Zero stale UI, progress, subtitle, surface or cleanup ownership |
| Direct stream expiry | Re-resolve semantic stream; never choose unrelated replacement |
| Torrent without metadata | Bounded metadata error with cancel action |
| Torrent loses peers | Rebuffer state, bounded recovery, then explicit failure |
| Range seek to missing pieces | Reprioritize range; response blocks with deadline, not forever |
| Player created but no first frame | One valid engine retry or explicit decoder/first-frame error |
| Subtitle provider slow | Partial list and loading state; selection remains responsive |
| Subtitle invalid/encoded/archive | Bounded normalize/validation failure; no phantom selection |
| Fragment/AA reconnect | View state may recreate; service playback ownership remains correct |
| Process death | Restore canonical item/progress only; no native/session object restore |
| Low storage/cache full | Fail before preparation; cleanup only owned inactive entries |
| Executor rejection | Typed internal/cancelled result; no spinner remains active |
| Addon disabled/removed | No route to absent fragment; all other addons unaffected |

## 6. Test architecture

Do not use real sleeps in unit tests. Add deterministic helpers:

```text
FakeClock
DeterministicScheduler
FakeProviderCall
FakePlaybackEngineEvents
FakeTorrentAlertSource
FakeTorrentStatusSource
FailureInjector
```

Required automated suites:

1. State-machine transition table, duplicate callbacks and illegal transitions.
2. Completion-order permutations for two to four providers.
3. Timeout/cancellation at every asynchronous boundary.
4. Ten deterministic A/B/C switches with zero cross-item writes.
5. Provider disable/remove during catalog, stream, subtitle and config calls.
6. Direct/P2P stream deduplication and tracker union.
7. Head/tail range readiness and seek reprioritization.
8. Dropped-alert recovery through the low-frequency status sample.
9. First-frame timeout, single engine retry and stale engine callback rejection.
10. Subtitle archive, encoding, format conversion, invalid cues and item switch.
11. Process restore from canonical identity without transport resurrection.
12. Cross-addon isolation tests for TV, Radio, Web, YouTube, Podcast and Audiobook routes.

Focused commands after each phase:

```powershell
.\gradlew :stremio:testAutoDebugUnitTest :stremio:testMobileDebugUnitTest
.\gradlew :fermata:testAutoDebugUnitTest :fermata:testMobileDebugUnitTest
```

Release gate commands:

```powershell
.\gradlew :fermata:compileAutoReleaseJavaWithJavac `
  :fermata:compileMobileReleaseJavaWithJavac `
  :stremio:lintVitalAnalyzeAutoRelease
```

Artifact builds are separate from the reliability goal and require the configured release signing
credentials. A passing JVM suite is not evidence that live P2P or DHU interaction is accepted.

## 7. Phased write plan

### Phase 0: characterization and provenance

- Freeze current Auto/Mobile test counts and behavior contracts.
- Add fixtures for every known bug.
- Record all reference commits/licenses.
- No production behavior change.

Exit: baseline passes and each known bug has a failing or characterization test.

### Phase 1: lifecycle and failure foundation

- Implement operation scope and typed failures.
- Migrate one pipeline at a time from ignored exceptions/unowned timers.
- Keep compatibility adapters at public facades.

Exit: every migrated operation can be cancelled and cannot publish stale state.

### Phase 2: provider compatibility

- Add multi-ID request planning.
- Add provider load states and incremental stream aggregation.
- Preserve stable UI order and source-scoped identity.

Exit: malformed/slow providers cannot block healthy providers; external catalogs/meta/streams work.

### Phase 3: playback supervisor and direct playback

- Add attempt state machine and engine event bridge.
- Enforce A -> B ownership and one decoder retry.
- Validate HTTP/HLS/DASH before touching P2P behavior.

Exit: direct playback, switch, Back, Dashboard and reconnect pass without stale state.

### Phase 4: P2P

- Add alert router, readiness gate and range probe.
- Remove active polling loops.
- Separate readiness from whole-torrent completion.
- Harden cancel, seek, no-peer, stall and cache cleanup.

Exit: no silent black screen, no automatic unexplained Back and all P2P failures are actionable.

### Phase 5: subtitles

- Add the single subtitle session.
- Add partial provider results and bounded conversion/validation.
- Enforce current-item/current-engine attachment.

Exit: selected subtitle renders or produces a specific failure; it never crosses items.

### Phase 6: UI/lifecycle/config isolation

- Derive page UI from explicit state.
- Verify AA reconnect/process restore.
- Isolate config WebView after functional paths are stable.

Exit: no infinite loading and no Stremio state leaks to other addons.

### Phase 7: adversarial release audit

- Run all fault-injection and cross-addon suites.
- Run focused security, cache quota and thread/resource leak review.
- Perform manual Mobile and DHU acceptance with healthy direct and well-seeded P2P sources.

Exit: all automated gates pass, manual blockers are zero and residual external/provider risks are
documented rather than hidden.

## 8. Agent implementation rules

1. Inspect the dirty worktree before every phase; never revert unrelated changes.
2. Change only one ownership boundary per patch.
3. Add characterization tests before changing existing behavior.
4. Do not declare a phase complete from compilation alone.
5. Do not use provider-name hardcoding for timeout, ranking or compatibility.
6. Do not add a second scheduler when an existing runtime scheduler can own the deadline.
7. Do not persist transport URLs, cookies, headers, torrent handles or operation IDs.
8. Do not expose secrets through `toString()`, logs, stable IDs or test fixtures.
9. Do not catch and ignore state-changing failures.
10. Do not alter shared Back/playerbar/top-bar rules to solve a Stremio-local bug.
11. Do not copy Stremio Web or decompiled Fermata Xtream code.
12. After each phase, review the diff for UI/resource/schema changes and state them explicitly.

## 9. Definition of done

The addon is ready only when all are true:

- Catalog, meta, search, Discover, details, movie/series/episode, stream and subtitle resources work
  across compliant external addons.
- Add/configure/enable/disable/remove flows are bounded and isolated.
- HTTP/HLS/DASH playback is deterministic.
- P2P preparation proves data readiness before player handoff.
- Every loading state terminates as content, empty, error or cancellation.
- Every playback failure has a typed reason and retry/cancel/source-selection action.
- Ten A/B/C switches produce zero stale title, progress, subtitle, frame or cache ownership.
- Process/lifecycle recovery restores canonical state without reviving stale transports.
- Cross-addon regression and Auto/Mobile suites pass.
- Manual Mobile and DHU acceptance pass for direct playback, P2P, subtitle, Back and Dashboard.
- License/provenance notices match every adapted source block.

Passing unit tests alone does not satisfy this definition.
