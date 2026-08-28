# FermataX x Stremio Web Only

**Version:** 1.1
**Status:** AUTHORITATIVE - RELEASE READINESS PARTIAL
**Scope:** Host the official Stremio Web application inside FermataX.

Stremio is an inline `FermataWebView` addon, using `https://web.stremio.com/#/` and the upstream
HTML5 `stremio-video` renderer. It is not a native Stremio implementation.

Read the numbered documents in order. `reports/PHASE_0B_REPORT.md` is preserved historical
evidence; `reports/PHASE_1_REPORT.md` records the first implementation and its observed limits.

The selected build is `-PWEB_STREMIO=true`: it replaces, rather than coexists with, legacy
`:stremio`. A normal build without that property remains unchanged until cutover is approved.

The web-only release graph and its universal APK have passed their focused release audit. Release
readiness remains **PARTIAL** until the independently recorded MediaSession/DHU, multi-audio and
next-track boundaries have evidence. The user-configured streaming-server/torrent boundary passed
with the local-only physical P8G fixture. See
`reports/PHASE_7_FINAL_ACCEPTANCE_RELEASE_REPORT.md` for the final reconciliation.
