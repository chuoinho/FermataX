# 04 - Security and Privacy

- Load the official HTTPS Stremio Web origin; do not inject credentials or player controls.
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
