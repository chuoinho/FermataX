# 03 — Implementation plan chi tiết

## 1. Thứ tự bắt buộc

```text
0D → 0A → 0B → 1 + 1S → 2 → 3 → 4 → 5 → 6 → 7
→ one rollback-capable release → 8
```

- Không skip phase.
- Phase 1S có thể chạy song song Phase 1 và không block Phase 2/3.
- Phase 1S phải PASS trước Phase 5/6.
- Phase 4/5/6 chỉ bắt đầu khi Phase 2 và Phase 3 PASS.
- Compile thành công không đủ để PASS.
- Không có automatic runtime fallback giữa Web và legacy.

## 2. Báo cáo bắt buộc sau mỗi phase

Tạo `docs/stremio/web-only/reports/PHASE_<ID>_REPORT.md` gồm:

1. Goal và scope thực tế.
2. Baseline HEAD và final HEAD exact.
3. Files changed/deleted.
4. Ownership/dependency audit.
5. Tests đã chạy và output tóm tắt.
6. Phone/Auto/DHU impact.
7. Security/privacy impact.
8. Lifecycle/concurrency impact.
9. APK/native/size impact nếu có.
10. Rollback procedure.
11. Known limitations.
12. `Exit gate: PASS` hoặc `Exit gate: FAIL` cùng bằng chứng.

Nếu FAIL, dừng chain và sửa trong cùng phase; không chuyển nợ sang phase sau trừ khi authority document cho phép rõ ràng.

## 3. Phase 0D — Documentation authority cutover

**Effort:** 0.5–1 ngày.

### Work

- Tạo `docs/stremio/README.md` làm authority index.
- Đưa bộ tài liệu này vào `docs/stremio/web-only/`.
- Phân loại tài liệu cũ: `SUPERSEDED NORMATIVE`, `HISTORICAL EVIDENCE`, `REFERENCE`.
- Cập nhật `MASTER_CONTEXT.md` hoặc file context tương đương.
- Gỡ mọi tuyên bố Core/native/server cũ là target hiện hành.
- Ghi rõ hosted Web model và limitations.

### Gate

- Chỉ còn một chuỗi authority không mơ hồ.
- Search toàn repo không còn tài liệu nào tuyên bố kế hoạch Core/native là active.
- Không thay production code trong phase này.

## 4. Phase 0A — Provenance, license và baseline

**Effort:** 1 ngày.

### Work

- Ghi FermataX HEAD, Stremio Web HEAD/release/version audit thực tế.
- Xác nhận Stremio Web GPL-2.0 và production dùng hosted origin, không distribute bundle.
- Lập SBOM/dependency snapshot trước migration.
- Đo APK universal size và liệt kê native libraries/ABI hiện tại.
- Xác định toàn bộ consumer của `jlibtorrent` và FrostWire Maven repo.
- Ghi minSdk/target/compile/NDK/WebKit versions.
- Audit CI hiện tại: checkout `stremio-core-java`, `coreprobe`, native/16KB gates và universal APK packaging.
- Lập threat model baseline cho WebView/JS interface/intent handoff.

### Gate

- Có reproducible baseline report.
- Biết chính xác dependency/native artifact nào chỉ thuộc legacy Stremio.
- Không xóa dependency khi chưa chứng minh không có consumer khác.

## 5. Phase 0B — Web contract feasibility spike

**Effort:** 2 ngày.

### Work

- Chạy Stremio Web production trong `FermataWebView` trên phone và DHU.
- Xác nhận login/guest, cookie persistence, addon page, search, details.
- Xác nhận Android UA làm xuất hiện `Allow choosing` external player.
- Thu sanitized fixtures cho external-player intent của direct MP4 và HLS.
- Xác nhận intent đi qua callback WebView nào: main-frame override, new-window hoặc cả hai.
- Xác nhận renderer recovery giữ subclass client.
- Xác nhận `doUpdateVisitedHistory()`/`getUrl()` quan sát được hash details route.
- Xác nhận hành vi magnet khi không có streaming server và khi có server test bên ngoài.
- Không viết Core bridge, server hoặc DOM scraper để làm spike PASS.

### Deliverables

- `WEB_CONTRACT_BASELINE.md`.
- Sanitized intent/route fixtures.
- Feasibility test hoặc debug-only harness có thể xóa sau.

