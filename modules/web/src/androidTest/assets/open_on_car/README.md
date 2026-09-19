# Native callback gate — NOT RUN

This is a manual device fixture, not a passing instrumentation test. No device/WebView version
has been recorded and no APK was installed for Task 4. Android's standard androidTest asset
directory is used without build changes. Serve this directory from a controlled HTTP(S) origin;
do not use file URLs (the transport policy rejects them). A static server may return 501
for `/post`; that exercises POST followed by a main-frame HTTP error. A controlled server
which returns an HTML document for POST is required for the successful-POST variant.

Before claiming SPA support, record device OS, WebView package/version, app commit/build, host
type, and observed native callbacks without logging URLs, form bodies, cookies, or tokens.

1. Open `navigation.html` on PHONE with mode OFF. Enable: no baseline request.
2. Click GET B: one source candidate; attached CAR receives one typed load. Phone remains local.
3. After B finishes, run push/replace/hash and Back/Forward. Verify `doUpdateVisitedHistory`
   delivery and ordering on the device; assert one candidate for each safe same-document URL.
4. Click the iframe link: no top-level candidate. Submit GET then POST: known GET may sync,
   POST/unknown/new-document history must not. Never replay form bodies.
5. Reload through app refresh, then page reload: only explicit known-GET reload is eligible.
6. Repeat with switch OFF->ON between request/start and with a replacement car host during
   debounce: no old-origin navigation may retarget the new session.
7. Rotate/resume phone or simulate renderer loss: restored/recovery page is baseline only;
   next explicit safe navigation re-arms the new source generation.
8. Disconnect/hide source during queued delivery: zero late loads. Main-frame errors/SSL must
   cancel pending work; automatic recovery/retry cannot emit another candidate.
9. Receiver: unattached returns NOT_READY; stale returns CANCELLED; dispatch is not page success.
   Repeat the same URL across tokens: native callbacks lack IDs, so ambiguous page ACK is withheld.

Known conservative boundaries: unknown document methods, unproven reloads, baseline-only documents,
and recovery redirects are not promoted to GET. No bridge/script fallback is used to fill gaps.
Actual device ordering, SPA coverage, restore/replacement callbacks and CAR behavior remain NOT RUN.
