# Stremio Acceptance Report

Status: release candidate; device/DHU acceptance pending

Last updated: 2026-07-23

Scope update (2026-07-22): Stremio `ytId`/YouTube handoff has been removed. Mixed provider
responses remain parseable, but `ytId` choices are omitted before playback descriptor and UI
creation. Film-first UI work is tracked in `STREMIO_VIEWING_EXPERIENCE_GOAL.md`; this report remains
the evidence record for the protocol, security, persistence and direct-playback foundation.

This report records evidence for `STREMIO_ADDON_GOAL.md`. A passing build is necessary but does not
replace device, DHU, security or isolation evidence.

## 2026-07-23 Hardening Gate

This gate supersedes older `In review`/`In remediation` labels below where the same subsystem is
listed. The implementation remains a native Java Stremio protocol client; the supplied Stremio Web
bundle is an operational behavior reference, not an embedded runtime.

| Check | Result | Evidence |
| --- | --- | --- |
| Stremio source/docs visible to Git | Pass | `modules/stremio/` and `docs/stremio/` are no longer ignored; staging/commit intentionally pending |
| Auto unit suite | Pass | 73 suites, 394 tests, 0 failures/errors |
| Mobile unit suite | Pass | 73 suites, 394 tests, 0 failures/errors |
| Auto APK assembly | Pass | `:fermata:assembleAutoDebug` |
| Release/R8/lintVital | Pass | `:fermata:packageAutoReleaseUniversalApk` and `:fermata:bundleAutoRelease` completed, including `:stremio:lintVitalAnalyzeAutoRelease` |
| Universal APK/AAB packaging | Pass | Stremio resources are fused into the universal APK; AAB contains `stremio/manifest/AndroidManifest.xml` and `stremio/dex/classes.dex` |
| Cinemeta artwork IDs | Pass | IMDb artwork paths accepted while arbitrary opaque credential paths remain blocked |
| Fresh-install bootstrap refresh | Pass | UI observes committed source revisions and refreshes the active route |
| Previous/next episode | Pass | Direct and exported items delegate to the deterministic episode queue |
| Expired stream selection | Pass | Semantic stream fingerprint required; no silent quality/server switch |
| Discover pagination | Pass | Pages append, deduplicate and remain bounded to 500 items/8 sessions; content-derived keys preserve focus and prevent full-list rebinds |
| Favorite consistency | Pass | DB failure compensates the Unified Favorites mutation; transaction tests cover all branches |
| Provider configuration | Partial by design | Same-origin navigation, validated HTTPS CDN resources, first-party cookies and projected submit supported; cross-origin context headers are stripped, unsupported WebView versions fail clearly, and POST forms remain explicitly blocked |
| Direct stream capability | Pass | HTTP/HLS/DASH only; unsupported external/torrent targets stay visible but non-playable |
| Subtitle preference | Pass | Dedicated language setting with region, base-language and English fallback |
| Lint analysis | Pass for release gate | `:stremio:lintVitalAnalyzeAutoRelease` passed; full debug `lintAnalyze` remains inconclusive after exceeding the local time limit |
| Physical device and DHU flows | Pending | Fresh install, provider configure, playback, episode transition and process-death checklist required |

## Baseline and Backup

- Baseline HEAD: `8ad7a27ea409f634040915548155208068c5facf` on `main`.
- Source-only local backup:
  `E:\Chatgpt\fermata-backups\fermatax-pre-stremio-20260721-151416.zip`.
- Backup inspection found no APK, AAB, JKS, `local.properties` or `google-services.json`.
- Toolchain and artifact-size evidence: `docs/stremio/PHASE0_BASELINE.md`.

## Phase 0 Evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Existing core Auto unit tests | Pass | `:fermata:testAutoDebugUnitTest`, build successful |
| Protocol/license references | Pass | `docs/stremio/REFERENCES.md` |
| Safe protocol fixtures | Pass | 13 valid JSON fixtures, malformed fixture rejected, URLs use `example.invalid` |
| Progress ownership A -> B | Pass | `PlaybackProgressOwnershipCharacterizationTest` |
| Delayed stale progress | Pass | `PlaybackProgressOwnershipCharacterizationTest` |
| Random A/B/C stress | Pass | 100 deterministic switches, 100 valid writes, 0 wrong item, 0 negative position |
| Process-death durability | Pending | Requires integrated Stremio item and device test |
| StrictMode DB/network | Pending | Requires Phase 2 repository/client |

