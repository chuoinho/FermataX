# Stremio Documentation Authority

## Current Architecture

The authoritative design is the hosted, inline WebView model documented in
[`web-only/README.md`](web-only/README.md). FermataX hosts the official
`https://web.stremio.com/#/` UI and its HTML5 `stremio-video` player.

FermataX owns only addon registration, WebView lifecycle/storage, browser custom-view fullscreen,
activity video mode, and back/navigation integration. Stremio Web owns login, addons, catalog,
search, library, settings, stream selection, player state, playback and progress.

There is no external-player callback, URL handoff, VLC/native renderer, JavaScript-to-native
playback bridge, torrent engine, bundled streaming server, Stremio Core, or `aauto.aar` change in
this architecture. A user who needs torrent playback configures a compatible streaming server in
Stremio Web settings; its HTTP/HLS result is played by the hosted HTML5 player.

## Build Selection

`-PWEB_STREMIO=true` replaces legacy `:stremio` with the Web-only addon at build time. It registers
exactly one `stremio_fragment` from `:web` and excludes the legacy `jlibtorrent` feature graph.
Without the property, the existing production graph is unchanged.

## Historical Material

Documents outside `web-only/` and Phase 0B evidence are historical only. The Phase 0B
external-player experiment is retained as evidence of an architecture that was later rejected; it
is not a release gate or a fallback path.
