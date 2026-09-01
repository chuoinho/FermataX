# FermataX x Stremio Web Only

**Version:** 1.2
**Status:** AUTHORITATIVE - RELEASE EVIDENCE COMPLETE WITH DECLARED UPSTREAM LIMITATIONS
**Scope:** Host the official Stremio Web application inside FermataX.

Stremio is an inline `FermataWebView` addon, using `https://web.stremio.com/#/` and the upstream
HTML5 `stremio-video` renderer. It is not a native Stremio implementation.

This is the only Stremio implementation in FermataX. It registers one `stremio_fragment` from
`:web`; there is no alternate native addon or build-time implementation switch.

The web-only release graph and its universal APK have passed their focused release audit. P8H
completed the independent Android Auto/DHU media-card control gate. The user-configured
streaming-server/torrent boundary passed with the local-only physical P8G fixture. Multi-audio
selection remains unadvertised by the observed upstream Player, and episode `nexttrack` remains
conditionally unadvertised by the observed MediaSession; neither is represented as a FermataX
feature. See `05_TEST_ACCEPTANCE.md` and
`reports/PHASE_8H_DHU_HOST_CONTROL_REPORT.md` for the final evidence.
