# Unified Audio & Equalizer Final Acceptance

## Final Status

`UNIFIED_AUDIO_EQUALIZER_FINAL_PASS`

FINAL-5 closes the unified EQ UI without changing accepted native DSP, YouTube
WebAudio, or Stremio policy. Runtime evidence and inherited accepted evidence
are distinguished below; no runtime PASS is inferred from source alone.

## Baseline / Final HEAD

| Item | Value |
| --- | --- |
| Worktree | `E:\\Chatgpt\\fermata-eq2` |
| Starting FINAL-5 HEAD | `f25fa47b` |
| Final source/test HEAD | `a3b9fbce` |
| Device | `15c36230`, Android 16 / API 36 |
| Package | `me.app.fermataX.auto.test`, `2.0.1 (304)` |

Known untracked evidence directories remain excluded: `.native-eq-a-temp/` and
`.webeq-b-temp/`. The user-owned `tcp:5277 -> tcp:5277` forward was preserved.

## Step 1 - Documentation Closure

`WEBEQ_B_YOUTUBE_REPORT.md` now has authoritative status
`WEBEQ_B_YOUTUBE_PASS`. Incomplete B2-B8 records are retained as historical
evidence and explicitly superseded by B7/B8R where applicable. The projected
BLOB/MSE, A -> B -> A, and fullscreen-causality gates are no longer open.
`YOUTUBE_FULLSCREEN_EXISTING_OR_UPSTREAM_ISSUE` remains not caused by WebAudio
and is not a WEBEQ-B blocker.

Commit: `15bfa4bd docs(audio): close YouTube WebEQ acceptance status`.

## Step 2 - EQ UI Audit

| File | Type | Purpose | Decision |
| --- | --- | --- | --- |
| `depends/utils/src/main/java/me/aap/utils/misc/ChangeableCondition.java` | Production | Deep-copy both operands of a composed condition. | Keep |
| `depends/utils/src/main/java/me/aap/utils/pref/PreferenceSet.java` | Production | Allow runtime visibility changes to relayout. | Keep |
| `fermata/src/test/java/me/aap/fermata/ui/fragment/DynamicPreferenceVisibilityTest.java` | Test | Cover both dynamic-row invariants. | Keep |
| `.native-eq-a-temp/`, `.webeq-b-temp/` | Evidence | Device and WebEQ evidence. | Exclude |

Root cause: each EQ row uses a copied visibility condition. The old composed
`copy()` reused one operand/listener, so later rows replaced earlier row
listeners. The list was also incorrectly fixed-size while visibility changed.
The combined result was one row after Enable, with the rest only after leaving
and reopening the page.

The fix deep-copies both operands and sets the preference RecyclerView to
non-fixed-size. It is a generic ownership-correct utility fix, not an EQ-only
branch. On the signed device build: with Master enabled and EQ disabled only
`Equalizer -> Enable` was shown; a single ordinary Enable tap immediately
rendered the complete 31 Hz through 16 kHz list.

The one global authority remains:

```text
AudioEffectsPrefsBuilder -> AudioEffectsProfileRepository
                         -> AudioEffectsController -> active backend
```

The UI exposes Master, Equalizer, preamp, ten canonical bands, Bass Boost,
Loudness, and Virtualizer under the existing backend contracts. Master off
bypasses; EQ off neutralizes without erasing the curve; Master plus EQ on
applies the stored curve. Preamp stays canonical and non-positive.

The numeric editor uses signed integer input and locale-neutral
`Integer.parseInt`; preamp range is `-15..0`, band range is `-15..15`.
Direct touch set `1 kHz = -6`, which persisted through leave/re-enter and
force-stop/relaunch, then was restored to `0`. No behavior relies on ADB input.

Commit: `a3b9fbce feat(audio): finalize unified audio and equalizer UI`.
Production LOC: `+4/-3`; test LOC: `+44`.

## Step 3 - Cross-Backend Physical Matrix

| Backend | Expected behavior | Physical result | Verdict |
| --- | --- | --- | --- |
| Native phone | Profile edit does not stop playback. | Signed release launched; native radio MediaSession was `PLAYING` during UI regression check, with no effect/fatal error. | `NATIVE_PHONE_EQ_REGRESSION_PASS` |
| Native AA | `INITIAL_ONLY`: save now, apply on next backend. | Existing accepted AA physical evidence remains valid. Current DHU preflight did not create projection. | `PASS_WITH_INITIAL_ONLY_ROUTE_CAPABILITY` |
| YouTube phone | Existing scoped playback stays intact. | Normal visible YouTube playback worked after release install; no renderer/WebAudio/fatal event in bounded audit. B8R remains controlled evidence. | `YOUTUBE_PHONE_WEBAUDIO_REGRESSION_PASS` |
| YouTube AA/DHU | Existing BLOB/MSE non-EME route stays intact. | B7 remains accepted projected evidence. Current DHU executable started, but device Head Unit Server was inactive. | Historical `PASS`; current smoke `NOT_OBSERVED` |
| Stremio | Supported blob/MSE non-EME scope only. | No suitable physical source used in FINAL-5; policy was untouched. | `STREMIO_SUPPORTED_PATH_NOT_OBSERVED` |

The current DHU limitation is environment-only: `desktop-head-unit.exe` and
the preserved forward were live, but no Head Unit Server/projection session was
started on the device. It did not reproduce a product regression and required
no source patch.

