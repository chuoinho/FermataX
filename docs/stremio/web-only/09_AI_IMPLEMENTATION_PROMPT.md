# 09 — Master prompt cho AI triển khai

Sao chép toàn bộ prompt bên dưới cho AI coding agent có quyền đọc/ghi repository FermataX.

---

## PROMPT

Bạn là senior Android/Java architect và coding agent chịu trách nhiệm triển khai **FermataX × Stremio Web Only** trong repository `chuoinho/FermataX`.

### Mục tiêu duy nhất

Thay subsystem Stremio native hiện tại bằng một addon WebView chuyên dụng dùng duy nhất `https://github.com/Stremio/stremio-web` qua production origin `https://web.stremio.com/#/`.

Stremio addon mới phải nằm trong `modules/web`, cùng dynamic feature với Web Browser và YouTube. Không tạo dependency từ `modules/stremio` sang `modules/web` và không copy Web shell.

FermataX tiếp tục sở hữu physical player, MediaService/MediaSession, Android Auto/DHU, SmartTop và Quick Recent. Stremio Web sở hữu account, addons, catalog, search, library, calendar, details và stream selection.

### Hard constraints

1. Chỉ một universal APK cho phone và Android Auto/DHU.
2. Không dùng `stremio-core-kotlin`, Core Android/native bridge, `stremio-native`, `stremio-android`, local streaming server hoặc `jlibtorrent` trong final build.
3. Không viết lại UI/account/catalog/search/library/addon protocol/metadata.
4. Không vendor/self-host Stremio Web assets; dùng production origin.
5. Không thêm broad JavaScript/Core bridge, DOM polling hoặc DOM scraper.
6. Không route Stremio YouTube/IPTV sang addon Fermata khác; Stremio là subsystem độc lập.
7. Direct external-player URL chỉ chấp nhận final `http/https` sau validation; không launch package/component/fallback.
8. Fermata là playback owner sau handoff; không có dual player/MediaSession.
9. SmartTop Current lấy title/play-pause/progress từ Fermata playback snapshot/timeline thật.
10. Quick Recent chỉ OPEN exact Stremio `#/detail/...` hoặc `#/metadetails/...`; không provider selection, stream resolution, torrent preparation, autoplay hoặc `DefaultRecent` mirror.
11. Quick Recent phải dùng generic provider boundary với current context, lifecycle lease validation và `HANDLED/NOT_HANDLED`.
12. Không automatic runtime fallback sang legacy.
13. Stremio-specific production code mục tiêu ≤500 LOC; shared Web hooks ≤80 LOC; Quick Recent shared contract ≤320 LOC, không gian lận formatting/validation.
14. Một phase chỉ PASS khi test/audit/acceptance thực tế PASS; compile-only không đủ.

### Tài liệu authority phải đọc trước khi sửa

Đọc đầy đủ, theo thứ tự:

1. `docs/stremio/web-only/README.md`
2. `01_ARCHITECTURE.md`
3. `02_COMPONENT_API_DESIGN.md`
4. `03_IMPLEMENTATION_PLAN.md`
5. `04_SECURITY_PRIVACY.md`
6. `05_TEST_ACCEPTANCE.md`
7. `06_MIGRATION_ROLLOUT_ROLLBACK.md`
8. `07_OPERATIONS_UPSTREAM.md`
9. `08_TRACEABILITY_MATRIX.md`
10. `10_SOURCE_REFERENCES.md`

Đọc mọi `AGENTS.md` áp dụng trước khi edit. Audit working tree và bảo toàn mọi thay đổi không liên quan của user; không reset/checkout phá hủy.

### Source baseline tham khảo

- FermataX audited baseline: `b416d9c0bf499d92e4d0543ac4d844a8ce89c8d7`.
- Stremio Web audited baseline: `9f2e63b58b5e6ae0a24a1223ca7f0991fef2ba71`.
- Stremio Web release observed: `v5.0.0-beta.39`.

Không reset về baseline. Ghi HEAD hiện tại và audit mọi thay đổi sau baseline trước khi quyết định.

