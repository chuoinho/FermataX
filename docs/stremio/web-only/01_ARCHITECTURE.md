# 01 - Architecture

## Ownership

| Area | Owner |
|---|---|
| Account, addons, catalog, search, library, settings | Stremio Web |
| Stream selection, HTML5 player, playback state and progress | Stremio Web |
| WebView host, cookies/local storage, lifecycle | FermataX Web shell |
| Custom-view fullscreen, video mode, Back navigation | FermataX Web shell |
| Phone, AA and DHU presentation | FermataX |

```text
FermataX -> :web -> StremioWebAddon -> FermataWebView -> https://web.stremio.com/#/
                                                   -> stremio-video / HTML5 player
```

There is no external player, stream URL extraction, native playback engine, JS bridge, torrent
engine, streaming server, Stremio Core or `aauto.aar` change. The WebView is the renderer.

## Playback boundary

The user configures an external Stremio streaming server in the Stremio Web UI when a provider
needs torrent/infohash transport. That server must expose content that the hosted HTML5 player can
consume. If unavailable, the error belongs to Stremio Web; FermataX must remain responsive.

## Navigation

Back exits browser fullscreen first, then WebView history, then the addon. Switching addons and
activity lifecycle use the existing Web shell. Cookies and Web storage are handled by Android
WebView and are never copied into native preferences.
