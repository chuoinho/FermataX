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
| Signed Web-only release | Universal APK, approved upload certificate and immutable input | PASS: Phase 7 release audit and device smoke |
| No legacy native payload in Web-only APK | Web-only Gradle graph and APK archive inspection | PASS: legacy `:stremio` is excluded; no `jlibtorrent`/torrent native library is packaged |
| Hosted MediaSession bridge boundary | Origin-scoped document-start message bridge | PARTIAL: direct playback works, but the active MediaSession/DHU claim remains unobserved |

The historical Phase 0B external-player requirements are intentionally not included here.
The reconciled physical acceptance baseline and remaining independent gaps are
recorded in `reports/PHASE_6A_ACCEPTANCE_RECONCILIATION_REPORT.md`.

The repository retains the legacy `modules/stremio` implementation for builds that do not enable
`WEB_STREMIO`; it is not part of the Web-only graph or its release artifact. The Stremio bridge
uses an origin-scoped document-start MediaSession compatibility shim and dispatches only approved
control actions. It does not scrape DOM content, extract stream URLs, read credentials/storage, or
implement a native Stremio player or torrent transport.