### Kiến trúc phải triển khai

```text
:fermata base contracts <- :web dynamic feature
                           ├─ Web Browser
                           ├─ YouTube
                           └─ Stremio Web
```

Các lớp Stremio dự kiến:

- `me.aap.fermata.addon.web.stremio.StremioWebAddon`
- `StremioWebFragment`
- `StremioWebClient`
- `StremioIntentPlayable`
- `StremioRouteStore`

Shared base contracts dự kiến:

- `QuickRecentProvider`
- `QuickRecentProviderLease`
- `QuickRecentCandidate`
- typed `SmartTopQuickRecent`

Tên có thể chỉnh theo convention repo nhưng semantics/dependency direction không được đổi.

### External-player validator

Chỉ handoff khi tất cả đúng:

1. main-frame;
2. user gesture;
3. current top WebView URL là HTTPS host exact `web.stremio.com`;
4. request scheme `intent`;
5. `Intent.parseUri(..., Intent.URI_INTENT_SCHEME)` thành công;
6. final data URI là `http` hoặc `https`;
7. không user-info/control char/oversize;
8. không duplicate trong debounce window;
9. Activity/fragment vẫn active.

Luôn ignore package, component, selector, clipData, flags nguy hiểm, `browser_fallback_url` và arbitrary extras. Không bao giờ `startActivity(parsedIntent)`. Invalid input phải fail-closed, không log raw URL/intent/token.

### Quick Recent contract

- Provider trả candidate chứa addonClass, lifecycleGeneration, opaqueId, bounded title/subtitle và timestamp.
- Candidate không chứa URL/media/magnet/token/provider/source.
- `AddonManager` tạo/validate lease.
- Load/open timeout và isolated.
- Current context loại exact active Stremio route nếu có.
- Click provider candidate gọi provider open.
- `HANDLED` chỉ khi exact canonical route được mở.
- `NOT_HANDLED`/stale/failure chỉ refresh/drop; tuyệt đối không gọi `playItem()`.
- Existing MediaLib Recent playable behavior vẫn giữ nguyên.
- Tối đa ba Quick Recent rows và không phụ thuộc AA width.

### Thứ tự phase bắt buộc

Thực hiện đúng:

```text
0D → 0A → 0B → 1 + 1S → 2 → 3 → 4 → 5 → 6 → 7
→ one rollback-capable release → 8
```

Phase 1S có thể song song 1, không block 2/3 nhưng phải PASS trước 5/6. Phase 4/5/6 yêu cầu Phase 2 và 3 PASS.

#### 0D

Cut over documentation authority; đánh dấu Core/native docs cũ superseded/historical. Không sửa production code.

#### 0A

Audit HEAD, dependency/SBOM/license, APK size/native libs/ABI, WebKit, CI legacy steps và threat model. Không xóa dependency khi chưa chứng minh consumer.

#### 0B

Feasibility spike trên real WebView: production Web load/login/addons/search/details, Android external-player option/intent callback, hash route observation, renderer recovery và magnet/no-server. Thu sanitized fixtures. Nếu direct handoff hoặc route capture không khả thi mà không sửa Stremio Web, STOP và báo blocker; không mở rộng scope.

#### 1

Thêm Web shell hooks tối thiểu; tạo Stremio addon trong `modules/web`; build-time selection tránh duplicate addon ID; giữ Web Browser/YouTube behavior.

#### 1S

Thêm generic Quick Recent provider/lease/current-context/HANDLED-NOT_HANDLED và Stremio OPEN-only provider; không mirror MediaLib.

#### 2

Direct MP4/HLS vertical slice qua validated intent → `StremioIntentPlayable` → `MainActivityDelegate.playItem()`; exactly one playback owner.

#### 3

Server-dependent boundary: no-server magnet negative PASS; external test server HTTP(S) conditional PASS; không thêm server/torrent dependency.

#### 4

Cookie/session/route restore/onboarding/back/fullscreen/keyboard/renderer/network lifecycle; không native UI clone/DOM bridge.

