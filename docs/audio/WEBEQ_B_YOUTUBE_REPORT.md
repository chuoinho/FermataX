# WEBEQ-B YouTube WebAudio Bridge

## Status

`IMPLEMENTED_WITH_PARTIAL_PHYSICAL_ACCEPTANCE`

This change adds a narrow, YouTube-only WebAudio equalizer path.  It is not a
generic WebView hook and it does not alter native-player EQ ownership.  The
physical device proved the supported MSE path, live profile propagation, and
safe neutralisation.  A clean full-screen/back and explicit A-to-B selection
run was not obtained in this checkpoint, so neither is represented as a pass.

## Scope and ownership

```text
YoutubeWebView
  -> YoutubeWebAudioBridge
      -> exact-origin document-start shim
          -> one MediaElementAudioSourceNode
          -> preamp -> ten canonical peaking filters -> destination
```

- `YoutubeWebView` owns its existing navigation and destruction boundaries.
- `YoutubeWebAudioBridge` owns a document generation, bounded profile updates,
  and best-effort document teardown.
- The document shim owns only one local `AudioContext` and graph per claimed
  content `HTMLVideoElement`.
- `AudioEffectsProfileRepository` remains the sole profile authority.  The
  bridge receives only master/EQ flags, ten bounded band gains, and a bounded
  non-positive preamp.

No JavaScript interface was added.  The bridge does not send URLs, media
addresses, headers, cookies, credentials, account state, tokens, samples, or
player DOM data to Java or logging.

## Attachment policy

The graph may attach only when all of the following are true:

- the top-level document is exactly `https://m.youtube.com` or
  `https://www.youtube.com`, with default HTTPS port;
- the existing YouTube playback host is preferred;
- the existing content-video selector returns a connected, playing,
  ready-to-play video;
- the source is `blob:`/MSE; and
- the media has no `mediaKeys` and has not emitted `encrypted` or
  `webkitneedkey`.

Direct HTTP/HTTPS sources, unknown sources, iframes, EME/DRM, paused
placeholders, and non-YouTube origins bypass the graph.  Critically, when both
Master and Equalizer are off, a newly discovered media element is not claimed.
Once a graph was validly claimed, Master Off or Equalizer Off changes it to
unity rather than making a second source claim possible.

## Physical evidence

Environment:

- device `15c36230`, Android 16 / API 36;
- signed package `me.app.fermataX.auto`;
- public YouTube MSE playback;
- temporary screenshots and bounded CDP output are untracked under
  `.webeq-b-temp/`.

The CDP query read only the shim's bounded status object:

```text
{ r, m, e, p, b }
```

It contains no content identity or media data.  `b` is the live 1 kHz filter
gain.  The following observations were made through the normal FermataX UI:

| Check | Observed result | Status |
| --- | --- | --- |
| Capability | Current content was `BLOB_MSE`, non-EME, its graph was attached, `AudioContext` was running, playback advanced, and an earlier controlled read-back showed 1 kHz `0 -> -10 -> 0`. | PASS |
| Profile propagation | With Master and EQ enabled, a normal Equalizer edit updated a live graph to 1 kHz `-6`; the graph did not need a restart. | PASS |
| Equalizer Off | A live claimed graph reported `SUPPORTED_ACTIVE`, `m=true`, `e=false`, `b=0`. | PASS |
| Master Off | A live claimed graph reported `SUPPORTED_ACTIVE`, `m=false`, `e=false`, `b=0`; the UI checkbox was visibly off. | PASS |
| No-claim gate | With Master and EQ disabled before attachment, the bridge reported `NO_MEDIA`, `m=false`, `e=false`, `b=null`. | PASS |
| Media-session pause/resume | A real media key changed FermataMediaService from `PLAYING` to `PAUSED` and back to `PLAYING`; the bridge remained neutral and attached. | PASS |
| Leave/re-enter YouTube | Normal Dashboard -> YouTube navigation retained a safe neutral bridge state and did not crash or expose data. | PASS |
| Fullscreen then Back | The visible page temporarily became blank and the session stopped. Refresh subsequently returned visible YouTube content, but this did not provide a stable transition proof. No attribution to the bridge is made from this evidence. | NOT OBSERVED |
| Explicit content A -> B | A second distinct user-selected playback transition was not reliably produced by the current YouTube surface. | NOT OBSERVED |
| Direct HTTP/HTTPS, EME, iframe bypass | Covered by focused unit policy tests only in this checkpoint. | UNIT PASS / PHYSICAL NOT OBSERVED |
| Android Auto/DHU | Not run for this YouTube-specific checkpoint. | NOT OBSERVED |

