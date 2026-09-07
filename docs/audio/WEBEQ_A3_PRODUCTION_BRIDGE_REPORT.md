# WEBEQ-A3 Stremio Production WebAudio Bridge

## Status

**IMPLEMENTED_WITH_CONSERVATIVE_RUNTIME_SCOPE**

This phase adds the narrow WebAudio backend authorised by the earlier runtime
audit. It processes only an observed-safe Stremio class: one visible, advancing,
non-EME `blob:` media element in the main `https://web.stremio.com` document.
Every other source class, including direct HTTP(S), remains on the existing
browser-owned playback path.

The runtime graph and its HLS replacement behaviour were observed on a physical
Android device. The original A3 observation could inspect topology only, so it
left live `AudioParam` propagation PARTIAL. A3R subsequently used a narrowly
scoped passive observer and directly proved `1 kHz` `0 -> -10 -> 0 dB` on the
same production filter; that later evidence supersedes the original limit.

## Scope And Ownership

The production seam is deliberately Stremio-only:

```text
StremioWebView
  -> StremioWebAudioBridge
      -> exact-origin document-start shim
          -> one MediaElementAudioSourceNode
          -> preamp -> 10 canonical peaking filters -> output gain -> destination
```

- `StremioWebView` owns document navigation, fresh-document replacement,
  automotive-session end, and destruction boundaries.
- `StremioWebAudioBridge` owns an exact-origin document-start script, profile
  dispatch, document generations, and best-effort teardown.
- The page shim owns only its document-local `AudioContext` and graph. It does
  not expose media URLs, DOM state, credentials, cookies, tokens, or account
  data to native code.
- `StremioWebMediaSessionBridge` remains separate and continues to own only
  MediaSession compatibility/control behaviour.

The bridge never broadens `FermataWebView`, the generic browser, YouTube, native
session audio effects, or `aauto.aar`.

## Safety Policy

Attachment requires every one of these gates:

- exact HTTPS host `web.stremio.com` on default HTTPS port;
- top-level main document;
- exactly one winning, connected and visible `video` or `audio` candidate;
- advancing, unpaused, non-ended media with `readyState >= 2`;
- `blob:` source only;
- no `mediaKeys` and no observed `encrypted` or `webkitneedkey` event.

Ties fail closed. Direct HTTP/HTTPS media, iframes, unknown sources, paused
placeholders, EME signals, source replacement, element removal, player-route
loss, navigation, automotive-session end, and view destruction all retain the
normal hosted player or tear down the document-owned graph. A temporary pause
or buffering stall retains an already attached graph because a media element
may have only one `MediaElementAudioSourceNode`.

The profile wire format is bounded and canonical: master and EQ enable flags,
ten bands from `31 Hz` through `16 kHz`, and a preamp clamped to `-60..0 dB`.
Profiles with an unsupported schema, unsafe preamp, invalid band count, NaN, or
out-of-range values fall back to unity/disabled processing.

## Physical Runtime Evidence

### Environment

- Physical device: `15c36230`, Android 16.
- Package under test: `me.app.fermataX.auto.test` version 304.
- Hosted origin: `https://web.stremio.com`.
- Temporary self-owned fixture was used only for this validation and has been
  removed; no raw media URL is retained in this report.

### Observed Matrix

| Case | Result | Evidence | State |
| --- | --- | --- | --- |
| HLS A | A visible advancing main-document `VIDEO` had a `blob:` source, `readyState=4`, and no `mediaKeys`. The fixture received playlist and segment requests. | DevTools WebAudio count: one media source, two gains, ten biquad filters, one destination. | PASS |
| HLS A to HLS B | Selection was performed through normal Stremio UI. A new HLS request sequence occurred, the B fixture's distinct frame rendered, and a fresh graph again contained ten biquad filters. | Normal player replacement did not reuse the old graph. | PASS |
| Direct MP4 | Selection through normal Stremio UI produced a visible playing `VIDEO` and normal fixture HEAD/GET traffic. No new WebAudio nodes appeared. | The intentional direct-source bypass stayed intact. | PASS |
| `1 kHz` UI setting | The normal FermataX Equalizer UI changed `1 kHz` to `-10 dB` before HLS validation and restored it to exactly `0 dB` afterwards. | Graph topology was observable, but live `AudioParam` values were not exposed by passive DevTools. | PARTIAL |
| Fullscreen | The current A3 click retest did not yield a reliable observed transition. | Earlier physical HLS evidence in `WEBEQ_A_STREMIO_REPORT.md` is retained as the fullscreen PASS evidence; this A3 run makes no new fullscreen claim. | HISTORICAL PASS |