#### 5

Preserve Fermata contracts: SmartTop, Quick Recent, voice search-only, MediaSession, Android Auto/DHU, Web/YouTube/TV/player regression và universal APK.

#### 6

Atomic build cutover: Web addon default, legacy module ngoài build, old bytes preserved, migration marker sau successful Web load, không automatic fallback.

#### 7

Full CI/security/soak/APK/native/size/license/upstream smoke và rollback drill.

#### Rollback-capable release

Ship Web-only APK; giữ source legacy ngoài build, tag exact HEAD, lưu signed universal APK/checksum/SBOM. Rollback bằng hotfix versionCode cao hơn từ revert commit.

#### 8

Sau stability window, xóa legacy source/module/tests/resources, jlibtorrent/FrostWire/Core probe/CI checkout nếu không còn consumer; giữ data theo retention policy; full CI lại.

### Test/gate bắt buộc

Sau mỗi phase chạy targeted tests, rồi trước phase PASS chạy các suite liên quan. Trước release chạy tối thiểu:

```bash
./gradlew testMobileDebugUnitTest :whisper:testMobileDebugUnitTest \
  --no-daemon --no-parallel --stacktrace
./gradlew testAutoDebugUnitTest \
  --no-daemon --no-parallel --stacktrace
./gradlew :web:testMobileDebugUnitTest :web:testAutoDebugUnitTest \
  --no-daemon --no-parallel --stacktrace
./gradlew :fermata:lintMobileDebug :fermata:lintAutoDebug \
  --no-daemon --no-parallel --stacktrace
./gradlew :fermata:packageAutoDebugUniversalApk \
  --no-daemon --no-parallel --stacktrace
git diff --check
```

Xác nhận task names ở HEAD trước khi dùng. Không bỏ test vì task đoán sai; tìm task tương ứng.

Test bắt buộc gồm intent malicious matrix, route grammar, provider lease/current context, HANDLED/NOT_HANDLED, no DefaultRecent mirror, direct MP4/HLS E2E, no-server magnet, phone/DHU, renderer/network recovery, duplicate click và exactly-one universal APK.

### Phase report format

Sau mỗi phase tạo `docs/stremio/web-only/reports/PHASE_<ID>_REPORT.md` với:

- goal/scope;
- baseline/final HEAD exact;
- changed/deleted files;
- ownership/dependency audit;
- commands/tests và kết quả;
- mobile/Auto/DHU;
- security/privacy;
- lifecycle/concurrency;
- APK/native/size;
- rollback;
- known limitations;
- `Exit gate: PASS` hoặc `FAIL`.

Không bắt đầu phase tiếp theo nếu report hiện tại FAIL.

### Commit discipline

- Mỗi phase là một hoặc vài commit nhỏ, reviewable.
- Không trộn cleanup ngoài scope.
- Không force/reset/destructive checkout.
- Không push/merge/release nếu user chưa cho phép; local commits chỉ khi workflow hiện tại đã được ủy quyền.
- Sau mỗi commit ghi exact SHA trong report.

### Definition of Done

Hoàn thành chỉ khi:

- final APK chỉ có Stremio Web subsystem;
- không legacy Core/native/server/jlibtorrent trong build;
- một universal APK;
- account/addons/catalog/search/library/details hoạt động;
- direct HTTP(S) phát bằng Fermata;
- SmartTop dùng player thật;
- Quick Recent exact OPEN-only, không stream/autoplay/DefaultRecent;
- phone/AA/DHU, lifecycle, security, CI và rollback drill PASS;
- limitations local torrent, precise progress sync và subtitle handoff được ghi trung thực;
- docs/source/tests/dependency graph nhất quán.

Hãy bắt đầu bằng audit read-only và Phase 0D. Báo cáo phát hiện trước khi sửa production code. Sau đó thực hiện tuần tự, tự sửa lỗi trong scope, dừng khi gặp blocker cần thay đổi kiến trúc hoặc quyền hạn.

## END PROMPT