The device was left with Master enabled and Equalizer disabled, yielding an
effective unity graph.  A temporary curve used for propagation was therefore
not left audibly active.

## Automated and immutable-artifact validation

Completed successfully on this worktree:

```text
.\gradlew.bat :fermata:testAutoDebugUnitTest :web:testAutoDebugUnitTest --no-daemon --console=plain
```

`git diff --check` passed before this report was added.  The approved immutable
Auto archive was rechecked:

```text
fermata/lib/auto/aauto.aar
SHA-256 99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B
```

A signed universal release was also rebuilt and verified:

```text
fermata/build/outputs/apk_from_bundle/autoRelease/
  fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk
SHA-256 8708274379590F1E820657017AF1ABF6F239666FD17A8CF3F5F8E24DCB9BB04D
APK Signature Scheme v2: true
APK Signature Scheme v3: true
```

Focused tests cover exact origins and port rejection, bounded profile
projection, invalid profile fallback, single source claim, generation-bound
update/teardown, direct-source bypass, and EME rejection.  The temporary
capability probe used before production implementation was removed; no probe
identifier remains in tracked source.

## Change inventory

Production:

- `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeWebView.java`
- `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeWebAudioBridge.java`
- `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeWebAudioCandidatePolicy.java`
- `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeWebAudioProfile.java`

Tests:

- `YoutubeWebAudioBridgeTest.java`
- `YoutubeWebAudioCandidatePolicyTest.java`
- `YoutubeWebAudioProfileTest.java`

No Stremio, generic browser, native player, MediaSession ownership,
`aauto.aar`, manifest, or Auto/DHU code is changed.

## Remaining acceptance work

1. Re-run full-screen -> Back against a stable public YouTube video and record
   the player and FermataMediaService state before and after each transition.
2. Run a controlled explicit A -> B selection, then an A -> B -> A sequence,
   proving that a fresh content element is handled without stale graph reuse.
3. On an available Auto/DHU host, repeat Master/EQ neutralisation and the
   control/lifecycle checks.  Do not infer those results from phone evidence.
4. If an EME or direct-source physical fixture becomes available through a
   normal YouTube session, verify bypass without collecting the media address.

## Cleanup

- No fixture server, reverse mapping, addon, or account mutation was used.
- The only temporary evidence is the untracked workspace directory
  `.webeq-b-temp/`; it is not included in either commit.
- The temporary CDP forward must be removed after the runtime session is no
  longer needed.

## WEBEQ-B2 YouTube Lifecycle & DHU Closure

### Scope and baseline

This acceptance pass used the signed release package on physical device
`15c36230` at commit `70db79ad`. It was observation-first: production and test
source changes are zero. The only newly created runtime evidence is untracked
under `.webeq-b-temp/b2/`; it contains screenshots and bounded bridge results,
not URLs, media identities, account information, cookies, headers, or samples.

The bridge boundary remained the existing bounded status object `{ r, m, e, p,
b }`. No player DOM, stream address, or WebView content was inspected.

### Fullscreen -> Back

Two physical comparisons were run against ordinary non-EME YouTube playback:

