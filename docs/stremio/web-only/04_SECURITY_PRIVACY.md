# 04 - Security and Privacy

- Load the official HTTPS Stremio Web origin; do not inject credentials or player controls.
- The Media Session compatibility/control bridge is restricted to the exact
  `https://web.stremio.com` origin and main frame. It uses AndroidX WebKit document-start scripts
  and an origin-aware web-message listener, never `addJavascriptInterface`.
- The page-to-native protocol is versioned, size-bounded and allowlisted. It transports only
  playback state, bounded title/artist metadata, handler registration, and an opaque session
  token. It rejects stale tokens, invalid origin/frame, malformed input, and messages outside the
  six documented message types.
- Native-to-page dispatch is generated solely from the fixed `play`, `pause`, and `nexttrack`
  enum. It cannot carry a URL, selector, JavaScript supplied by the page, cookie, token, or header.
- Preserve Android WebView cookie/local-storage isolation; never mirror these values into Fermata
  settings, backups, diagnostics or logs.
- Do not intercept media requests, parse Stremio internal state or extract stream URLs.
- Do not register any external-player handoff in the Stremio addon capability set.
- Main-frame `intent:` and `stremio:` navigation is rejected locally. Download and popup behavior
  remains unsupported until observed and explicitly reviewed; never launch arbitrary parsed
  intents.
- Generic diagnostics must not record page URLs containing query credentials or account material.

The absence of a bundled torrent/server runtime reduces FermataX's attack surface and keeps user
server credentials under Stremio Web control.