No URL, query string, token, cookie, account value, or media capability was
logged or added to source control.

## Automated Validation

The following completed successfully after implementation:

```text
.\gradlew.bat :web:testAutoDebugUnitTest :web:testMobileDebugUnitTest verifyWebOnlyProductionGraph --console=plain
```

The focused JVM tests cover exact origin/port acceptance, document-start
availability, one-time attachment, encrypted-media rejection, candidate ties,
direct HTTP/HTTPS rejection, source replacement, short pause/stall retention,
generation-bound profile updates, canonical ten-band serialisation, safe
preamp bounds, and malformed wire profiles.

`git diff --check` passed before and after this report. The immutable
`aauto.aar` SHA-256 was rechecked as:

```text
99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B
```

This is the approved immutable value; the archive was not modified.

## Change Inventory

Production changes:

- `StremioWebView.java`: installs, invalidates, and closes the dedicated bridge
  at existing document/session/view lifecycle boundaries.
- `StremioWebAudioBridge.java`: origin-bound bridge and document-start shim.
- `StremioWebAudioCandidatePolicy.java`: pure conservative eligibility policy.
- `StremioWebAudioProfile.java`: bounded canonical profile projection.

Test changes:

- `StremioWebAudioBridgeTest.java`
- `StremioWebAudioCandidatePolicyTest.java`
- `StremioWebAudioProfileTest.java`

New production source is 543 physical lines, plus the scoped lifecycle calls in
`StremioWebView`; new focused test source is 210 physical lines. No unrelated
production, test, manifest, YouTube, generic browser, Stremio Core, torrent, or
native media-engine code was changed.

## Cleanup Evidence

The temporary addon `FermataX WebEQ A3 Temporary` was uninstalled once through
the normal Stremio Addons UI. The post-uninstall UI contained only the original
seven addons: Cinemeta, YouTube, WatchHub, Public Domain Movies, OpenSubtitles
v3, Local Files (shown by Stremio as `Local Files (without catalog support)`),
and K20 Phim Tong Hop.

After that confirmation:

- the local Node server PID `13820` was stopped;
- `adb reverse tcp:18080` was removed;
- temporary DevTools forwards `9222` through `9225` were removed;
- the self-owned fixture directory, logs, probes, generated media, and 225
  temporary screenshots were deleted;
- port `127.0.0.1:18080` was verified closed.

No temporary server, ADB mapping, fixture addon, account mutation, or captured
test material remains.

## Remaining Limits

- The bridge intentionally does not process direct HTTP/HTTPS, audio-only
  direct media, iframes, unknown media, or EME/DRM paths.
- The exact live WebAudio parameter-value assertion needs a future measurement
  method that is both non-invasive and does not expose player or account data.
- Current fullscreen acceptance relies on the earlier physical HLS evidence;
  A3's attempted fullscreen retest was inconclusive.

## A3R Final Runtime Acceptance

### Baseline And Method

- Baseline: `d63dfeb03d8f4147ad38f2a5ea17e328eaccf8bc`
  (`feat(audio): add safe Stremio WebAudio bridge`).
- Device: physical Android device `15c36230`; package
  `me.app.fermataX.auto.test`.
- A temporary document-start DevTools observer wrapped only WebAudio node
  constructors. It retained node identity and inspected only context state and
  permitted `AudioParam` values. It did not read media addresses, DOM content,
  cookies, credentials, account state, audio samples, buffers, or network data;
  it did not create a graph or alter network handling.

### Live Profile Propagation

The observer saw the production bridge's single live graph: one running
`AudioContext`, one `MediaElementAudioSourceNode`, two gains, and ten peaking
filters. The 1 kHz filter had identity `66` across the complete UI sequence:

| Normal FermataX UI action | Observed 1 kHz `gain.value` | Graph identity |
| --- | --- | --- |
| Baseline | `0 dB` | unchanged |
| Set 1 kHz to `-10 dB` | `-10 dB` | unchanged |
| Restore 1 kHz to `0 dB` | `0 dB` | unchanged |

No additional media source, context, or filter set appeared during that
sequence. This is direct runtime evidence for the full UI -> repository ->
native bridge -> document shim -> live `AudioParam` path.

The master and nested Equalizer switches were operated through the normal
FermataX settings UI. The final master-page operation retained the same graph
and produced unity filter and gain values while disabled, then restored the
stored profile without source reattachment. Earlier switch operations touched
the nested Equalizer control first; they are not used as evidence for the
master-page control.

### Runtime Regression Evidence