| Run | Starting state | Observation after one Android Back | Result |
| --- | --- | --- | --- |
| Bridge-active | `SUPPORTED_ACTIVE`, Master on, EQ off/unity, MediaSession playing | The normal YouTube page remained visible, but playback/session stopped and the page presentation was not a clean continuation. No blank page was observed in this run. | Not a clean pass. |
| No-claim control | Fresh app process; Master and EQ were both off before starting an eligible video; `NO_MEDIA`, MediaSession playing | Playback started normally with no graph. Back also left the expected player presentation and returned to Dashboard rather than a stable watch surface. | Reproduced without a graph. |

The no-claim control proves that the observed Back/presentation instability is
not caused by a claimed WebAudio graph. This checkpoint therefore makes no
production change to WEBEQ-B. It does not establish a clean fullscreen/back
pass and does not reproduce the earlier blank page.

**Verdict:** `YOUTUBE_FULLSCREEN_EXISTING_OR_UPSTREAM_ISSUE` for the observed
navigation/presentation behavior; `WEBEQ-B` is not attributed as its cause.

### Explicit A -> B -> A

An explicit A -> B selection was made through a normal recommended-content
tile. B reached playback presentation and no filtered console/logcat evidence
reported `InvalidStateError`, `MediaElementSource`, duplicate-source, or
`AudioContext` failures. A separate active A run also showed
`SUPPORTED_ACTIVE` with the retained controlled 1 kHz value of `-6`.

The full A -> B -> A proof was not obtained. After Back, the current YouTube
surface returned to Dashboard instead of preserving a stable user-selectable
watch surface, so returning explicitly from B to A would not be the required
continuous content-replacement experiment. Some selections also began in a
previous no-claim document, which correctly remained `NO_MEDIA` until a new
eligible active playback was started. That is not evidence of a duplicate
claim or a graph lifecycle defect.

**Verdict:** `YOUTUBE_A_B_A_LIFECYCLE_PARTIAL`. No WebAudio-specific
regression was reproduced, but the mandatory uninterrupted A -> B -> A
acceptance chain remains unobserved.

### DHU / Android Auto

DHU was launched through the repository's existing `open-dhu.bat` workflow
with the 1280x720 preset. This created the B2-owned forward
`tcp:5277 -> tcp:5277`; no reverse mapping was created. The DHU process was
responsive and Android Auto's `GhostActivity` appeared on the physical device.

Passive bounded CDP observation found two YouTube WebView documents while
projection was connected. Exactly one reported `SUPPORTED_ACTIVE`; the other
reported `NO_MEDIA`. That supports the single-active-graph ownership invariant
for this snapshot, but it does not identify either document or substitute for
visible DHU interaction.

The B2-created DHU process was stopped and restarted. The phone returned to
the FermataX activity on disconnect and Android Auto `GhostActivity` returned
after reconnect. The inactive document did not claim a second graph. The
available automation surface could not expose the DHU window controls, so the
following were **not observed** and are not claimed as PASS: visual FermataX
launch in DHU, live `0 -> -10 -> 0` DHU EQ update, DHU pause/resume controls,
or a visible DHU fullscreen/back flow.

**Verdict:** `WEBEQ_B_YOUTUBE_DHU_PARTIAL_PHYSICAL_ACCEPTANCE`.

### Direct / EME / iframe

No suitable direct, EME, or iframe source appeared naturally in this pass.
Existing policy unit coverage remains `UNIT PASS / PHYSICAL NOT OBSERVED`.

### Audit rounds

1. **Fullscreen/navigation:** no blank page in the new active run; the failed
   presentation order also happened in the no-claim control, so no WebAudio
   causality was assigned.
2. **Media-element lifecycle:** one active graph was observed when eligible;
   no duplicate-source exception was found in the bounded filtered logs. The
   continuous A -> B -> A path remains incomplete.
