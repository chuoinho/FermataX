# Audio EQ Release Regression

## Status

`AUDIO_EQ_RELEASE_REGRESSION_PASS = YES`.

This is a targeted pre-release regression run after the post-final hardening
commits. It exercised the changed UI, persistence, migration-boundary and
native-session paths deeply. It did not repeat the completed WebEQ discovery
matrices. No production or test source was changed in this run.

## Tested HEAD

| Item | Value |
| --- | --- |
| Worktree | `E:\\Chatgpt\\fermata-eq2` |
| Branch | `codex/eq2-native-session-backend` |
| HEAD | `fe6f2260a8cc74ab41c64ff6c92ff7cf9499a437` |
| Hardening commits | `f954441a`, `fe6f2260` |
| Initial tracked worktree | Clean; only the preserved untracked `.native-eq-a-temp/` and `.webeq-b-temp/` evidence directories existed. |
| Production/test LOC in this regression | `0 / 0` |

## APK / Device

| Item | Value |
| --- | --- |
| Device | `15c36230` (Redmi Note 8, Android 16 / API 36) |
| Package | `me.app.fermataX.auto.test` |
| Version | `2.0.1 (304)` |
| Candidate APK | `fermata/build/outputs/apk_from_bundle/autoRelease/fermata-2.0.1-me.app.fermataX.auto.test-auto-release-universal.apk` |
| APK SHA-256 | `5BA30A98A73BC855774B44DB7888A8A516975CF329F13957F592A083CD8B9EC5` |
| Installation | Same candidate update-installed with `adb install -r`: `Success`. |

The candidate was not rebuilt during this regression.

## Automated Gate

Command:

```text
.\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest --no-daemon --console=plain
```

The command completed successfully with zero failures or errors. The retained
full-suite result at this exact source state is Fermata `880` tests, `0`
failures, `0` errors, `2` skipped; Web `217` tests, `0` failures, `0` errors,
`0` skipped. A focused current run of
`ArchitectureBoundaryTest` also passed (`8` tests, `0` failures/errors).

The architecture checks continue to enforce one unified profile authority and
the absence of `AudioEffectsFragment`, `AudioEffectsView`, legacy
`engine.AudioEffects`, `AudioEffectsLegacyApplier`, session-0 `Equalizer`, and
session-0 `DynamicsProcessing` runtime paths. `git diff --check` is clean.

## EQ UI / Curve

Physical observations on the signed candidate:

- With Master on and EQ off, the stored curve was present in its muted state;
  numeric EQ bands were hidden as intended.
- Enabling EQ once made the 10 bands appear immediately, from `31 Hz` through
  `16 kHz`, without leaving or reopening the page.
- `31 Hz = -6` updated the curve immediately.
- Turning EQ off hid the numeric bands without losing the stored curve.
- Master off/on retained the profile and restored the regular settings rows.

This confirms the `PreferenceView` recycled-height regression fixed in
`f954441a` did not return.

**Result: `EQ_UI_REGRESSION_PASS`.**

## Persistence

Two independent persisted values were checked on the real device:

| Operation | Observation |
| --- | --- |
| `31 Hz = -6`, EQ off, force-stop, relaunch | EQ remained off; the stored `-6` value remained available after reopening EQ. |
| `1 kHz = -6`, leave/reopen, force-stop, relaunch | The value remained `-6` after both navigation and process recreation. |
| Final cleanup | Both tested bands were restored to `0`; Master remained on and EQ remained off. |

**Result: `EQ_PERSISTENCE = PASS`.**

## Legacy Migration

The current device contains only the unified `.test` package and no isolated
legacy build/state that still stores old manual bands or an Android system
preset. Creating synthetic preferences, clearing application data, or treating
unit data as a physical upgrade would not be a valid migration test, so neither
was done.

| Gate | Result | Evidence |
| --- | --- | --- |
| M1: genuine legacy manual-band update | `NOT_OBSERVED` | No eligible legacy application state was available. |
| M2: genuine Android system-preset update | `NOT_OBSERVED` | No eligible legacy environment/preset state was available. |
| Resolver/fallback correctness | `PASS (automated)` | Current suite covers successful resolver mapping, unavailable/throwing/invalid resolvers, preserved legacy state, and exactly-once localized fallback notice. |

No silent-flat physical migration is claimed. The release status is therefore
`LEGACY_MIGRATION = PHYSICAL_PARTIAL`, not a fabricated physical PASS.

## Native Phone

The known-good native Radio source `Classic Vinyl HD` created a real Fermata
`PLAYING` MediaSession while the candidate was installed. With playback active,
`1 kHz` changed `0 -> -6 -> 0`; the MediaSession stayed `PLAYING` and the
post-edit log contained no Fermata crash or AudioEffect exception. AudioFlinger
showed a Fermata-owned non-zero effect chain, including an Equalizer, and no
session-0 binding was observed.

Bass Boost was toggled and restored through its normal profile UI; Loudness was
toggled on then restored off. The Android 16 device does not expose the legacy
native Virtualizer backend, so a real Virtualizer effect toggle is
`NOT_APPLICABLE` for this route rather than a failed capability.

