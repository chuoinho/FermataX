# 06 — Migration, rollout và rollback

## 1. Migration principles

- Final APK chỉ dùng Stremio Web.
- Không tự động fallback sang legacy runtime.
- Không cố chuyển native auth/source secrets vào Web storage.
- Không xóa dữ liệu legacy trong cutover release.
- Cutover phải atomic ở build/addon registration level.
- Rollback là một hotfix build từ source revert với versionCode cao hơn, không phải APK downgrade.

## 2. Build-time selection trước cutover

Trong Phase 1–5 cần test hai implementation mà không đăng ký hai addon cùng `stremio_fragment`.

Thiết kế build-time flag tạm thời:

```text
STREMIO_WEB_ONLY=false
  include legacy modules/stremio
  do not register Stremio entry in modules/web

STREMIO_WEB_ONLY=true
  exclude legacy module/addon metadata from build
  register Stremio entry in modules/web
```

Flag chỉ phục vụ development/migration. Không có runtime fallback. Release Phase 6 đặt Web-only làm default và CI kiểm tra không thể vô tình đóng gói cả hai.

Nếu implementation cụ thể của Settings/Gradle làm tăng complexity, có thể dùng một migration branch riêng với two-commit cutover, miễn mọi test build không bao giờ chứa hai addon cùng ID.

## 3. User data handling

### Legacy data

- Giữ nguyên database, SharedPreferences và secure store trong cutover release.
- Không đọc token legacy để inject vào WebView.
- Không migrate addon source list thủ công; Stremio Web dùng account/addon state riêng.
- Cho phép full backup hiện có giữ dữ liệu legacy trong thời gian rollback.
- Phase 8 chỉ xóa code; data cleanup cần retention/consent riêng nếu có rủi ro mất cấu hình.

### New Web data

- Cookie/Web storage do WebView quản lý.
- Native chỉ lưu onboarding và canonical Recent route.
- Route store có schema/version riêng và có thể clear an toàn.

### Migration marker

Chỉ ghi marker sau khi:

1. Stremio Web main frame load thành công.
2. Fragment còn active và đúng addon generation.
3. Không có main-frame SSL/HTTP fatal error.

Marker không khẳng định user đã login hoặc stream đã phát.

## 4. Atomic cutover checklist

1. `modules/web` đăng ký đúng một Stremio addon.
2. Legacy module bị loại khỏi Gradle graph/release artifact.
3. Addon ID/icon/order/voice target giữ ổn định.
4. Existing enabled/on-start preference không trỏ class legacy chết; có mapping class-name preference nếu cần.
5. Dashboard/nav restore không crash khi saved fragment class cũ không tồn tại.
6. Recent/Favorites chứa legacy items không được tự phát; resolver fail safely.
7. Universal APK upgrade install giữ signature/applicationId.
8. Clean install và upgrade đều qua initial flow đúng.

## 5. Rollout stages

### Internal

- Debug universal APK.
- Test accounts và legal sample streams.
- Full phone/DHU matrix.
- Verify WebView packages phổ biến.

### Beta

- Nhóm nhỏ thiết bị/ROM/AA head units.
- Theo dõi page-load, handoff rejected, renderer gone, duplicate playback, login failure.
- Không thu raw URL/title/token.

### Rollback-capable production

- Tag exact HEAD.
- Lưu signed universal APK, checksum, SBOM, release notes và phase reports.
- Source legacy vẫn tồn tại ngoài build để tạo hotfix revert nhanh.
- Không xóa old data.

### Cleanup release

- Chỉ sau stability window.
- Phase 8 xóa source/dependencies/CI legacy.
- Re-run full acceptance và cập nhật backup/restore docs.

## 6. Rollback triggers

- Production Web không load trên tỷ lệ thiết bị đáng kể.
- OAuth/account flow blocker không có workaround trong hosted model.
- External-player intent contract thay đổi làm direct playback hỏng diện rộng.
- Duplicate playback/MediaSession ownership regression.
- Android Auto/DHU crash/ANR hoặc mất navigation.
- Security issue ở intent/JS/navigation boundary.
- Data corruption hoặc upgrade crash.

## 7. Rollback procedure

1. Freeze rollout và ghi affected release/HEAD/Web version/time.
2. Xác nhận trigger bằng privacy-safe reproduction.
3. Revert atomic cutover commit hoặc dùng rollback branch đã diễn tập.
4. Tăng versionCode; không yêu cầu downgrade.
5. Build/sign đúng một universal APK.
6. Chạy minimum rollback CI: mobile/auto units, lint, package, upgrade smoke.
7. Phát hotfix và giữ Web-only branch để root-cause.
8. Không tự restore/overwrite user data; legacy data vẫn còn.

## 8. Forward recovery

Nếu lỗi do upstream intent contract:

- update sanitized fixture;
- sửa parser trong giới hạn HTTP(S) fail-closed;
- không start external package;
- chạy Phase 0B/2/5/7 gates lại.

Nếu lỗi do OAuth/CORS/origin:

- xác nhận production origin và WebView behavior;
- không tự chuyển sang appassets/self-host trong hotfix nếu chưa có architecture/security review;
- fallback UX có thể mở help/error, không chuyển runtime.

## 9. Phase 8 deletion safety

Trước khi xóa legacy:

- tag rollback source;
- xác nhận signed hotfix build từ tag;
- xác nhận không còn active user data migration cần code legacy;
- dependency scan chứng minh jlibtorrent/Core probe không có consumer;
- backup/restore không instantiate class legacy;
- repository search và CI guard cập nhật.

Sau deletion, rollback từ source tag vẫn phải tạo được APK có versionCode mới nếu cần emergency recovery.
