# 02 - Component Design

## Production classes

- `StremioWebAddon`: addon metadata, dedicated `stremio_web` preferences, hosted home URL and
  voice-search entry.
- `StremioWebFragment`: fragment ID, Stremio search route, disabled desktop toggle, and the
  active-fragment signal that claims/releases the control-only MediaSession delegate.
- `StremioWebView`: Stremio-specific WebView subtype that installs its bridge before the first
  hosted navigation and recreates the same setup after renderer loss.
- `StremioWebMediaSessionBridge`: exact-origin document-start Media Session compatibility shim and
  fixed-action bridge. Its six accepted versioned messages are `READY`, `PLAYBACK_STATE`,
  `METADATA`, `HANDLER_REGISTERED`, `HANDLER_REMOVED`, and `SESSION_CLOSED`.
- `StremioWebClient`: retains the generic WebView client behavior while rejecting only main-frame
  `intent:` and `stremio:` routes. This prevents a hosted Stremio install action from launching an
  arbitrary Android app; HTTPS navigation and player resources remain untouched.
- `WebBrowserAddon`: reusable protected constructor with a preference namespace and initial URL.
- `WebBrowserFragment`: factory hooks for client/chrome-client and polymorphic URL persistence.

`StremioWebFragment` uses the existing `FermataChromeClient` unchanged. Its only client
specialization is the narrow external-scheme block above; it does not intercept media, extract
URLs, or change WebView renderer recovery. The Stremio-only bridge uses no Java object exposed to
JavaScript and no selector, DOM, CSS, or router operation.

`MediaSessionCallback.ControlOnlyDelegate` is deliberately smaller than `MediaEngine`. It has no
playable item, source, decoder, duration, position, progress, audio focus, or playback lease. It
only routes MediaSession `play`, `pause`, and optional `next` actions after confirming that no
native engine owns playback. A fragment switch releases it synchronously; re-entry requires both
an active fragment and the current document's registered handlers.

## Persistence

Native preferences store only the latest hosted route in the `stremio_web` namespace. Android
WebView owns cookies, local storage and Stremio settings. FermataX must not persist or log account
tokens, cookies, addon credentials, streaming-server credentials, magnet links or media URLs.

## Voice

Voice routes to `https://web.stremio.com/#/search?search=<encoded query>`. It opens search only;
it must not select a stream or autoplay.
