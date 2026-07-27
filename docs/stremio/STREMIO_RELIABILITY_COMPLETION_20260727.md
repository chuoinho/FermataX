# Stremio Reliability Implementation Report

Date: 2026-07-27
Specification: `docs/stremio/STREMIO_RELIABILITY_IMPLEMENTATION_GUIDE.md`

## Scope preserved

- Native FermataX UI, Dashboard, SmartTopCard, top bar, playerbar and Back policy remain owned by
  FermataX.
- Stremio has no split view and does not accept YouTube/`ytId` streams.
- TV, Radio, Web, YouTube, Podcast and Audiobook retain their existing playback contracts.
- No package, stable item ID, preference key or database schema migration was introduced.
- Stremio runtime, transport, torrent, subtitle and UI operations remain addon-local and cancellable.

## Phase results

| Phase | Result | Main evidence |
|---|---|---|
| 0 | Complete | Frozen invariants, fixtures, pinned upstream revisions and license/provenance map |
| 1 | Complete | Owned operation scopes, generation invalidation, typed failures and cancellation tests |
| 2 | Complete | Multi-ID provider routing, finite provider states, incremental deterministic aggregation |
| 3 | Complete | One playback-attempt supervisor, stale-attempt rejection, direct validation, first-frame evidence and one bounded decoder fallback |
| 4 | Complete | Alert-driven torrent readiness, verified head/tail ranges, loopback probe, truthful progress, seek priority and bounded cache ownership |
| 5 | Complete | Per-video subtitle session, partial results, bounded archive/encoding/format conversion, validation and current-engine attachment |
| 6 | Complete | Finite presenter/source/discovery/config loading, lifecycle cancellation, view recreation and canonical process restore |
| 7 | Complete | Fault injection, cross-addon tests, release compilation, fatal lint, source audit and Mobile/DHU acceptance pass |

## Reliability invariants implemented

- A request result is accepted only while its owner, generation, route/item identity and lifecycle
  are current.
- Switching A to B invalidates A synchronously. Ten deterministic A/B/C switch cycles accept zero
  stale frame, title, progress, subtitle or cache events.
- Every visible loading state has an owner and a finite deadline. Timeout invalidates UI ownership
  before cancellation can invoke completion inline.
- Direct HTTP, HLS and DASH descriptors are validated before playback handoff.
- Torrent playback is handed to a decoder only after metadata, selected-file identity, verified
  boundary pieces and loopback byte ranges are ready.
- Torrent failures distinguish no peers, data stall, hard timeout, bridge failure and engine
  unavailability. Timeout logs do not expose an info hash.
- Subtitle discovery/download/conversion belongs to exactly one video session and cannot attach to
  a later item.
- Process restoration loads a stable DB projection; transient URL, header, cookie, torrent handle,
  operation ID and decoder state are not restored.
- Source secrets remain in encrypted storage. SQLite stores only opaque references and redacted
  transport values.

## Automated verification

| Gate | Result |
|---|---|
| Stremio Auto unit tests | 509 passed, 0 failed, 0 skipped |
| Stremio Mobile unit tests | 509 passed, 0 failed, 0 skipped |
| Fermata core Auto unit tests | 281 passed, 0 failed, 0 skipped |
| Fermata core Mobile unit tests | 267 passed, 0 failed, 0 skipped |
| ExoPlayer Auto/Mobile tests | 6 passed |
| VLC Auto/Mobile tests | 6 passed |
| Auto release Java compilation | Passed |
| Mobile release Java compilation | Passed |
| Stremio Auto/Mobile fatal lint | Passed |

The full non-fatal `lintAnalyzeAutoRelease` task did not terminate within the bounded ten-minute
CI window. `lintVitalAnalyzeAutoRelease` and `lintVitalAnalyzeMobileRelease` completed successfully.
This tool-performance limitation is not recorded as a clean full-lint result.

## Mobile and DHU acceptance results

Test device: Redmi Note 8, Android 16, package `me.app.fermataX.auto`, release-signed universal
APK, Android Auto Desktop Head Unit.

