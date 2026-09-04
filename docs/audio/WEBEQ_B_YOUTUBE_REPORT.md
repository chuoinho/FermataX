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