## Phase 1 Evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Install-time feature manifest | Pass | Main/debug manifests declare install-time and `dist:fusing=true` |
| Auto module compile/tests | Pass | `:stremio:compileAutoDebugJavaWithJavac`, `:stremio:testAutoDebugUnitTest` |
| Mobile module compile/tests | Pass | `:stremio:compileMobileDebugJavaWithJavac`, `:stremio:testMobileDebugUnitTest` |
| Generated addon registration | Pass | `StremioShellTest` checks Dashboard, Navigation, STREMIO and resolver scheme |
| Stable shell action IDs | Pass | `stremio:action:add`, `stremio:action:addons` |
| Root route capability | Pass | `StremioShellTest` checks `AddonCapability.STREMIO` |
| Dashboard metadata role | Pass | `AddonUiMetadataTest` |
| Playback request profile | Pass | Auto/Mobile focused tests 6/6 |
| Deferred resolver | Pass | Auto/Mobile core regression; disabled addons stay disabled and concurrent delivery coalesces |
| Managed progress policy | Pass | Auto/Mobile core plus Podcast/Audiobook regression; generation ownership rejects stale writes |
| Disable/remove isolation on device | Pending | Requires APK/device test |
| Universal APK/AAB feature inspection | Pass | Release universal APK contains Stremio layouts; release AAB contains the Stremio manifest and dex split |

## Phase 2 Protocol Evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Manifest/resource model | Pass | Immutable protocol models and validator tests |
| String/object resource matching | Pass | `CapabilityMatcherTest` |
| Catalog exact matching | Pass | `CapabilityMatcherTest` |
| Disabled provider exclusion | Pass | `CapabilityMatcherTest` |
| Request encoding | Pass | RFC 3986, Unicode, deterministic extras tests |
| HTML/malformed/oversized manifest | Pass | Validator rejects; maximum 512 KiB |
| Protocol Auto tests | Pass | 16 tests, 0 failures |
| Network/SSRF policy | Pass | Auto 68/68 and Mobile 25/25 focused coverage; redirects revalidate DNS and forbidden embedded IPv4 forms are rejected |
| Encrypted source identity | Pass | Immutable UUID, encrypted secret store, tokenized-path redaction and persistence-boundary taint checks pass |
| SQLite schema/lifecycle | Pass | Worker-owned lifecycle, atomic source/index commit, bounded retention and orphan cleanup pass |
| Real HTTP client/cache | Pass | DNS and transport work run off the caller thread; body, redirect, timeout, cache and concurrency bounds pass |
| Source add/edit/remove domain | Pass | Production repository/index/UI wiring and committed-snapshot observation pass |

## Adversarial Review Decisions

- Durable source identity changed from URL/token-derived key to immutable `source_uuid`.
- Provider-local metadata IDs are source-scoped; only recognized canonical namespaces may merge.
- Existing free-form request headers are not accepted as complete `proxyHeaders` support.
- Torrent contracts must live in base/library code and the optional module needs an explicit Gradle
  inclusion flag.
- Database open/DDL must not copy Podcast's synchronous constructor pattern.

## Release Decision

Ready for APK/internal hands-on testing. The known code, build, R8, lintVital and packaging gates
pass, and the second static audit found no unresolved release-blocking defect in the supported
direct-playback scope. Not ready for a public release until the physical-device and DHU rows above
pass and the resulting source/docs are staged together. Direct HTTP/HLS/DASH is the supported
playback contract; torrent, external URL playback, cloud account sync and Calendar are explicit
non-blocking scope limitations rather than partially wired features.

Release artifacts built by the 2026-07-23 gate:

- Universal APK SHA-256: `5630AE3867BE69E17FDF25C9DBBA947D57037D09A427CFFB8DA9C4D3C66C1290`
- AAB SHA-256: `11830010619A36C548F58876C8024034A2E084F5E9F3C5FE7E6A8FD8D5433C34`