- Cold launch, Stremio Home, Continue Watching, details and stream lists reached finite content.
- Direct VNStream HLS played video and rendered the selected Vietnamese subtitle.
- Provider labels render as `VNStream | 2`; no mojibake separator remains.
- A Torrentio Interstellar source reached 95 peers and 4.1 MB/s and played for more than 100
  seconds on DHU without a false first-frame timeout or unexplained navigation.
- A device-specific VLC surface failure was reproduced. The old P2P request was released once,
  one ExoPlayer fallback was created, and the fallback produced a real video frame. Early
  first-frame callbacks are now latched until player-ready instead of being discarded.
- P2P playback starts at zero until the first frame, then owns one deferred resume seek. Direct
  playback retains its existing initial-seek behavior.
- Back from the P2P player returned to the source list, then details and Home while playback
  continued. Dashboard returned at its first position with normal card sizing and SmartTopCard.
- Switching from Stremio to Podcasts changed the topbar to `Podcasts`; MediaSession retained only
  the active Interstellar title and no peer/rate text leaked into durable metadata.
- Zero-progress/invalid P2P attempts returned a typed actionable failure without crash or an
  infinite spinner. Preparation kept topbar, nav, playerbar and cancel controls visible.
- Subtitle selection exposed a finite loading/result state. Direct playback attachment and item
  isolation were verified; no subtitle from a previous video appeared on the next item.
- No `FATAL EXCEPTION`, app ANR or cross-addon state mutation was observed in the final DHU run.

## Release smoke checklist

Use a healthy direct source and a legal, well-seeded P2P source. A test is a blocker if it crashes,
loads forever, shows a silent black screen, changes another addon's state or displays stale media.

1. Open Stremio directly after a cold app start. Verify Home reaches content, empty or actionable
   error within 30 seconds.
2. Open Search, Discover, Library and Addons. Navigate Back through each route and verify Dashboard
   still returns to the first position with unchanged card sizing.
3. Add, configure, disable, enable, refresh and remove a compliant addon. Interrupt each flow once
   with Back and confirm no spinner or mutation survives the closed screen.
4. Play one direct HTTP/HLS/DASH item. Verify title, artwork, playerbar, fullscreen and Back; switch
   to another Stremio item and confirm the first frame/title never returns.
5. Switch Stremio to TV, Radio, YouTube, Podcast and Audiobook. Verify no Stremio title, subtitle,
   progress, player state or cache event appears in the selected addon.
6. Play a well-seeded P2P item. Verify peer, speed, verified-buffer progress and cancellation are
   truthful; seek near the middle and near the end; then switch to another item.
7. Test a zero-peer and a stalled P2P source. Verify an actionable error appears within the bounded
   readiness window and the UI never performs an unexplained Back.
8. Load subtitles from fast, slow, invalid, gzip/zip, UTF-16, ASS/SSA and TTML sources. Confirm
   partial results remain selectable, valid cues render in sync and invalid payloads fail clearly.
9. While Stremio is loading and while video is playing, leave and reconnect Android Auto. Verify
   navigation restores from canonical content and current service playback remains correctly owned.
10. Kill and recreate the app process. Verify durable item/progress restore works without reviving a
    stale stream URL, torrent session or subtitle attachment.

## Residual external risks

- Provider availability, malformed manifests, expiring tokens and server-side protocol deviations
  remain external. They now terminate as bounded typed failures.
- P2P startup still depends on tracker/DHT reachability, peer availability, NAT and device/vendor
  network policy. Readiness evidence prevents these conditions from becoming a silent handoff.
- Decoder/device codec differences remain outside the addon. Exactly one compatible fallback is
  allowed; repeated fallback loops are prohibited.
- Configuration pages requiring cross-origin credential propagation or unsupported POST semantics
  intentionally fail instead of weakening WebView isolation.
- Torrent startup time and whether VLC or ExoPlayer wins remain source-, codec- and device-specific.
  The bounded one-fallback policy prevents this variation from becoming a fallback loop.
