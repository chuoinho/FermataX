# Compact EQ Presets Report

## Status

- Worktree: `E:\Chatgpt\fermata-eq-explicit-apply`
- Baseline: `f78bf30f1fe6e60a023926d5752371c51d55ad2d`
- Implementation commit: `0ef51e557b801c9afe1618b3940496908356c302`
- Branch: `codex/eq-explicit-apply`
- Parent worktree was not changed. No merge or push was performed.
- No ADB, APK build, sideload, or physical playback operation was performed.

## Compact UI Decisions

- `AudioEffectsScreenView` remains content-only; no navigation, shell, player, or
  duplicate chrome was added.
- Equalizer bands are 168dp tall with 48dp phone or 64dp automotive minimum hit
  widths, 4dp gaps, and the existing vertical band drag arbitration.
- The ten-band strip uses two banks of five when the minimum hit-width strip does
  not fit. All ten bands remain visible when it fits. Unused strip space remains
  available to the parent vertical scroll gesture.
- Effects keep the existing Bass Boost, Loudness, and capability-gated
  Virtualizer controls, but their gain controls are compact horizontal rows.
  At 600dp or wider and 360dp high or shorter, the effects column sits beside
  the equalizer bank at 204dp; otherwise it remains below the equalizer.
- Status, Cancel, and Apply use one fixed 56dp bottom action row. Content receives
  bottom padding for that row, and the test matrix covers `360x640`, `640x320`,
  `800x400`, and `1024x600` dp-sized fixtures.
- The master switch remains the only equalizer gate. No DSP, media-session,
  WebAudio, limiter, or session-0 changes were made.

## Preset Table

The canonical order is exactly:

`31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz`

The immutable table contains five conservative Fermata tonal suggestions plus
`Custom`. Values are whole dB and are intentionally modest; the curves are not
scientifically optimal and are not headphone correction profiles.

| Preset | 31 | 62 | 125 | 250 | 500 | 1k | 2k | 4k | 8k | 16k | Max boost |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Flat | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 dB |
| Pop | 2 | 2 | 2 | 1 | 0 | -1 | -1 | 0 | 1 | 1 | 2 dB |
| Rock | 2 | 2 | 1 | -1 | -1 | 0 | 1 | 2 | 2 | 2 | 2 dB |
| Classical | 1 | 1 | 0 | 0 | 0 | 1 | 2 | 2 | 1 | 1 | 2 dB |
| Dance | 3 | 2 | 1 | 0 | 0 | -1 | -1 | 1 | 2 | 2 | 3 dB |

`Custom` has no curve. Manual band edits therefore select `Custom`; a curve
selects a named preset again only when all ten values exactly match its immutable
table entry after reopening or refreshing the screen.

## Draft and Headroom Semantics

- Preset selection edits only the in-memory `AudioEffectsDraft`.
- `Flat` changes only the ten canonical bands and preserves preamp and every
  other effect setting.
- A genre preset changes preamp only as needed to ensure
  `preamp <= -maximumBoost`. An existing more-negative preamp is preserved.
- Cancel/discard restores the committed profile. Apply is the only path that
  saves the complete profile and starts the existing explicit runtime-apply flow.
- No preset preference, preset list, or second persistence authority was added.
  The enum is the source of curve truth; the string-array resource only supplies
  localized display labels.

## Upstream Provenance

Research used the primary VLC repository at the exact revision returned by
`git ls-remote` on 2026-09-08:

- Revision: `a6c467e743f49055282d8b7696c8c401cdff4fcd`
- [VLC equalizer_presets.h at that revision](https://github.com/videolan/vlc/blob/a6c467e743f49055282d8b7696c8c401cdff4fcd/modules/audio_filter/equalizer_presets.h)
- [VLC equalizer.c at that revision](https://github.com/videolan/vlc/blob/a6c467e743f49055282d8b7696c8c401cdff4fcd/modules/audio_filter/equalizer.c)
- Both source headers identify VLC authors and VideoLAN copyright and the GNU
  Lesser General Public License, version 2.1 or any later version (LGPL-2.1-or-later).

The upstream frequency tables are:

- VLC table: `60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000 Hz`
- ISO table: `31.25, 62.5, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz`

VLC's `equalizer.c` selects either table for coefficient calculation. Fermata
uses its existing canonical integer order, which is the ISO-shaped order rounded
to the named integer frequencies above. The Fermata curves were authored as
conservative suggestions informed by the source's broad tonal shapes; no VLC
curve values were copied by index. This avoids assigning a VLC value for 60 or
170 Hz to Fermata's 31 or 62 Hz bands, for example.

## Verification

Exact full command:

```text
.\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest --no-daemon --console=plain
BUILD SUCCESSFUL in 29s
176 actionable tasks: 6 executed, 170 up-to-date
```

Result files for the Fermata task report 944 tests, 0 failures, 0 errors, and 2
skipped across 186 test suites. The web test task completed successfully; this
module emitted no XML test result files in its `build/test-results` directory in
this run.

Focused affected results:

- `AudioEffectsPresetTest`: 6 tests, 0 failures, 0 errors, 0 skipped.
- `AudioEffectsDraftTest`: 11 tests, 0 failures, 0 errors, 0 skipped.
- `AudioEffectsScreenTouchTest`: 5 tests, 0 failures, 0 errors, 0 skipped.
- `EqualizerCurveGeometryTest`: 7 tests, 0 failures, 0 errors, 0 skipped.

Additional checks:

- `git diff --check`: passed with no whitespace errors before the implementation
  commit; the staged diff check also passed.
- `fermata/lib/auto/aauto.aar` SHA-256 before and after: `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.
- The baseline Git blob and final worktree AAR both have Git object ID
  `002864aeca274e945507b14961fcb71c44863894`.

## Concerns and Missing Evidence

- No physical-device layout, touch, audio, Android Auto, or audible acceptance
  evidence is available. The parent should build and perform the requested
  physical review after inspecting this commit.
- Unit tests verify policy boundaries, immutable curves, canonical cardinality
  and ranges, preamp headroom, draft isolation, Cancel, explicit Apply, and the
  compact touch/layout matrix. They do not prove localized text width on every
  device or actual hardware effect behavior.
- The report records the implementation commit; the report itself is added in a
  separate local documentation commit after this implementation commit.
