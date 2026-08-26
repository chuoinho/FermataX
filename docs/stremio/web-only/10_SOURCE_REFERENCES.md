# 10 - Source References

- [Stremio Web](https://github.com/Stremio/stremio-web): hosted product and HTML5 player owner.
- `modules/web/.../WebBrowserAddon.java`: preference-backed Web addon shell.
- `modules/web/.../WebBrowserFragment.java`: lifecycle, menu and Back policy.
- `modules/web/.../FermataWebView.java`: WebView settings, cookies and renderer recovery.
- `modules/web/.../FermataWebClient.java`: generic page lifecycle and diagnostics.
- `modules/web/.../stremio/StremioWebClient.java`: narrow main-frame external-scheme safety policy.
- `modules/web/.../FermataChromeClient.java`: custom-view fullscreen and activity video mode.
- `modules/web/.../yt/YoutubeFragment.java`: fullscreen reference only, not a playback model to copy.

Legacy `modules/stremio` is a temporary build alternative during rollout, not a source dependency
or runtime fallback for the Web-only addon.
