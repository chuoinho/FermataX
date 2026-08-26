# 09 - Implementation Constraints

Implement only an inline hosted Stremio WebView addon. Reuse `WebBrowserAddon`,
`WebBrowserFragment`, `FermataWebView`, `FermataWebClient` and `FermataChromeClient`.

Do not add Stremio Core/native repositories, Kotlin Core, jlibtorrent, streaming server, VLC/native
renderer, external-player handoff, media URL interception or a JavaScript playback bridge. Do not
modify `aauto.aar`. Keep code small, lifecycle-safe and free of cookie/token/URL logging.

Do not declare physical playback, fullscreen or lifecycle PASS without a real device observation.
