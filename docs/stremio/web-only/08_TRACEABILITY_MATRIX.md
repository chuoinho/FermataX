# 08 - Traceability Matrix

| Requirement | Component | Verification |
|---|---|---|
| Hosted Stremio entry | `StremioWebAddon` | Device UI and generated addon metadata |
| No duplicate addon ID | `settings.gradle`, `modules/web/build.gradle` | `gradlew projects -PWEB_STREMIO=true` |
| Isolated last route | `WebBrowserAddon` protected constructor | Unit build and source audit |
| No Android-app handoff | `StremioWebClient` | Navigation-policy unit test; upstream Install action pending observation |
| Search via voice | `StremioWebAddon`, `StremioWebFragment` | URL contract test, device voice test pending |
| Browser fullscreen/back | `FermataChromeClient`, `WebBrowserFragment` | PASS: P5B5 physical fullscreen/Back follow-up |
| Login/settings persistence | `FermataWebView` WebView storage | PASS: P1C authenticated-session and lifecycle observation |
| No native Stremio runtime | Build graph | Dependency report excludes `:stremio` and `jlibtorrent` |

The historical Phase 0B external-player requirements are intentionally not included here.
The reconciled physical acceptance baseline and remaining independent gaps are
recorded in `reports/PHASE_6A_ACCEPTANCE_RECONCILIATION_REPORT.md`.