### Gate

- Direct URL có thể handoff mà không sửa Stremio Web.
- Canonical details route có thể lưu mà không polling DOM.
- Login/addon config không bị navigation policy chặn.
- Nếu một trong ba điều trên FAIL, dừng và báo blocker; không tự mở rộng sang native/Core.

## 6. Phase 1 — Thin Web shell

**Effort:** 2 ngày.

### Work

- Thêm protected constructor/factory hooks nhỏ trong Web shell.
- Tạo package `addon.web.stremio` và các lớp shell.
- Tạo build-time selection để test Web implementation mà không đăng ký hai addon cùng ID.
- Thêm addon metadata Stremio vào `modules/web` khi Web mode được bật.
- Dùng prefs namespace `stremio_web` và official home URL.
- Implement back/refresh/voice/onboarding.
- Không thay YouTube/Web Browser behavior.

### Gate

- Architecture guards PASS.
- `:fermata` không import class Stremio/Web cụ thể.
- Web Browser/YouTube regression tests PASS.
- Stremio Web shell mở được trên phone và DHU.
- Stremio-specific code ở phase này nằm trong budget.

## 7. Phase 1S — Quick Recent provider contract

**Effort:** 2–3 ngày, song song Phase 1.

### Work

- Thêm `QuickRecentProvider`, lease, candidate, context và `HANDLED/NOT_HANDLED`.
- Snapshot/validate lease trong `AddonManager` tương tự provider patterns hiện có.
- Đổi SmartTop Quick Recent presentation thành typed entry, không ép mọi entry là `PlayableItem`.
- Giữ behavior MediaLib Recent hiện tại qua playable adapter.
- Stremio provider trả tối đa một canonical details route.
- Click provider-bound entry chỉ mở route.
- Stale/disabled provider trả `NOT_HANDLED`, refresh UI và không play.
- Không mirror candidate vào `DefaultRecent`.

### Gate

- Existing Quick Recent playable tests PASS.
- Provider isolation, timeout, lease invalidation và current-context tests PASS.
- Stremio click không gọi stream resolution, torrent preparation hoặc `playItem()`.
- AA mọi width vẫn có tối đa ba dòng Quick Recent khi có dữ liệu.

## 8. Phase 2 — Direct playback vertical slice

**Effort:** 2 ngày.

### Work

- Implement intent parser/validator và duplicate suppression.
- Handoff direct MP4 và HLS sang `StremioIntentPlayable`.
- Dùng `MainActivityDelegate.playItem()`; không launch external package.
- Giữ Web details page/back stack.
- Xác nhận physical player/MediaSession/notification ownership.
- Ghi privacy-safe diagnostics theo reason enum.

### Gate

- Direct MP4 và HLS E2E PASS trên phone.
- Ít nhất một direct URL PASS trên DHU.
- Exactly one playback start cho một click.
- Invalid package/fallback/file/content/javascript/data intents bị chặn.
- Web renderer/fragment recreation không dừng active playback.

## 9. Phase 3 — Server-dependent/torrent boundary slice

**Effort:** 1–2 ngày.

Phase này không thêm torrent engine. Nó chứng minh boundary Web-only hoạt động trung thực.

### Work

- Test magnet/no-server: Stremio Web báo server unavailable, Fermata không nhận magnet.
- Test server ngoài được user cấu hình trong Stremio Web: server trả HTTP(S), cùng handoff phát bằng Fermata.
- Test default loopback `127.0.0.1:11470` khi không có server: không retry vô hạn, không crash.
- Test server mất kết nối giữa selection và playback.
- UI phân biệt unsupported/no-server với media decode failure.

### Gate

- Negative no-server path PASS.
- Conditional external-server HTTP(S) path PASS trong test environment.
- APK không chứa P2P/server/native torrent dependency.
- Không log magnet/infohash/server credentials.

## 10. Phase 4 — Web UI, state và lifecycle

**Effort:** 1–2 ngày.

### Work

- Cookie/session persistence qua cold/warm start.
- Last canonical route restore.
- Hash route capture qua history callback và pause snapshot.
- One-time external-player onboarding.
- Fullscreen/back/refresh/input behavior.
- Renderer crash recovery và network retry.
- Xác nhận playerFrame ở lại WebView.
- Không thêm native clone cho catalog/library/details.

### Gate

