# Stremio Control-Only Player Bridge Design

**Status:** approved design, pending implementation-plan review  
**Date:** 2026-09-20  
**Branch:** `codex/open-on-car`

## Goal

Make the existing hosted Stremio WebView the sole renderer and player while allowing the existing
FermataX phone header, SmartTop dashboard, Android Auto, and DHU surfaces to present its safe
state and send safe transport commands. The feature must not introduce a second player, a second
MediaSession, a fake `PlayableItem`, or a generic bridge for arbitrary web addons.

## Scope and non-goals

In scope:

- The Stremio-specific, exact-origin Media Session bridge in `:web`.
- A short-lived control-only claim of FermataX's existing `MediaSessionCallback`.
- Metadata, Play/Pause, upstream-advertised Next, optional timeline presentation, and eventually
  a lease-bound seek command.
- Correct ownership across phone navigation, document changes, WebView/renderer loss, native
  playback takeover, and Android Auto connection loss.
- Phone header, SmartTop, and AA/DHU presentation through the existing MediaSession.

Out of scope:

- A FermataX-native Stremio player, decoder, torrent/server runtime, stream URL extraction, or
  external-player handoff.
- A second MediaSession, audio-focus ownership, queue, playback-progress persistence, or a fake
  `PlayableItem` for Stremio Web.
- DOM selectors, CSS injection, router scraping, cookies, credentials, stream URLs, headers, or
  arbitrary page JavaScript.
- Previous-track support. Next exists only when Stremio's own Media Session registers
  `nexttrack`.
- Artwork fetching from arbitrary remote artwork URLs in the first release.
- A generic bridge for YouTube, Web Browser, or another addon.

## Architecture

```text
Hosted Stremio WebView (the only renderer/player)
        | exact-origin, main-frame WebMessage protocol
        v
StremioWebMediaSessionBridge (Stremio-only state/command adapter)
        | control-only lease; no MediaEngine
        v
MediaSessionCallback (the only Android MediaSession)
        |
        +--> PhonePlaybackCarHeaderController
        +--> SmartTopCoordinator
        +--> Android Auto / DHU host controls
```

`StremioWebMediaSessionBridge` is only eligible to hold the lease while all of these statements
are true:

1. the AndroidX document-start script and exact-origin web-message listener were installed;
2. the current WebView is attached, not closed, and still hosts an accepted `https://web.stremio.com`
   document;
3. the document generation and opaque page-session token match the native state;
4. the page reports playback as `playing` or `paused` and has a Play or Pause action handler;
5. no native `MediaEngine` owns the FermataX session; and
6. a confirmed automotive shutdown has not occurred.

The active fragment is deliberately not an eligibility criterion. `showFragment()` retains a
WebView using hide/show, so Dashboard, Settings, Home/lock, and resume can hide the fragment while
the document and renderer remain live. Fragment visibility is UI state, not player liveness.

## Ownership and lifecycle contract

| Event | Required result |
|---|---|
| Stremio -> Dashboard or Settings | Preserve the lease while the hosted document is live. The WebView is hidden, not destroyed. |
| Phone Home/lock/resume | Preserve the lease if the document and renderer remain live. Never replay a prior command. |
| Back that calls `loadFreshDocument`, reload, or leaves the exact origin | Cancel pending transfer, revoke the claim immediately, advance the document generation, and reject old messages. |
| `pagehide`, Stremio player unmount, or loss of required handlers | Revoke immediately. |
| WebView detach, renderer loss, `destroy()`, or bridge close | Revoke immediately. Recovery returns to a safe route and never restores a former player action. |
| Native `MediaEngine` starts | `MediaSessionCallback.setEngine()` revokes the control-only lease before the engine becomes owner. |
| Raw AA disconnect | Disable the phone's **Open on car** switch and cancel pending transfer only. Keep a still-live Stremio claim during the grace window. |
| AA reconnect within 1.5 seconds | Cancel the shutdown. Retain the live session without replaying transport or transfer commands. |
| Confirmed AA disconnect after 1.5 seconds | Run the existing `AutoSessionShutdown`: release Stremio control-only state, blank/close hosts, and stop the normal auto session. Reconnect requires a manual open; no old request may replay. |