## Step 4 - Validation

Fresh command:

```text
.\\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest --no-daemon --console=plain
```

Results: `1,093` tests, `0` failures, `0` errors, `2` skipped. Focused
`DynamicPreferenceVisibilityTest` also passed. `ArchitectureBoundaryTest`:
`8` tests, `0` failures, `0` errors. `git diff --check` passed.

Fresh nonblank hotspot counts:

```text
YoutubeWebView       1246 / ceiling 1248
YoutubeMediaEngine   1121 / ceiling 1155
MediaSessionCallback 2182 / ceiling 2276
MainActivityDelegate 1142 / ceiling 1291
ControlPanelView      758 / ceiling 956
```

The change adds no generic WebAudio injection, URL/media extraction,
cookies/headers/tokens, DRM/EME bypass, session-0 DSP, forced restart, or
Stremio/YouTube policy expansion.

```text
fermata/lib/auto/aauto.aar
SHA-256 99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B
```

`aauto.aar` is unchanged.

## Step 5 - Release Acceptance

Final-source release packaging completed for the existing internal-test
identity. The APK update-installed successfully on `15c36230`; launch,
Settings -> Playback settings -> Audio & Equalizer, native radio, and normal
YouTube were smoke-tested.

| Artifact | Value |
| --- | --- |
| Universal APK | `fermata/build/outputs/apk_from_bundle/autoRelease/fermata-2.0.1-me.app.fermataX.auto.test-auto-release-universal.apk` |
| AAB | `fermata/build/outputs/bundle/autoRelease/fermata-2.0.1-me.app.fermataX.auto.test-auto-release.aab` |
| Package/version | `me.app.fermataX.auto.test`, `2.0.1 (304)` |
| APK SHA-256 | `9B184057F843839B1A9D812788C2C0C17AA81A402C9B46F65CABED6465FC38C4` |
| AAB SHA-256 | `07096C7B67B711E0E51A5BFCC7A9E1EC51977587E8DE9E8EAA8891D135069BD6` |
| APK signature | One signer; v3 verified. This generated APK has no v2 signature. |
| AAB signature | `jarsigner -verify` exit `0`. |

## Final Capability Matrix

| Surface | Backend | Status | Limitation |
| --- | --- | --- | --- |
| Native phone | Android native DSP | `PASS` | Route capability dependent. |
| Native AA tested route | DP/AA policy | `PASS_WITH_INITIAL_ONLY_ROUTE_CAPABILITY` | Applies at next fresh backend. |
| YouTube phone | Scoped WebAudio | `PASS` | BLOB/MSE, non-EME only. |
| YouTube AA/DHU | Scoped WebAudio | `PASS` | BLOB/MSE, non-EME; B7 evidence. |
| YouTube A -> B -> A | Scoped WebAudio | `PASS` | B8R direct-touch evidence. |
| Fullscreen causality | Scoped WebAudio | `NOT_WEBAUDIO_CAUSED` | Separate existing/upstream issue. |
| Stremio supported path | Scoped WebAudio | `NOT_OBSERVED` in FINAL-5 | Existing scope unchanged. |
| Direct HTTP(S), EME/DRM, iframe/unknown | WebAudio | `BYPASS` | Fail-closed. |

## Known Intentional Limitations

- The tested AA Remote Submix route is `INITIAL_ONLY`: edits save immediately
  and apply on the next fresh backend without interrupting active playback.
- WebAudio deliberately excludes direct HTTP(S), EME/DRM, iframe, unknown or
  ambiguous media, and non-preferred hosts.
- Stremio was not broadened and no unsupported source class is claimed.

## Audit Round 1 - Functional

One global profile remains authoritative. The dynamic-row regression is tested
and physically observed. Native playback continuity was observed; accepted AA
deferred semantics and YouTube B7/B8R evidence remain valid; Stremio behavior
is unchanged.

## Audit Round 2 - Architecture / Safety

The final source change is minimal. No hotspot grew, no session-0 binding,
generic injection, DRM bypass, sensitive WebView data flow, duplicate owner,
or immutable artifact mutation was found.

## Audit Round 3 - Release / Evidence

Current physical observations are separated from inherited accepted B7/B8R
evidence. Tests, architecture boundary, packaging, signatures, hashes, and
update install passed. Temporary evidence remains untracked.

## Final Verdict

```text
NATIVE_EQ_PHONE = PASS
NATIVE_EQ_AA = PASS_WITH_INITIAL_ONLY_ROUTE_CAPABILITY
YOUTUBE_WEBAUDIO_PHONE = PASS
YOUTUBE_WEBAUDIO_DHU_BLOB_MSE = PASS
YOUTUBE_A_B_A_LIFECYCLE = PASS
YOUTUBE_FULLSCREEN_WEBAUDIO_CAUSALITY = NOT_WEBAUDIO_CAUSED
WEBEQ_B_YOUTUBE = PASS
STREMIO_WEBAUDIO = NOT_OBSERVED in FINAL-5; scope unchanged
EQ_UI = PASS
AAUTO = UNCHANGED
ARCHITECTURE = PASS

UNIFIED_AUDIO_EQUALIZER_FINAL_PASS = YES
READY_TO_MERGE = YES
READY_FOR_RELEASE = YES
```

No source-code blocker remains. The only new observation gap is a DHU session
without active device Head Unit Server; it does not supersede accepted B7/B8R
projected-device evidence and is not a release blocker.
