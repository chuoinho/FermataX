# 04 — Security và privacy specification

## 1. Trust boundaries

| Boundary | Trust level |
|---|---|
| Fermata base/player | Trusted application code |
| `modules/web` shell | Trusted application code |
| `web.stremio.com` application | Remote trusted supplier, mutable upstream |
| Addon metadata/stream URLs | Untrusted data |
| OAuth/addon config pages | External remote content |
| External-player intent | Untrusted serialized input |
| Streaming server endpoint | User-configured external service |

Không xem URL hoặc Intent là trusted chỉ vì nó xuất hiện trong Stremio UI.

## 2. Intent validation algorithm

Thực hiện theo thứ tự cố định:

1. Chỉ xét main-frame navigation.
2. Yêu cầu user gesture.
3. Snapshot current WebView URL và xác nhận HTTPS host exact `web.stremio.com`.
4. Giới hạn raw intent length trước parse.
5. Parse với `Intent.URI_INTENT_SCHEME`; catch mọi exception.
6. Không resolve/start package, component, selector hoặc fallback.
7. Reject nếu intent có clip data hoặc unsafe flags có thể ảnh hưởng dispatch.
8. Lấy data URI sau parse.
9. Chỉ nhận scheme case-insensitive `http`/`https`.
10. Reject user-info trong authority, control characters và malformed host.
11. Không copy arbitrary extras/headers vào player.
12. Debounce theo privacy-safe digest + monotonic time.
13. Dispatch đúng một `playItem()` trên main thread khi Activity còn active.

Không log raw intent, raw media URL, query string, magnet, token hoặc cookie.

## 3. Allowed/rejected examples

| Input/result | Decision |
|---|---|
| Intent → `https://cdn.example/video.m3u8` | Allow sau full validation |
| Intent → `http://192.168.1.10/stream` | Allow; cleartext policy hiện có áp dụng |
| Intent → `file:///sdcard/...` | Reject |
| Intent → `content://...` | Reject |
| Intent → `javascript:...` | Reject |
| Intent → `data:text/html,...` | Reject |
| Intent chứa package VLC/MX Player | Ignore package; chỉ xử lý data URI nếu hợp lệ |
| `browser_fallback_url` | Ignore |
| Raw `magnet:` | Reject/consume; Web hiển thị server requirement |
| Non-main-frame intent | Reject/ignore, không handoff |
| Intent không có user gesture | Reject |

## 4. WebView policy

- JavaScript và DOM storage cần bật vì Stremio Web yêu cầu.
- SSL error luôn cancel; không có “continue anyway”.
- Third-party cookie behavior phải được kiểm thử với login, nhưng không log cookie.
- Không thêm một broad JavaScript interface mới.
- Generic `FermataJsInterface` hiện chỉ được dùng cho keyboard/error event; phải giới hạn event type, data length và rate nếu Stremio surface có thể gọi nó.
- Không bật universal access from file URLs; production không dùng file origin.
- Không self-host/local asset origin trong scope.
- `setMixedContentMode` không nới rộng toàn cục chỉ để chạy một stream; media HTTP được handoff cho player.

## 5. Navigation policy

Không áp allowlist cứng cho mọi HTTPS main-frame vì có thể phá OAuth và addon configuration. Thay vào đó:

- chỉ handoff intent khi current top origin là production Stremio;
- reject mọi non-HTTP custom scheme không được xử lý rõ;
- external HTTPS navigation dùng policy Web hiện có;
- không route Stremio YouTube/IPTV sang addon Fermata khác;
- Stremio subsystem vẫn độc lập trong cùng Web surface.

## 6. Route store privacy

Chỉ persist:

- canonical hash route;
- bounded title/subtitle không chứa address;
- last-interaction timestamp;
- schema version.

Reject và không persist:

- query `streamingServerUrl`;
- media URL;
- addon transport URL có credential;
- token/auth key;
- magnet/infohash;
- provider/source selection;
- raw HTML/DOM text.

`toString()` của route/candidate/playable phải redact metadata nhạy cảm.

## 7. Diagnostics

Event enums gợi ý:

- `STREMIO_PAGE_READY`
- `STREMIO_ROUTE_ACCEPTED`
- `STREMIO_ROUTE_REJECTED`
- `STREMIO_HANDOFF_ACCEPTED`
- `STREMIO_HANDOFF_REJECTED`
- `STREMIO_HANDOFF_DUPLICATE`
- `STREMIO_PROVIDER_STALE`
- `STREMIO_SERVER_UNAVAILABLE`

Payload chỉ gồm enum, bool, counter, duration bucket, WebView package/version và host classification. Không gồm URL, title người dùng, query hoặc ID nội dung.

## 8. Threat cases bắt buộc test

- Intent parser confusion/case/encoding.
- Nested `intent://` và fallback URL.
- Oversized URL/extras.
- Duplicate click/replay.
- Stale Activity/Fragment.
- Stale Quick Recent lease sau addon disable/reload.
- OAuth page cố tạo media intent.
- playerFrame hoặc iframe cố tạo top-level intent.
- Renderer crash trong lúc handoff.
- HTTP redirect từ media URL sang blocked local/content scheme theo player policy.
- Log/exception/toString secret leakage.

## 9. License/provenance

- Ghi Stremio Web repo, version/ref audit và GPL-2.0 trong third-party notice/technical docs.
- Hosted model không copy release bundle vào APK.
- Nếu tương lai vendor/fork static assets, đó là architecture change cần license/source-correspondence/CORS/OAuth/service-worker review mới; không được coi là maintenance nhỏ.
