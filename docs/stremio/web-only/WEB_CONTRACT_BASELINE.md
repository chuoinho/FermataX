# Hosted Web Contract Baseline

This document now records the only required hosted contract: `https://web.stremio.com/#/` loads in
`FermataWebView`, retains WebView-managed authenticated storage, and renders its own HTML5 player.

The external-player callback explored in Phase 0B is historical negative evidence only. It was
explicitly rejected as the production architecture and is not a prerequisite for Phase 1.

The Phase 1 device result is recorded in `reports/PHASE_1_REPORT.md`. A missing server blocks only
real playback fixtures; it does not permit a native fallback.
