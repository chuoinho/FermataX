# FermataX x Stremio Web Only

**Version:** 1.1
**Status:** AUTHORITATIVE
**Scope:** Host the official Stremio Web application inside FermataX.

Stremio is an inline `FermataWebView` addon, using `https://web.stremio.com/#/` and the upstream
HTML5 `stremio-video` renderer. It is not a native Stremio implementation.

Read the numbered documents in order. `reports/PHASE_0B_REPORT.md` is preserved historical
evidence; `reports/PHASE_1_REPORT.md` records the first implementation and its observed limits.

The selected build is `-PWEB_STREMIO=true`: it replaces, rather than coexists with, legacy
`:stremio`. A normal build without that property remains unchanged until cutover is approved.
