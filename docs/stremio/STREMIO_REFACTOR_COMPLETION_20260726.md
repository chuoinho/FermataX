# Stremio Behavior-Preserving Refactor Completion

> Status: HISTORICAL EVIDENCE for the retired native Stremio implementation. The current
> implementation authority is [`web-only/README.md`](web-only/README.md).

Date: 2026-07-26

## Scope and invariant

The Stremio addon was refactored without intentionally changing UI output, navigation, Back,
playerbar, top bar, SmartTopCard, MediaSession ownership, source identity, database schema,
projection format, P2P behavior, subtitle behavior, or other addons.

Baseline archive:

- `E:\Chatgpt\fermata-backups\FermataX-stremio-refactor-baseline-20260726-221659.zip`
- SHA-256: `428CF3764A0DB7B8AB43D27F413F541648731C95C634FF0E48FCF841C99418A5`

## Before and after

| Facade/coordinator | Before | After | Result |
| --- | ---: | ---: | --- |
| `StremioPresentationGateway` | 1,593 lines | 146 lines | Route facade over page loaders |
| `StremioSessionGatewayAdapter` | 988 lines | 283 lines | Session/item facade over stores and resolvers |
| `StremioRepository` | 937 lines | 259 lines | Public facade over domain DAOs |
| `TorrentHttpServer` | 621 lines | 343 lines | Loopback HTTP facade over stream leases/policy |
| `StremioTorrentEngine` | 672 lines | 371 lines | Engine facade over preparation/session/cache owners |
| `StremioFragment` | 893 lines | 552 lines | Fragment lifecycle facade over UI controllers |
| `StremioPresentationAdapter` | 748 lines | 228 lines | Recycler adapter with extracted holders/bindings |
| `StremioProtocolClient` | 437 lines | 349 lines | Protocol orchestration with extracted planning/policy/errors |

## Phase results

1. Baseline and characterization
   - Captured the source archive and behavioral contracts.
   - Covered route-cache eviction, progress ownership, subtitle action, lifecycle, and stable UI IDs.

2. Async and lifecycle
   - Added the shared `StremioCall` contract.
   - Standardized completion, cancellation, stale-result rejection, and close ownership.

3. Presentation
   - Split Home, Discover, Search, Details, Streams, and Library page loaders.
   - Extracted page construction, formatter, presentation models, request state, target store, and
     typed registry.
   - Preserved stable IDs, ordering, route cache, and the exact provider separator `" \u00b7 "`.

4. Session and persistence
   - Split projection codec/store, session reads, progress, favorites, restore, media resolution,
     adjacent playback, and voice search.
   - Preserved projection version `1`, cache resource `session-item-v1`, SHA-256 cache identity,
     provider rebinding, generation ownership, restore identity, and adjacent provider preference.

5. Data
   - Split source, metadata, video, progress/favorite, session, and cache DAOs.
   - Kept `StremioRepository` public methods unchanged.
   - Preserved `SerialDatabaseExecutor`, SQL ordering, transaction boundaries, schema, retention,
     validation timing, and exception behavior.

6. P2P playback
   - Split preparation/single-flight/timeout, native session/resume ownership, cache maintenance,
     stream lease, stream policy, and progress mapping.
   - Preserved loopback token/range semantics, timeouts, quotas, priority windows, cancellation,
     source leases, cleanup boundaries, and `RemotePlaybackProgress` ordering.

7. UI
   - Split navigation, search, subtitle, favorite, source, playback handoff, and viewport
     controllers.
   - Extracted presentation holders and bindings.
   - Restored the only accidental resource difference found during differential review; all
     non-Java Stremio source files now match the baseline byte-for-byte.

8. Network and protocol
   - Split protocol request planning, resource request policy, and error mapping.
   - Kept HTTP cache, transport, redirect, DNS validation, SSRF restrictions, sensitive-header
     policy, body limits, deadlines, and cache identity unchanged.