- Login/catalog/search/library/details/addons PASS.
- Rotation/background/foreground/renderer recovery PASS.
- Không có DOM polling hoặc JS Core bridge.
- Unsupported parity được mô tả đúng trong UI/help.

## 11. Phase 5 — Preserve Fermata contracts

**Effort:** 2 ngày.

### Work

- SmartTop Current title/play-pause/progress từ `PlaybackSnapshot`/timeline thật.
- Không full rebind/flicker khi timeline tick.
- Quick Recent OPEN-only và current-context exclusion.
- Voice search không autoplay dù `play=true`.
- Android Auto/DHU focus, keyboard, back, shutdown/resume.
- Một MediaSession và một playback owner.
- Regression Web Browser/YouTube/TV/player/Recent/Favorites.

### Gate

- Mobile/Auto unit suites PASS.
- AA/DHU matrix PASS.
- SmartTop/Quick Recent contract tests PASS.
- Không sửa Previous ở player/MediaSession ngoài SmartTop.
- Universal APK contract PASS.

## 12. Phase 6 — Transactional cutover

**Effort:** 1 ngày.

### Work

- Chuyển default build authority sang Stremio entry trong `modules/web`.
- Loại `modules/stremio` legacy khỏi build/addon registry, nhưng chưa xóa source trong phase này.
- Không đóng gói `jlibtorrent` của legacy module trong APK Web-only.
- Preserve legacy prefs/database bytes để rollback; không cố chuyển token sang Web.
- Hiển thị login/onboarding rõ ràng sau cutover.
- Viết migration marker chỉ sau lần Web shell load thành công.
- Không runtime fallback tự động.

### Gate

- APK chỉ đăng ký đúng một addon ID `stremio_fragment`.
- Clean install và upgrade install đều PASS.
- Failed Web load không corrupt legacy data hoặc marker.
- Git revert/cutover hotfix procedure đã diễn tập.

## 13. Phase 7 — Hardening và release readiness

**Effort:** 2 ngày.

### Work

- Full unit/lint/architecture/security CI.
- Web production contract smoke.
- Soak test navigation/playback/background/renderer recovery.
- APK size/native library/secret/log audit.
- Dependency and license report.
- Document upstream compatibility baseline.
- Chuẩn bị release notes và known limitations.

### Gate

- Tất cả test trong `05_TEST_ACCEPTANCE.md` PASS.
- Không crash/ANR/dual playback trong soak.
- APK có đúng một universal artifact.
- Rollback package/commit/runbook sẵn sàng.

## 14. One rollback-capable release

- Phát hành Web-only với source legacy vẫn được giữ ngoài build.
- Tag exact source và lưu signed universal APK/checksum/SBOM.
- Rollback là hotfix có versionCode cao hơn tạo từ revert commit; không downgrade APK và không bật runtime fallback.
- Theo dõi crash, handoff failure, login/addon config và Web contract.
- Chỉ sang Phase 8 sau cửa sổ ổn định đã định trước và không có blocker.

## 15. Phase 8 — Legacy cleanup

**Effort:** 1–2 ngày.

### Work

- Xóa source/test/resource không còn dùng trong `modules/stremio`.
- Xóa module khỏi settings/build graph.
- Xóa `jlibtorrent` aliases và FrostWire Maven repo nếu dependency scan xác nhận không còn consumer.
- Xóa `coreprobe`, checkout `stremio-core-java` và CI step legacy nếu không còn mục đích khác.
- Xóa flags/migration scaffolding chỉ dùng cho cutover.
- Giữ legacy user data theo retention policy hoặc công cụ backup; không tự xóa credential/data khó phục hồi.
- Cập nhật docs/architecture guards/SBOM.

### Gate

- `rg` không còn production import/reference legacy Stremio/Core/torrent.
- Dependency graph không còn jlibtorrent/Core probe.
- Full CI/universal APK/smoke PASS.
- Documentation authority và repository tree khớp final architecture.

## 16. Effort tổng

- Minimal Web shell + direct handoff: khoảng 10 engineer-days.
- Full scope gồm Quick Recent generic boundary, migration, security, AA và release hardening: **14–18 engineer-days**.
- Lịch thực tế khoảng ba tuần cho một kỹ sư, hoặc ngắn hơn nếu Phase 1 và 1S chạy song song bởi hai người.
