# Phase 0B Report: Hosted Web Contract Feasibility

## Goal And Scope

Verify that the exact hosted Stremio Web model can provide the navigation and direct-playback
contracts required before any production migration. This phase did not modify production code.

## Baseline And Final HEAD

- Baseline HEAD: `f56fb1a4dc581d2f6500a8b5f4b3332aa7e1eef9`
- Final HEAD: documentation update pending commit
- Legacy `modules/stremio` remains registered and untouched.

## Files Added

- `docs/stremio/web-only/WEB_CONTRACT_BASELINE.md`
- `docs/stremio/web-only/reports/PHASE_0B_REPORT.md`

## Evidence And Test Results

On Redmi Note 8 / Android 16 with System WebView `145.0.7632.109`, the hosted origin rendered in
the existing Fermata Web Browser. Opening a catalog item produced the observed canonical route:

```text
https://web.stremio.com/#/detail/movie/tt36590417/tt36590417
```

The same live session initially displayed `Streaming server is not available.`. A disposable,
unpackaged stream-server test process on the development host was connected through
`adb reverse tcp:11470 tcp:11470`; after Stremio Web received its own bootstrap endpoint parameter,
the unavailable banner disappeared. This proves the external-server readiness boundary, not
playback handoff.

The upstream pinned source confirms why this is material rather than a test-data gap:

- External-player links are read from `deepLinks.externalPlayer`, a Core result.
- Direct HTTP entry still calls `core.transport.encodeStream(...)`.
- Magnet playback explicitly requires `useStreamingServer().settings.type === 'Ready'`.

## Ownership, Security, And Lifecycle Audit

The canonical detail route can remain the only Fermata-owned Stremio datum. Account, addons,
stream selection, deep links, and streaming-server state remain owned by Stremio Web/Core.

No capability, URL, cookie, token, manifest data, or magnet data was persisted, logged, or
introduced into the app. Existing renderer recovery was inspected but not exercised by crashing a
physical WebView renderer; it cannot be marked PASS.

Phone, Android Auto, DHU, APK content, native libraries, and legacy behavior are unchanged.

## Rollback

Revert the documentation-only commit that records this phase. No user data or runtime migration
exists to roll back.

## Known Limitation And Required Decision

The supplied Web-only architecture requires a valid external Stremio Core/streaming-server
transport to emit an HTTP(S) external-player deep link. That dependency is not included in the
final Fermata APK, and the plan forbids building or embedding a replacement.

An approved external test environment must be supplied and proven on device for all of the
following before Phase 1 starts:

1. direct MP4 and HLS `deepLinks.externalPlayer` callbacks;
2. safe no-server magnet behavior; and
3. client-subtype renderer recovery.

## Authenticated Retest (2026-08-26)

The test profile was authenticated in the hosted Stremio Web UI on the physical device. Its
Player setting was changed through the Android UI from `Disabled` to `Allow choosing`, and the
persisted selected value was observed after navigation. This closes the login/configuration
sub-gate without collecting account or credential material.

The direct-MP4 fixture was attempted only via Stremio Web's visible `Search or paste link` input.
The Android keyboard opened, but shell text injection could not enter data into the cloned display;
the main activity subsequently produced an Android ANR and recovered after the system `Wait`
action. There was no observed WebView external navigation, chooser, or callback URI. HLS was not
attempted after this failure because the same input path is required.

The new observed ANR means renderer/client recovery remains not observed and the phase cannot
advance. It does not change production code or establish a direct-playback implementation path.

## Exit Gate

**PARTIAL / BLOCKED.** Hosted login and external-player preference persistence now pass, but the
required direct URL handoff has not been observed. The real input-path ANR is an additional
physical-device blocker. Per the implementation plan, phases 1 through 8 must not begin.