`StremioWebFragment.onHiddenChanged()` and `FRAGMENT_CHANGED` must stop claiming/releasing based
on active visibility. They may still refresh the UI and Open-on-car capture. Liveness must instead
be maintained by `StremioWebView` document, attachment, renderer, and close callbacks.

## Protocol and state model

The existing protocol stays origin-locked to `https://web.stremio.com`, main-frame-only,
document-start-only, versioned, opaque-session-bound, and size-bounded. The bridge continues to
use `WebViewCompat.addWebMessageListener`; it must not add an `addJavascriptInterface` fallback.

Protocol version 2 adds two page-to-native message types while retaining the current `READY`,
`PLAYBACK_STATE`, `METADATA`, `HANDLER_REGISTERED`, `HANDLER_REMOVED`, and `SESSION_CLOSED`:

| Message | Required fields | Meaning |
|---|---|---|
| `CONTENT_CHANGED` | `v`, document generation `g`, session `s`, positive content generation `c` | A different in-document media item was selected. Invalidates the preceding lease and timeline. |
| `TIMELINE` | `v`, `g`, `s`, `c`, bounded finite `positionMs`, `durationMs`, `rate`, `seekable`, `playing` | A presentation snapshot. No URL, source, token, header, cookie, or DOM data is permitted. |

The injected script observes the current `HTMLVideoElement` by capture event listeners and a
bounded attachment lifecycle, not by a repeated selector/query loop. It emits an immediate update
for `loadstart`, `loadedmetadata`, `durationchange`, `play`, `pause`, `waiting`, `seeking`,
`seeked`, `ended`, and `emptied`; `timeupdate` is capped at one message per second. `loadstart`
increments the page-local content generation and clears its timeline. It sends only finite,
non-negative, bounded numbers. It never sends `currentSrc`, `src`, route, DOM text outside bounded
metadata, headers, credentials, or artwork URLs.

Native state receives the same limits. A session mismatch, an old document generation, a stale
content generation, invalid numbers, or a timeline outside a reasonable range is ignored. A
content change advances the control-only content key from:

```text
documentGeneration:pageSession
```

to:

```text
documentGeneration:pageSession:contentGeneration
```

Thus an old button press or seek drag cannot affect a new episode in the same Stremio single-page
document.

## MediaSession and timeline contract

Add an immutable, non-native value type:

```java
public record ControlOnlyTimeline(
    long contentGeneration,
    long positionMillis,
    long durationMillis,
    float playbackRate,
    long updatedAtElapsedMillis,
    boolean seekable,
    boolean playing) {}
```

`ControlOnlyPresentation` gains a nullable `ControlOnlyTimeline`. It remains a presentation
record, not a `PlayableItem`, and `PlaybackTimelineSnapshot` remains exclusive to native
`MediaEngine` playback.

`MediaSessionCallback` extends `ControlOnlyDelegate` with a separate, optional seek capability.
The callback captures a control-only seek token from the current lease and content generation;
the token contains no WebView or URL. A seek is dispatched only if the lease, delegate,
content-generation, seekable state, and position range are still current. The bridge then sends a
fixed `seek` command containing only those opaque generations and a clamped millisecond position.
The injected script validates the same tuple and sets `video.currentTime` only on its current
attached seekable video. It sends a fresh timeline after the seek.

`ACTION_SEEK_TO` is advertised only while the current control-only timeline is seekable. The
native `onSeekTo()` path must route control-only seek before touching `MediaEngine`; native seeking
continues to use its existing `LastPlayedLease` and `PlaybackTimelineSnapshot` logic.

Rollout is deliberately staged:

1. lifecycle, metadata, Play/Pause, and Next;
2. read-only timeline in phone and SmartTop;
3. lease-bound seek after physical runtime validation; and
4. AA/DHU exposure of seek only when Step 3 passes.

## UI contract

All surfaces render the same existing Android MediaSession. No surface talks directly to the
WebView.

- **Phone header:** present control-only title/subtitle and Stremio fallback icon. Play/Pause and
  Next call the existing binder/MediaController path. It initially treats a control-only timeline
  as display-only, then enables the seek bar only after a valid control-only seek token is
  available. It cancels a drag when the lease or content generation changes.