3. **DHU/scope:** one active graph across the two observed documents; no native
   AA EQ, Stremio, generic browser, `aauto.aar`, or hotspot source changed.

### Validation and artifact state

- Production LOC: `0`; test LOC: `0`.
- Hotspot counts remained unchanged: `YoutubeWebView=1246`,
  `YoutubeMediaEngine=1121`, `MediaSessionCallback=2182`,
  `MainActivityDelegate=1142`, and `ControlPanelView=758`.
- `aauto.aar` SHA-256 remained
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.
- The existing signed universal artifact from the prior accepted source state
  remains applicable because this pass did not change source.

### Cleanup state

The effective release profile was restored to Master on and Equalizer off
(unity). An unrelated `.test` activity surfaced after the release process was
force-stopped; its temporarily touched Equalizer checkbox was restored off and
that activity contributed no acceptance evidence. B2-created DHU and CDP
forwards and the DHU process were removed after the focused unit-suite
recheck. Playback was left paused. Final device checks reported empty
`adb forward --list` and `adb reverse --list` output.

### Final B2 verdict

`WEBEQ_B_YOUTUBE_PARTIAL_PHYSICAL_ACCEPTANCE`

WEBEQ-B remains safe on the previously accepted supported path. This pass did
not prove a WebAudio-specific lifecycle regression and therefore made no
production fix. It also did not earn the broader `WEBEQ_B_YOUTUBE_PASS`: clean
fullscreen/back, a continuous A -> B -> A graph test, and visible DHU control
acceptance remain outstanding.

## WEBEQ-B3 Final Physical Closure

### Baseline and scope

This is an acceptance-only pass on physical device `15c36230`, signed release
package `me.app.fermataX.auto`, and commit `1c5cf89d`. The tracked production
and test source trees were clean before testing and remain unchanged. The only
new evidence is untracked under `.webeq-b-temp/b3/`; it contains screenshots
and bounded state only. No URL, media address, DOM, cookie, header, account
data, token, sample, or temporary production diagnostic was retained.

The temporary controlled profile was observed through the normal FermataX UI
and the existing bounded bridge status object only:

```text
Master = ON
Equalizer = ON
Preamp = 0 dB
1 kHz = -6 dB
```

The starting and restored release profile was Master on, Equalizer off, with
zero preamp. The mobile release's legacy Equalizer presentation exposed its
native backend control rather than a ten-band canonical editor, so the bridge
read-back, not an inferred UI label, is the evidence for the controlled 1 kHz
value.

### Continuous A -> B -> A

Video A was a public, non-EME Charlie Chaplin title. With A visibly playing,
the bounded status was:

```text
bridge = SUPPORTED_ACTIVE
Master = true
Equalizer = true
Preamp = 0
1 kHz = -6
MediaSession = PLAYING
```

Video B was selected once from the visible normal YouTube recommended-content
tile, not by URL injection, JavaScript navigation, Dashboard navigation, or an
Android Back return. B was a distinct public Charlie Chaplin title and visibly
started in the FermataX playback surface. Its bounded bridge status remained
`SUPPORTED_ACTIVE` with Master and Equalizer true, preamp zero, and 1 kHz
`-6`; `FermataMediaService` was `PLAYING`.

The B3 bounded surface intentionally does not reveal media-element identity or
graph count. It therefore does not prove whether YouTube reused A's element or
created a new one, and it does not elevate that fact to a physical graph-count
pass. It does prove that the current document retained one active bridge owner
and the controlled profile across the A -> B selection.

The filtered B3 device-log audit found no
`InvalidStateError`, `MediaElementSource`, `createMediaElementSource`,
`already connected`, `already created`, `AudioContext`, or `WebAudio bridge
failure` entry. Console exception collection was not required to access media
data and was not expanded beyond the existing bounded probe; it is therefore
`NOT OBSERVED` in B3. B2's prior filtered console evidence remains historical
support for its A -> B attempt.