- HLS A visibly played for several minutes. During a fresh replay, the
  production graph was running and Fermata's MediaSession was `PLAYING`.
- Leaving HLS A Player for Detail closed that graph's context and source. The
  MediaSession then returned to `NONE`; no active context was left behind.
- Fresh HLS B playback produced a distinct running context/source/filter set
  only after HLS A had closed. Each observed HLS generation had one source and
  ten filters.
- Fresh Direct MP4 playback visibly advanced and put MediaSession in
  `PLAYING`, while the observer recorded no new MediaElement source. The
  intentional direct-source bypass remains intact.
- Fullscreen remains the historical physical PASS recorded above; no new A3R
  fullscreen claim is made.

### Automated And Release Validation

The required JVM command completed successfully:

```text
.\\gradlew.bat :web:testAutoDebugUnitTest :web:testMobileDebugUnitTest verifyWebOnlyProductionGraph --console=plain
```

The signed universal release APK was built and verified:

- Artifact:
  `fermata/build/outputs/apk_from_bundle/autoRelease/fermata-2.0.1-me.app.fermataX.auto-auto-release-universal.apk`
- Size: `330,452,195` bytes.
- SHA-256:
  `0B4A32677DBDEEEB9FB4D60D24D2593978BC5532DF08AD8242071A0CD1765E24`.
- APK Signature Scheme v3: `true`.
- `aauto.aar` SHA-256:
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.

### Cleanup And Status

The temporary addon was uninstalled through Stremio's visible confirmation
dialog. The resulting visible inventory contains only the original seven
addons: Cinemeta, YouTube, WatchHub, Public Domain Movies, OpenSubtitles v3,
Local Files (without catalog support), and K20 Phim Tong Hop.

The temporary server and tunnel processes were stopped. The phase-created ADB
reverse and DevTools forward were removed; the fixture port is closed.
This execution environment rejected deletion of the already-verified temporary
fixture directory outside the workspace, so that inert directory and
phase-named screenshots are not claimed removed here. No fixture process,
mapping, or installed addon remains.

**Final A3 status: `A3R_PARTIAL_INSTRUMENTATION_LIMIT`.** The live AudioParam,
node-identity, bypass, lifecycle, JVM, release, signature, and immutable-AAR
gates have direct evidence. `WEBEQ_A3_FULL_PASS` is intentionally withheld
because a same-instant observation of master OFF playback continuity with
MediaSession still `PLAYING` was not captured, and the external temporary file
cleanup is incomplete under the current execution policy. Production source
diff: `0`; test source diff: `0`.

## A3F Final Closure

### Baseline And Source State

- Baseline/report HEAD: `87fb0df9d1574234599d2faaa5acfc4630489446`.
- Production implementation retained: `d63dfeb0`.
- No production or test source changed; this report is the only intended
  tracked change.

### Controlled Fixture And Playback Entry Attempt

A self-owned temporary addon was installed and removed exclusively through the
visible Stremio Addons UI. Its HLS catalog, metadata, and stream endpoints were
requested successfully. The selected HLS detail then remained on Stremio's
`Install addons` empty-stream surface: no media playlist/segment request,
HTML5 playback, active MediaSession, or new production WebAudio graph occurred.

The run therefore stopped before the A3F master-off gate. This is not evidence
of a bridge defect: no eligible player graph existed. The coherent master-OFF
observation (unity parameters, `PLAYING`, advancing `currentTime`, unchanged
context/source/filter identities) and the companion master-ON restoration were
not obtained. The accepted A3R evidence remains unchanged.

### Cleanup And Status

- The fixture was visibly uninstalled; the list returned to the original seven
  addons recorded by A3R.
- The temporary Node server and Cloudflare tunnel were stopped.
- `adb reverse tcp:18081` and `adb forward tcp:9222` were removed; port `18081`
  was verified closed.
- The temporary observer disconnected. No EQ profile was changed in this run.
- External deletion policy rejected the exact owned fixture directory
  `C:\\Users\\ttanh\\AppData\\Local\\Temp\\fermatax-webeq-a3r-fixture`.
  It is inert: no addon, server, tunnel, listener, or ADB mapping references it.
  Manual cleanup: `Remove-Item -LiteralPath 'C:\\Users\\ttanh\\AppData\\Local\\Temp\\fermatax-webeq-a3r-fixture' -Recurse -Force`.

**A3F status: `NOT_OBSERVED`.** `A3R_PARTIAL_INSTRUMENTATION_LIMIT` remains the
honest functional status; external cleanup is
`BLOCKED_EXTERNAL_TEMP_DELETE`, not a production defect.
