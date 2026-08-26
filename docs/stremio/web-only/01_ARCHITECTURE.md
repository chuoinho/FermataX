# 01 - Architecture

## Ownership

| Area | Owner |
|---|---|
| Account, addons, catalog, search, library, settings | Stremio Web |
| Stream selection, HTML5 player, playback state and progress | Stremio Web |
| WebView Media Session compatibility and fixed transport control | Stremio-only bridge |
| WebView host, cookies/local storage, lifecycle | FermataX Web shell |
| Custom-view fullscreen, video mode, Back navigation | FermataX Web shell |
| Phone, AA and DHU presentation | FermataX |

```text
FermataX -> :web -> StremioWebAddon -> FermataWebView -> https://web.stremio.com/#/
                                                   -> stremio-video / HTML5 player
```

There is no external player, stream URL extraction, native playback engine, torrent engine,
streaming server, Stremio Core or `aauto.aar` change. The WebView is the renderer.

On Android WebView providers without `navigator.mediaSession`, a Stremio-only document-start
compatibility shim creates the small API surface Stremio Web calls: `playbackState`, `metadata`,
`MediaMetadata` and handlers for `play`, `pause`, and `nexttrack`. The browser bridge is origin
locked to `https://web.stremio.com`, applies only to the main frame, and communicates through
AndroidX WebKit's origin-aware web-message listener. It never reads the DOM, route, media URL,
cookies or page credentials.

The native side may temporarily claim FermataX's MediaSession only while the Stremio fragment is
active and the hosted page exposes a non-`none` session with a play or pause handler. Its only
output is the fixed `play`, `pause`, or `nexttrack` callback dispatch. It has no decoder, source,
position, progress, queue or audio-focus ownership; Chromium remains responsible for playback.

## Playback boundary

The user configures an external Stremio streaming server in the Stremio Web UI when a provider
needs torrent/infohash transport. That server must expose content that the hosted HTML5 player can
consume. If unavailable, the error belongs to Stremio Web; FermataX must remain responsive.

## Navigation

Back exits browser fullscreen first, then WebView history, then the addon. Switching addons and
activity lifecycle use the existing Web shell. Cookies and Web storage are handled by Android
WebView and are never copied into native preferences.

When the fragment is hidden, replaced, navigates to a new document, loses its renderer, or is
destroyed, the bridge releases its MediaSession claim. A new document uses a fresh opaque session
token, so a delayed message from an old page cannot reclaim controls.