The required uninterrupted B -> A leg was not honestly obtainable. After B
selection FermataX entered its playback surface, where the visible previous
control did not select A. The normal YouTube content-selection surface was no
longer available. Android Back, Dashboard navigation, injected URLs, and
JavaScript navigation were forbidden for this acceptance experiment and were
not used for the accepted chain. An earlier exploratory fullscreen-exit Back
was not counted as A -> B -> A evidence.

**Verdict:** `YOUTUBE_A_B_A_LIFECYCLE_NOT_OBSERVED_RELIABLY`.

This is a YouTube/FermataX surface-flow limitation, not a reproduced
WebAudio-specific defect. A -> B retained bridge eligibility and the controlled
profile, and no duplicate-source or AudioContext failure was observed. No
source change is justified by this evidence.

### DHU visible acceptance

The established `open-dhu.bat` workflow launched DHU at the 1280x720 preset.
It created the B3-owned `tcp:5277 -> tcp:5277` forward, the DHU process had a
visible Windows window handle, and the phone transitioned to Android Auto
`GhostActivity`. These are setup observations only.

The available CUA surface did not expose any native app window, including the
DHU window, for direct viewing or interaction. Consequently B3 could not
visibly confirm FermataX in DHU, YouTube in DHU, projected playback, a single
active projected host, live `0 -> -10 -> 0` EQ, Master neutralisation, EQ
neutralisation, or the DHU pause/resume controls. Passive CDP was deliberately
not substituted for those visible gates.

DHU was stopped after this preflight blocker. The phone returned to FermataX
without a crash. A new reconnect/ownership result is not claimed because the
visible-DHU preflight gate failed first.

**Verdict:** `BLOCKED_DHU_VISIBLE_UI_ENVIRONMENT` and
`WEBEQ_B_YOUTUBE_DHU_PARTIAL_PHYSICAL_ACCEPTANCE`.

### Fullscreen disposition

No fullscreen investigation was performed in B3. The B2 disposition remains
unchanged:

`YOUTUBE_FULLSCREEN_EXISTING_OR_UPSTREAM_ISSUE`

No B3 observation directly attributes a fullscreen or Back presentation issue
to WebAudio.

### Validation and immutable boundaries

- Production LOC changed: `0`.
- Test LOC changed: `0`.
- Focused Fermata and web unit-suite command completed successfully in this
  worktree; the final rerun is recorded with this closure commit.
- `git diff --check` passed with the report as the only tracked change.
- Nonblank hotspot counts are unchanged from B2:

  ```text
  YoutubeWebView = 1246
  YoutubeMediaEngine = 1121
  MediaSessionCallback = 2182
  MainActivityDelegate = 1142
  ControlPanelView = 758
  ```

- `fermata/lib/auto/aauto.aar` SHA-256 remains
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.
- Source did not change, so the previously verified signed universal WEBEQ-B
  APK remains applicable. No rebuild was performed.
- No Stremio, generic browser, native EQ, Auto manifest, or `aauto.aar` source
  was changed.

### Cleanup and audits

- The release profile was restored and visually verified as Master on,
  Equalizer off, preamp zero; the bounded bridge returned the expected neutral
  profile (`m=true`, `e=false`, `b=0`).
- Playback was left paused and the test surface was exited to settings.
- B3-created DHU was stopped.
- The B3 DHU forward and the bounded-CDP forward were removed. Final
  `adb forward --list` and `adb reverse --list` were empty.
- The temporary bounded-status reader was removed before this report.
- `.webeq-b-temp/b3/` remains untracked evidence only.

Audit round 1: A and B each produced `SUPPORTED_ACTIVE`, retained the `-6`
profile across their visible transition, and produced no filtered device-log
duplicate-source or AudioContext failure. The continuous return to A is
`NOT OBSERVED RELIABLY` rather than inferred.