9. Cleanup
   - Removed `PlaybackDescriptorRefresher`, the only production type proven to have no caller.
   - No generated APK, AAB, class, log, or temporary file exists under `modules/stremio/src`.

## Verification

| Check | Result |
| --- | --- |
| Stremio Auto unit/integration tests | 466 passed, 0 failed/skipped |
| Stremio Mobile unit/integration tests | 466 passed, 0 failed/skipped |
| Fermata core Auto regression tests | 275 passed, 0 failed/skipped |
| Fermata core Mobile regression tests | 261 passed, 0 failed/skipped |
| Auto release Java compile | Passed |
| Mobile release Java compile | Passed |
| Stremio release lintVital | Passed |
| Release R8/minification | Passed |
| Universal APK packaging | Passed |
| Auto AAB packaging | Passed |
| Independent adversarial reviews | No concrete findings |

The session characterization suite includes 100 generation/ownership transitions, exceeding the
10-transition A/B/C exit criterion.

Release artifacts produced during verification:

- Universal APK: `E:\Chatgpt\fermata\fermata\build\outputs\apk_from_bundle\autoRelease\fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk`
  - Size: 367,231,941 bytes
  - SHA-256: `BEA65B132BADD7EEDDB505DA17687DDE8E164BF47ADDA79BAD5A9EDCA2C7B463`
- AAB: `E:\Chatgpt\fermata\fermata\build\outputs\bundle\autoRelease\fermata-2.0.1-me.app.fermataX.auto-auto-release.aab`
  - Size: 201,961,985 bytes
  - SHA-256: `1EC6B7CC7CC5D3E85F2C4DC86560F1153BB08FE71DDCBEEE1182B822E323F767`

The universal APK contains Stremio layouts and jlibtorrent for arm64-v8a, armeabi-v7a, and x86_64.
The AAB contains the Stremio manifest, dex, resources, and native libraries. The feature remains
install-time with fusing enabled.

## Manual acceptance checklist

### Mobile

1. Open Stremio, Home, Discover, Search, Library, Settings, and source management.
2. Confirm labels, card sizes, ordering, details layout, subtitle option, and scroll position match
   the previous build.
3. Search with the software keyboard, submit, cancel, and immediately navigate away; confirm no
   stale result replaces the current page.
4. Open a movie and an episode, select direct and P2P streams, then use Back at each level.
5. Select a subtitle, switch episode/movie, and confirm subtitle ownership does not cross items.
6. Start item A, switch quickly to B and C, then verify Continue/progress belongs to each item.

### Android Auto / DHU

1. Open Stremio from Dashboard and verify nav rail, top bar, card order, and initial scroll position.
2. Exercise Home -> Details -> Stream -> player -> Back and confirm the existing Back rule.
3. Open Search and confirm AA keyboard input, cancel, and result navigation.
4. Start playback, return to Dashboard, open SmartTopCard, and verify title/current-item routing.
5. Switch Stremio -> TV -> Radio -> Stremio and verify playerbar/top bar ownership never leaks.
6. Disable/remove one Stremio source and verify other Stremio sources and all other addons remain
   available.

### P2P and lifecycle

1. Use a healthy torrent with visible peers and verify preparing, buffering, speed, and completion
   progress appear in the same order as before.
2. Cancel during metadata wait, initial buffering, and active playback; verify the task and native
   session stop and the cache remains consistent.
3. Switch rapidly between two P2P movies and confirm the old video/progress does not survive.
4. Leave and re-enter the addon during P2P playback, then stop playback and verify cache cleanup.
5. Restart the app and confirm process restore opens only the valid current item and Back target.

## Residual device risk

Unit, integration, release, R8, lint, and package inspection pass. Real DHU interaction and live
multi-peer torrent timing cannot be proven by JVM tests, so the manual DHU/P2P checklist remains the
final device acceptance step before publishing.
