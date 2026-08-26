# Stremio Web Contract Baseline

## Scope

This record captures the observed contract of the production hosted Stremio Web origin in the
existing Fermata WebView shell. It is deliberately evidence-only: it does not add a Web bridge,
scrape the DOM, bundle a Stremio server, or change the legacy addon.

## Device And Runtime

- Device: Redmi Note 8 (`15c36230`), Android 16.
- Android System WebView: `145.0.7632.109`.
- Installed FermataX: package `me.app.fermataX.auto`, version `303`.
- Surface: the existing Web Browser addon and its real `FermataWebView` / `FermataWebClient`.
- Origin: `https://web.stremio.com/#/`.

## Observed Hosted-Web Behavior

1. The hosted page loaded in the real Fermata WebView and rendered the Home/Discover UI.
2. A selected catalog item navigated to the browser-visible canonical hash route:

   ```text
   https://web.stremio.com/#/detail/movie/tt36590417/tt36590417
   ```

   This proves that the existing WebView route observation (`getUrl()` and
   `doUpdateVisitedHistory()` in the proposed shell) can capture a canonical detail route without
   DOM polling or JavaScript state access.
3. The hosted page displayed `Streaming server is not available.` on the device. No stream list
   or external-player handoff was available from that state.
4. A disposable external server was then run on the development host only and connected to the
   phone through `adb reverse tcp:11470 tcp:11470`. The hosted page removed the unavailable-server
   banner and loaded normally with `http://127.0.0.1:11470/` supplied through Stremio Web's own
   `streamingServerUrl` bootstrap parameter. The test server is not part of the app, source tree,
   Gradle graph, APK, or release design.

Evidence screenshots remain local and are intentionally not committed:

- `C:\Users\ttanh\AppData\Local\Temp\stremio-web-0b-home.png`
- `C:\Users\ttanh\AppData\Local\Temp\stremio-0b-details.png`
- `C:\Users\ttanh\AppData\Local\Temp\stremio-0b-server-config-correct.png`

## Upstream Contract Audit

The pinned upstream checkout is `9f2e63b58b5e6ae0a24a1223ca7f0991fef2ba71`.

- `src/routes/MetaDetails/StreamsList/Stream/Stream.js` gets external-player navigation only
  from Core-provided `deepLinks.externalPlayer` (`web`, `openPlayer`, or `playlist`). It does not
  derive a direct final media URL from the page.
- `src/common/usePlayUrl.ts` sends even a direct HTTP URL to
  `core.transport.encodeStream(...)`; magnet handling explicitly requires a ready streaming
  server.
- `src/routes/Settings/Player/usePlayerOptions.ts` writes the external-player setting through
  `core.transport.dispatch(...)`.

Therefore the hosted bundle without a connected Core/streaming-server transport cannot generate
the `deepLinks` required by Phase 2. Intercepting media resource requests or reading Web state
would be a different architecture and is prohibited by this package.

## Security And Lifecycle Result

- The observed details route contains no credential, server endpoint, infohash, or final media
  URL and is suitable for the narrow route-store model after validation.
- No raw URL, token, cookie, manifest configuration, or capability was added to diagnostics.
- Existing `FermataWebClient.newReplacement()` / `FermataWebView.recoverRenderProcess()` remain
  available to retain a Stremio client subtype once a shell is legitimately implemented.

## Contract Status

| Requirement | Result |
|---|---|
| Hosted production origin renders in Fermata WebView | PASS |
| Canonical detail route observable without DOM scraping | PASS |
| External server can become ready through the hosted Web contract | PASS (external test process only) |
| Login/addon configuration flow | NOT OBSERVED (anonymous profile only) |
| Android external-player setting persists through hosted state | NOT OBSERVED (anonymous profile cannot change it) |
| Direct MP4/HLS external-player intent | BLOCKED |
| Magnet/no-server negative behavior | PARTIAL: hosted UI reports unavailable; no selectable stream fixture |
| Renderer recovery subtype | NOT OBSERVED |

The production refactor must not start until an approved, externally provided Stremio Core/server
environment **and a test Stremio profile** demonstrate a real direct MP4/HLS
`deepLinks.externalPlayer` handoff on the device. If that environment is outside scope, the
Web-only plan has a design blocker rather than an implementation task.