Audit round 2: DHU setup and projection transition were observed, but direct
DHU controls were not available to the test surface. Every visible-control
claim is therefore blocked rather than inferred from passive observation.

Audit round 3: scope remained report-only; source, architecture ceilings,
`aauto.aar`, profile cleanup, ADB cleanup, and DHU cleanup were checked.

### Final B3 verdict

`WEBEQ_B_YOUTUBE_PARTIAL_PHYSICAL_ACCEPTANCE`

```text
A -> B = PASS for visible selection, playback, bounded active bridge,
         profile retention, and filtered device-log error gate.
A -> B -> A = YOUTUBE_A_B_A_LIFECYCLE_NOT_OBSERVED_RELIABLY.
DHU visible control = BLOCKED_DHU_VISIBLE_UI_ENVIRONMENT.
Fullscreen = retain B2 non-WebAudio attribution.
Production/test LOC = 0.
```

The remaining work is not a code-fix task. It requires a test environment that
can directly display and operate the DHU window, plus a normal YouTube surface
that permits a B -> A selection without Android Back or Dashboard navigation.

## WEBEQ-B4 Final Full Physical Closure Checkpoint

### Baseline and starting environment

This B4 checkpoint began at `47b119361dc75215cafa551870f4c6293c0b3970` with
no tracked production or test source diff. The historical untracked evidence
directories `.native-eq-a-temp/` and `.webeq-b-temp/` were preserved.

An existing DHU process was already running and owned the pre-existing
`tcp:5277 -> tcp:5277` ADB forward. The physical device entered Android Auto
`GhostActivity`; no reverse mapping existed. B4 did not restart DHU, create a
second DHU process, change the existing forward, or change the current release
audio profile.

### Visible-DHU preflight

The required direct-control preflight could not be satisfied by the available
automation environment. It reported no native application surface at all,
including no DHU window, despite the running DHU process and Android Auto
projection. Therefore the agent could not visually inspect or operate the
already-open DHU window.

This is not evidence that the user-visible DHU window is absent or unusable;
it is evidence that it is not exposed to the available direct automation
surface. The B4 rules forbid substituting process state, `GhostActivity`, ADB,
or passive CDP for visible DHU interaction. The checkpoint stopped before
altering projection, profile, playback, or YouTube navigation.

### Gates not run

The following were not run in B4 because the mandatory visible-DHU preflight
failed for this automation surface:

- continuous A -> B -> A selection;
- visible projected FermataX/YouTube playback;
- live `0 -> -10 -> 0` WebAudio EQ read-back;
- Master/EQ neutralisation;
- visible DHU pause/resume;
- projected-host ownership;
- DHU disconnect/reconnect.

No product failure, WebAudio failure, duplicate-source error, or AudioContext
failure was reproduced. B4 does not revise any B2 or B3 conclusion.

### Scope and cleanup

- Production LOC changed: `0`.
- Test LOC changed: `0`.
- No temporary diagnostic, source, test, ADB mapping, profile, playback, or
  DHU process was changed by B4.
- The pre-existing DHU process and its `tcp:5277` forward were deliberately
  left intact for a future session with direct visible control.
- B3's focused-suite and `ArchitectureBoundaryTest` evidence remains the most
  recent source-validation evidence; B4 did not rerun tests because it made no
  source change and did not reach a physical acceptance action.

### Final B4 verdict

`WEBEQ_B_YOUTUBE_PARTIAL_PHYSICAL_ACCEPTANCE`

```text
A -> B -> A = not run in B4; retain B3
YOUTUBE_A_B_A_LIFECYCLE_NOT_OBSERVED_RELIABLY.

DHU visible control = BLOCKED_DHU_VISIBLE_UI_ENVIRONMENT for this agent
surface. The already-running DHU session is preserved, not treated as failed.
```

WEBEQ-B remains open solely for direct visible DHU interaction and a valid
normal-YouTube B -> A content-selection path. No code change is indicated.
