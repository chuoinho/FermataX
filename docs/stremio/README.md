# Stremio Documentation Authority

## Current Architecture

The authoritative design for the FermataX Stremio addon is the hosted Web-only model in
[`web-only/README.md`](web-only/README.md). It replaces the legacy native Stremio implementation
with a dedicated `web.stremio.com` WebView surface in `modules/web`, and hands only validated
HTTP(S) media URLs to the existing Fermata player.

Read the documents in `web-only/` in their numbered order. `08_TRACEABILITY_MATRIX.md` is the
requirements-to-verification index and `03_IMPLEMENTATION_PLAN.md` defines the mandatory phase
gates.

## Status Of Earlier Documents

The other files in this directory describe the previous native Stremio addon. They remain as
historical evidence and protocol/reference material only; they are not an implementation target.
In particular, no document outside `web-only/` authorizes a Core bridge, a native streaming server,
or bundled torrent runtime.

The legacy source remains in the repository until the Web-only migration reaches its cleanup gate.
It must not be used as a runtime fallback.
