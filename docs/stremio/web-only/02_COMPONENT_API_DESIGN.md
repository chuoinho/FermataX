# 02 - Component Design

## Production classes

- `StremioWebAddon`: addon metadata, dedicated `stremio_web` preferences, hosted home URL and
  voice-search entry.
- `StremioWebFragment`: fragment ID, Stremio search route and disabled desktop toggle.
- `StremioWebClient`: retains the generic WebView client behavior while rejecting only main-frame
  `intent:` and `stremio:` routes. This prevents a hosted Stremio install action from launching an
  arbitrary Android app; HTTPS navigation and player resources remain untouched.
- `WebBrowserAddon`: reusable protected constructor with a preference namespace and initial URL.
- `WebBrowserFragment`: factory hooks for client/chrome-client and polymorphic URL persistence.

`StremioWebFragment` uses the existing `FermataChromeClient` unchanged. Its only client
specialization is the narrow external-scheme block above; it does not intercept media, inject
JavaScript, extract URLs, or change WebView renderer recovery.

## Persistence

Native preferences store only the latest hosted route in the `stremio_web` namespace. Android
WebView owns cookies, local storage and Stremio settings. FermataX must not persist or log account
tokens, cookies, addon credentials, streaming-server credentials, magnet links or media URLs.

## Voice

Voice routes to `https://web.stremio.com/#/search?search=<encoded query>`. It opens search only;
it must not select a stream or autoplay.
