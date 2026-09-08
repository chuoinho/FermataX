# FermataX EQ Single-Column Implementation Report

## Baseline and scope

- Worktree: `E:\Chatgpt\fermata-eq-explicit-apply`
- Branch: `codex/eq-explicit-apply`
- Baseline: `d54a1cfc`
- Required immutable artifact: `fermata/lib/auto/aauto.aar`
- No changes were made to `E:\Chatgpt\fermata`.
- No DSP policy, playback, session-0 effects, WebAudio, YouTube/Stremio,
  MediaSessionCallback, APK, ADB, sideload, merge, or push work was done.

The referenced DHU captures were inspected:

- `C:\Users\ttanh\AppData\Local\Temp\codex-clipboard-281bdb04-3caa-414d-be38-4d8051e25e58.png`
- `C:\Users\ttanh\AppData\Local\Temp\codex-clipboard-070880b3-799c-41e9-82b4-6ae31765ce6c.png`

They showed a truncated right-hand effects column, clipped lower controls,
unreadable button and preset-popup text, and raw `1000%` strength display.

## Changes

- Removed the short-wide two-column branch. The shared native screen is now a
  vertical EQ and effects column on phone and projected hosts.
- Band sizing uses the measured screen-view width after content padding and the
  EQ scale gutter. Phone bands are at least 48 dp wide; projected bands are at
  least 64 dp wide. Four dp gaps are used. Ten bands are shown when the
  measured bank fits; otherwise the existing two five-band banks are used.
- Replaced the verbose visible scale row with a compact drawn `+15`, `0`, and
  `-15` scale. The existing vertical band and horizontal page gesture
  arbitration remains in place.
- Stacked Preamp, Bass Boost, Loudness, and capability-gated Virtualizer rows
  with full labels, value headers, switches where applicable, and sliders below.
  Effect labels no longer ellipsize.
- Kept the master switch as the only EQ enable control. Flat remains the
  preset-based EQ reset. Existing draft, Cancel, explicit Apply/retry, and
  emergency master-off behavior are unchanged.
- Put apply status above Cancel and Apply so action labels remain reachable in
  short viewports.
- Applied stateful theme-derived foreground/background colors to AppCompat
  buttons, bank tabs, and preset popup rows for normal, pressed, selected,
  focused, and disabled states.

## Changed files

- `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsScreenView.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsApplyView.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsScreenLayoutPolicy.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsDisplayUnits.java`
- `fermata/src/main/res/values*/strings.xml` supported locale resources
- `fermata/src/test/java/me/aap/fermata/ui/view/AudioEffectsDisplayUnitsTest.java`
- `fermata/src/test/java/me/aap/fermata/ui/view/AudioEffectsScreenTouchTest.java`
- `fermata/src/test/java/me/aap/fermata/ui/view/EqualizerCurveGeometryTest.java`
- `fermata/src/test/java/me/aap/fermata/media/audio/AudioEffectsProfileArchitectureTest.java`
- `docs/audio/EQ_SINGLE_COLUMN_IMPLEMENTATION_REPORT.md`

## Display and storage units

These are display-only conversions. Stored preferences and backend inputs keep
their existing integer scales.

| Control | Display | Stored preference | Backend mapping |
| --- | --- | --- | --- |
| EQ bands | integer dB, `-15..+15` | integer dB | existing canonical EQ mapping |
| Preamp | integer dB, `-15..0` | integer dB | existing preamp mapping |
| Bass Boost | `raw / 10`, `0..100%` | integer `0..1000` | `BassBoost.setStrength(raw)` |
| Virtualizer strength | `raw / 10`, `0..100%` | integer `0..1000` | `Virtualizer.setStrength(raw)` |
| Loudness | `raw / 10`, `0..100%` relative level | integer `0..1000` | `LoudnessEnhancer.setTargetGain(raw * 10)` mB |

Numeric editors validate the displayed range before converting back. Bass Boost,
Loudness, and Virtualizer input uses explicit relative level percent and rounds
`displayed * 10`. The UI does not claim acoustic dB for Loudness: the native
backend still receives millibels from the unchanged `raw * 10` mapping. For
example, stored raw `180` becomes `1800 mB` and displays `18%`; stored raw
`1000` becomes `10000 mB` and displays `100%`. Tested endpoints and
intermediates include `0`, `320 -> 32%`, `180 -> 18%`, and `1000 -> 100%`,
with round-trips back to the same stored raw values.

## Verification

Post-audit verification for the unit-only follow-up:

- `:fermata:testMobileDebugUnitTest --tests
  "me.aap.fermata.ui.view.AudioEffectsDisplayUnitsTest" --tests
  "me.aap.fermata.media.audio.AudioEffectsProfileArchitectureTest"`:
  BUILD SUCCESSFUL, 19 tests, 0 failures or errors.
- `git diff --check`: clean.
- `Get-FileHash fermata/lib/auto/aauto.aar -Algorithm SHA256`: required hash
  matched.

The broader implementation gates and native view coverage remain recorded from
the prior implementation verification. No new build, APK, device, DHU, or
sideload work was performed for this follow-up, per scope.

The native view tests cover responsive sizing, one-column view-tree layout,
action bounds, nested RecyclerView touch dispatch, vertical band drags, bank
paging, numeric unit round trips, draft Cancel, and preset behavior.

The immutable `aauto.aar` SHA256 must remain:

`99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`

## Acceptance gaps and risks

- Native APK rendering: NOT OBSERVED.
- DHU rendering and actual projected theme on hardware: NOT OBSERVED.
- Physical touch, playback continuity, audible output, and hardware effect
  capability behavior: NOT OBSERVED.
- TalkBack and hardware-D-pad acceptance: NOT OBSERVED. The native view tree,
  content descriptions, focus links, and MotionEvent paths are covered only by
  local tests.
- Exact proposed `360 x 480`, `640 x 300`, `680 x 300`, and fontScale `1.3`
  matrix runs: NOT OBSERVED. Local tests cover `320 x 240`, `360 x 640`,
  `480 x 1080`, `640 x 320`, `800 x 400`, `1024 x 600`, and `2340 x 1080`.
- The proposed 800 x 400 DHU figure is the app area after the AA system bar;
  the editor also shares space with the existing shell top bar, navigation,
  and player bar. This report does not claim that the full 800 x 400 display is
  available to the editor.
- `:fermata:lintMobileDebug :fermata:lintAutoDebug`: FAILED with 3 errors and
  379 warnings, all outside this change: `modules/tv/.../StalkerItemId.java:88`
  (`URLEncoder.encode` API 33), `modules/tv/.../StalkerItemId.java:92`
  (`URLDecoder.decode` API 33), and
  `fermata/.../NativeSessionAudioEffectsBackend.java:266`
  (`WrongConstant`).

Parent-side physical acceptance and release APK work remain required.
