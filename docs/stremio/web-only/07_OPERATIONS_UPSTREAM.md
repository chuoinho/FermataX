# 07 - Upstream Operations

The supported upstream is [Stremio Web](https://github.com/Stremio/stremio-web), hosted at
`https://web.stremio.com/#/`. FermataX does not vendor its bundle, so each release validation must
smoke-test the real hosted origin on the supported Android System WebView versions.

Monitor for changes to:

- login/session persistence;
- catalog/search/details rendering;
- HTML5 playback and browser fullscreen;
- streaming-server configuration UI;
- renderer crashes, errors and ANRs.

If the hosted UI changes incompatibly, investigate the WebView host boundary first. Do not silently
switch to a native renderer or player callback.
