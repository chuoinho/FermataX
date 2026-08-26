# 07 — Operations và upstream maintenance

## 1. Upstream dependencies

Runtime contract phụ thuộc:

- `https://web.stremio.com` availability và behavior;
- Stremio Web Android platform detection;
- external-player `intent://` shape;
- hash routes cho details/search;
- Android System WebView behavior;
- Fermata Web shell/player contracts.

Không phụ thuộc Gradle/Maven trực tiếp vào Stremio packages trong hosted model.

## 2. Compatibility record

Mỗi FermataX release ghi:

```text
FermataX HEAD/version:
Stremio Web observed version/commit if available:
Stremio Web production smoke timestamp:
Android System WebView package/version:
Phone devices:
DHU profiles:
External-player fixtures digest:
Known limitations:
```

Không lưu URL media hoặc account data trong record.

## 3. Release-time contract smoke

Chạy ngay trước release:

1. Home/intro load.
2. Guest/email login flow.
3. Addons page và một config flow.
4. Search → details → episode.
5. Android external player option tồn tại.
6. Direct media intent được handoff.
7. Back về details.
8. Quick Recent exact route.
9. Magnet/no-server semantics.
10. Renderer/network recovery.

Smoke thất bại là release blocker, không “known flaky” nếu liên quan direct playback hoặc login.

## 4. Upstream change response

### Route change

- Update route grammar chỉ sau khi quan sát/cite source mới.
- Giữ backward parser cho route đang persist nếu an toàn.
- Migrate/clear invalid stored route, không mở URL tùy ý.

### Intent change

- Thu sanitized fixture mới.
- So sánh semantic final URI, không phụ thuộc string slicing.
- Update tests trước implementation.
- Không nới scheme/package/fallback policy.

### Player setting change

- Update onboarding text/path.
- Không dùng DOM automation để click setting.
- Nếu Stremio bỏ external player contract, dừng và re-plan; không scrape internal Core state.

### WebView regression

- Record WebView package/version.
- Reproduce trên current stable và platform-supported version.
- Dùng existing renderer/retry diagnostics.
- Không bypass SSL hoặc mở file universal access.

## 5. Observability

Metrics privacy-safe:

- main page ready/failure counts;
- renderer gone/recovery counts;
- intent accepted/rejected reason;
- duplicate suppressed;
- Quick Recent stale lease;
- handoff-to-first-player-state latency bucket;
- host mode phone/projection;
- WebView package/version.

Không thu:

- titles/history;
- media URLs/domains;
- account/addon/token;
- magnet/infohash;
- full stack trace có URL nếu chưa redact.

## 6. Operational health thresholds

Đặt threshold thực tế sau beta baseline. Các điều kiện luôn là blocker:

- security rejection bypass;
- duplicate player start;
- dual MediaSession ownership;
- upgrade crash;
- universal APK contract violation;
- widespread login/page-load failure.

## 7. Maintenance cadence

- Trước mỗi FermataX release: full contract smoke.
- Khi Stremio Web publish release lớn hoặc đổi player/routes: Phase 0B subset + targeted regression.
- Khi Android System WebView major update: Web shell/intent/keyboard/fullscreen smoke.
- Sau security incident: rotate fixture corpus, review logs và re-run threat tests.

Không auto-update code hoặc parser dựa trên remote content.

## 8. CI cleanup

Final CI phải:

- không checkout `stremio-core-java`;
- không chạy `coreprobe` nếu không còn consumer;
- có Web/Stremio intent/route/Quick Recent targeted tests;
- giữ architecture, lint, whitespace và universal APK gates;
- inspect APK để chặn libtorrent/Core legacy quay lại.

## 9. Future changes requiring a new ADR

- Bundling/self-hosting Stremio Web assets.
- Thêm native JS/Core bridge.
- Bundled streaming server/torrent engine.
- Sync Fermata progress về Stremio account.
- Subtitle handoff từ Stremio addon sang Fermata.
- Cho phép custom scheme hoặc external packages.

Những thay đổi này không được triển khai như “fix nhỏ” trên architecture hiện tại.