One radio initialization logged platform `AudioFlinger` capacity errors while
other system effect chains were registered. It did not crash Fermata, stop the
MediaSession, recur during the `1 kHz` edit, or establish a hardening-caused
regression. It is recorded as an environment capability warning only.

**Result: `NATIVE_PHONE_EQ_REGRESSION_PASS`.**

## Native AA

The DHU process and Android Auto companion were present during this run, but a
fresh controlled Remote Submix `INITIAL_ONLY` backend was not recreated. This
run therefore does not claim a new physical deferred-apply observation.

The accepted physical AA4 evidence remains applicable because these hardening
commits did not change the `INITIAL_ONLY` controller/backend policy:

- active edit saves and marks pending without live DP mutation;
- a fresh backend consumes the latest profile and clears pending;
- disconnect/reconnect keeps that lifecycle safe.

Its direct observations are recorded in
`NATIVE_EQ_A_FINAL_ACCEPTANCE_REPORT.md` under AA4. Current result:
`NATIVE_AA_INITIAL_ONLY = HISTORICAL_PASS_CURRENT_SMOKE_NOT_OBSERVED`.

## YouTube Phone

The YouTube addon was opened successfully on the signed candidate and initiated
its normal WebView load; no renderer crash, `AudioContext` error, or duplicate
MediaElementSource exception appeared in the bounded device log. A stable
eligible media item was not available within this short sanity window, so no
new `SUPPORTED_ACTIVE` claim or band-change assertion is made.

**Result: `YOUTUBE_PHONE_SANITY = NOT_OBSERVED`.** Historical B8R acceptance
remains unchanged because the hardening diff did not modify WebAudio or YouTube
policy.

## YouTube DHU

No controlled projected BLOB/MSE item was available in this run. The historical
B7/B8R projected-host evidence remains valid and is not rediscovered here.

**Result: `YOUTUBE_DHU_SANITY = NOT_OBSERVED`.**

## Stremio

No known supported blob/MSE non-EME Stremio source was used. No Stremio source,
WebAudio policy, streaming behavior, or addon code changed in the hardening
commits.

**Result: `STREMIO_SANITY = NOT_OBSERVED`.**

## I18n / Resources

The candidate rendered the English `Audio & Equalizer`, `Equalizer`, and
`Preamp` strings on device. Static resource verification confirmed the same
required keys in Vietnamese:

- `Âm thanh & bộ chỉnh âm`
- `Bộ cân bằng`
- `Tiền khuếch đại`
- the localized legacy-preset migration notice

No production `equalier` spelling remains; occurrences are only negative test
assertions. All shipped locale directories are covered by the architecture
resource test.

**Result: `I18N_RESOURCE = PASS`.**

## aauto Hash

```text
fermata/lib/auto/aauto.aar
SHA-256 99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B
```

It matches the required immutable hash exactly.

**Result: `AAUTO = UNCHANGED`.**

## Regression Matrix

| Area | Test depth | Result |
| --- | --- | --- |
| Automated tests | Full | `PASS` |
| Architecture boundary | Focused | `PASS` |
| EQ UI / dynamic rows | Full physical | `PASS` |
| Curve recycle regression | Full physical | `PASS` |
| Profile persistence | Full physical | `PASS` |
| Manual legacy migration | Genuine upgrade unavailable | `NOT_OBSERVED` |
| Native preset migration | Genuine upgrade unavailable | `NOT_OBSERVED` |
| Native phone | Targeted physical | `PASS` |
| Native AA INITIAL_ONLY | Historical physical gate retained; current route not recreated | `HISTORICAL_PASS_CURRENT_SMOKE_NOT_OBSERVED` |
| YouTube phone | Light sanity | `NOT_OBSERVED` |
| YouTube DHU | Light sanity | `NOT_OBSERVED` |
| Stremio | Light sanity | `NOT_OBSERVED` |
| I18n/resources | Targeted | `PASS` |
| `aauto.aar` | Full hash gate | `UNCHANGED` |

## New Issues

No release-blocking regression was found.

The sole notable runtime observation was a transient platform effect-capacity
warning during one radio initialization. It was non-fatal, did not interrupt
playback, did not recur on the subsequent EQ update, and is not evidence that
the hardening changed the native session contract.

## Final Verdict

```text
EQ_UI_REGRESSION = PASS
EQ_PERSISTENCE = PASS
LEGACY_MIGRATION = PHYSICAL_PARTIAL
NATIVE_PHONE_EQ = PASS
NATIVE_AA_INITIAL_ONLY = HISTORICAL_PASS_CURRENT_SMOKE_NOT_OBSERVED
YOUTUBE_PHONE_SANITY = NOT_OBSERVED
YOUTUBE_DHU_SANITY = NOT_OBSERVED
STREMIO_SANITY = NOT_OBSERVED
I18N_RESOURCE = PASS
AAUTO = UNCHANGED

AUDIO_EQ_RELEASE_REGRESSION_PASS = YES
READY_FOR_PRODUCTION_BUILD = YES
```

Optional not-observed sanity surfaces are not release blockers: their policies
were outside the hardening diff and their prior physical acceptance evidence is
retained. A future migration test must begin from an actual legacy installation
without clearing its stored profile.