- **SmartTop:** render `CURRENT_WEB` with the Stremio fallback icon and a control-only timeline
  instead of `SmartTopTimeline.hidden(playing)`. It uses the same action set and only publishes a
  timeline refresh when a materially newer presentation arrives.
- **AA/DHU:** consume the Android MediaSession state and actions already emitted by
  `MediaSessionCallback`. The host sees only actions genuinely registered by Stremio. Previous is
  never advertised; seek is hidden until seekable state is true.
- **Artwork:** phase one keeps bounded title/artist plus the local Stremio icon. A later proposal
  may support a strictly validated HTTPS artwork URI only after the image-loading privacy and cache
  behavior are reviewed; no native image fetch is part of this design.

## Presentation updates

`FermataServiceUiBinder` is currently driven by MediaController callbacks. A control-only
publication must therefore update the existing MediaSession metadata and playback state in the
same transaction and expose `getControlOnlyPresentation()` from the binder. Add a dedicated
control-only timeline/presentation notification so that phone and SmartTop refresh immediately on
one-second timeline updates without manufacturing a native playback snapshot or a fake item.

The UI derives a `ControlPresentation` from either the current native snapshot/timeline or the
currently valid control-only presentation/timeline, preferring a real native engine whenever one
exists. The handoff is atomic: release/revoke clears presentation before a native engine publishes
its first state.

## Security and privacy invariants

- Exact `https://web.stremio.com` origin, main frame, and document generation are required for
  every page message.
- All messages are allowlisted, JSON-size-bounded, field-bounded, and reject stale opaque tokens.
- Native-to-page dispatch may carry only fixed actions (`play`, `pause`, `nexttrack`, and the
  lease-bound `seek`) plus numeric generation/position values. It cannot carry a URL, selector,
  arbitrary JavaScript, cookie, header, token, or stream identifier.
- Diagnostics may record event kind and generations, but never media URLs, account information,
  Stremio addon data, cookies, headers, or source strings.
- Upstream Stremio MediaSession behavior may be studied, but GPL-2.0 source is not copied. AndroidX
  WebKit (Apache-2.0), Capacitor's main-frame feature-check pattern (MIT), and Scarlett's
  one-second `timeupdate` throttling concept (MIT) are behavioral references only.

## Validation matrix

Automated coverage must prove:

1. hiding Stremio for Dashboard or Settings keeps a live claim and transport routing;
2. Home/lock/resume retains a live document without a replay;
3. fresh document, origin change, page close, destroy, and renderer loss invalidate old leases;
4. a native engine takes ownership before a control-only action can execute;
5. a content change rejects a stale seek token;
6. unsupported/invalid timeline payloads are ignored without changing the claim;
7. the raw AA disconnect/reconnect grace window retains a live claim without replay; and
8. confirmed disconnect invokes normal shutdown and requires manual reconnect/open.

Physical phone + DHU coverage must observe:

1. exactly one action from Play/Pause/Next or steering-wheel/DHU input;
2. no Previous unless Stremio actually exposes it (this design never does);
3. a visible, correct read-only timeline before seek rollout;
4. when seek is enabled, one seek to the requested position and no stale seek after episode change;
5. Dashboard/Settings/Home/lock-resume control continuity while the WebView stays live;
6. no late transport/transfer after Back, reload, host loss, or confirmed AA disconnect; and
7. manual reconnection starts a clean session with no automatic replay.

## Documentation migration

The historical `docs/stremio/web-only/01_ARCHITECTURE.md` and
`02_COMPONENT_API_DESIGN.md` describe fragment-visibility-based claim/release. They must be
updated in the implementation change to point to this liveness-based contract; otherwise they
would contradict the feature. `05_TEST_ACCEPTANCE.md` must gain the new control-only lifecycle
and timeline gates without claiming a capability that was not physically observed.

## Acceptance criteria

The feature is acceptable only when there is one Android MediaSession, one Stremio WebView player,
no unbounded page/native data path, no automatic command replay, and all automated plus required
physical cases above have recorded PASS/NOT-OBSERVED status. Any failure in stale-command,
renderer-loss, native-takeover, or confirmed-AA-shutdown behavior blocks rollout.
