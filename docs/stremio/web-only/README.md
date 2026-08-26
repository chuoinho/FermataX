# FermataX × Stremio Web Only — Bộ tài liệu kỹ thuật authoritative

**Phiên bản tài liệu:** 1.0
**Ngày:** 2026-08-26
**Trạng thái:** AUTHORITATIVE
**Phạm vi:** Tích hợp duy nhất [`Stremio/stremio-web`](https://github.com/Stremio/stremio-web) vào [`chuoinho/FermataX`](https://github.com/chuoinho/FermataX).

## 1. Quyết định cuối cùng

Stremio trong FermataX là một preset WebView chuyên dụng nằm ngay trong dynamic feature `modules/web`, tương tự cách YouTube đang cùng tồn tại với Web Browser.

- `modules/web` chứa `StremioWebAddon`, `StremioWebFragment` và `StremioWebClient`.
- `StremioWebFragment` host `https://web.stremio.com/#/`.
- Stremio Web sở hữu account, addons, catalog, search, library, calendar, details và stream selection.
- FermataX sở hữu Activity/Fragment host, physical player, MediaService/MediaSession, Android Auto/DHU, SmartTop và Quick Recent.
- External-player intent chỉ được đổi thành media URL `http/https` đã kiểm tra rồi phát bằng Fermata.
- Một universal APK duy nhất được dùng cho phone và Android Auto/DHU.

Không dùng `stremio-core-kotlin`, Core Android/native bridge, `stremio-native`, `stremio-android`, local streaming server, `jlibtorrent` hoặc một implementation Stremio riêng.

## 2. Điều chỉnh quan trọng so với plan sơ bộ

Không tạo `implementation project(':web')` từ `modules/stremio`. CI hiện có architecture guard cấm addon module import implementation của sibling module. Cách đúng và ít code hơn là đăng ký Stremio trực tiếp trong `modules/web/build.gradle`, sau đó loại bỏ `modules/stremio` cũ.

Điều này giữ dependency graph một chiều:

```text
:fermata (base contracts) <- :web (Web Browser + YouTube + Stremio Web)
```

Base `:fermata` không được import class cụ thể từ `:web`.

## 3. Source baseline đã audit

| Repository | Ref audit | Thời điểm commit |
|---|---|---|
| FermataX | `b416d9c0bf499d92e4d0543ac4d844a8ce89c8d7` | 2026-08-25 |
| Stremio Web | `9f2e63b58b5e6ae0a24a1223ca7f0991fef2ba71` | 2026-08-25 |
| Stremio Web release | `v5.0.0-beta.39` | 2026-07-27 |

Các ref trên là baseline để so sánh, không phải yêu cầu AI reset repository về commit cũ. Khi triển khai phải ghi lại HEAD thực tế và re-audit diff kể từ baseline.

## 4. Chuỗi authority

Thứ tự ưu tiên khi có mâu thuẫn:

1. Yêu cầu trong tài liệu này và `08_TRACEABILITY_MATRIX.md`.
2. `01_ARCHITECTURE.md` và `02_COMPONENT_API_DESIGN.md`.
3. `03_IMPLEMENTATION_PLAN.md`.
4. Security/test/migration/operations documents.
5. Source code và test đã được xác nhận ở HEAD triển khai.
6. Tài liệu Stremio Core/native/server cũ chỉ là historical evidence.

Trong repo, Phase 0D phải tạo `docs/stremio/README.md` và đánh dấu toàn bộ tài liệu Core/native/server cũ là `SUPERSEDED` hoặc `HISTORICAL`; không được để hai kiến trúc cùng authoritative.

## 5. Danh mục tài liệu

| Tài liệu | Mục đích |
|---|---|
| `01_ARCHITECTURE.md` | Ownership, dependency graph và runtime flow |
| `02_COMPONENT_API_DESIGN.md` | Thiết kế lớp, interface và data contract |
| `03_IMPLEMENTATION_PLAN.md` | Kế hoạch phase 0D–8, gate và effort |
| `04_SECURITY_PRIVACY.md` | Threat model, URL validation, secret/log policy |
| `05_TEST_ACCEPTANCE.md` | Unit/instrumentation/E2E/AA/CI matrix |
| `06_MIGRATION_ROLLOUT_ROLLBACK.md` | Cutover, dữ liệu cũ, rollout và rollback |
| `07_OPERATIONS_UPSTREAM.md` | Theo dõi upstream Web và release operations |
| `08_TRACEABILITY_MATRIX.md` | Requirement → component → test → gate |
| `09_AI_IMPLEMENTATION_PROMPT.md` | Prompt đầy đủ giao cho AI triển khai |
| `10_SOURCE_REFERENCES.md` | Source evidence và link audit |

## 6. Phạm vi chức năng

### Hoàn thành bằng Stremio Web

- Login/account/guest.
- Addon install/config/remove.
- Discover, catalog, search, library, calendar.
- Details, season, episode và stream selection.
- Continue Watching khi playback do Stremio Web sở hữu.
- Web/player-frame content chạy trong WebView.

### Hoàn thành bằng Fermata

- Direct HTTP(S) media handoff.
- Physical playback, MediaSession và notification.
- Playerbar, SmartTop Current, play/pause/progress.
- Android Auto/DHU presentation.
- Quick Recent mở lại đúng details/video route.

### Giới hạn bắt buộc

- Không có local torrent/magnet engine.
- Không có local transcoding/archive/NZB runtime.
- Magnet chỉ hoạt động khi user tự cấu hình một streaming server bên ngoài trong Stremio Web.
- External handoff không đảm bảo truyền subtitle addon sang Fermata.
- Fermata playback position không được đồng bộ liên tục ngược vào Continue Watching của Stremio nếu không thêm một bridge mới; bridge đó nằm ngoài scope.
- Không tuyên bố full desktop-client parity cho các giới hạn trên.

## 7. Code budget

| Nhóm | Production code mới dự kiến |
|---|---:|
| Stremio-specific trong `modules/web` | 350–500 dòng |
| Hook dùng chung trong Web shell | 40–80 dòng |
| Quick Recent provider contract dùng chung | 220–320 dòng |
| **Tổng production code đầy đủ** | **610–900 dòng** |

Mốc 350–500 dòng chỉ đúng cho phần Stremio-specific. Quick Recent OPEN-only cần một boundary generic thay vì lạm dụng `PlayableItem`; phần shared contract này được tính riêng.

Đổi lại, kiến trúc loại bỏ khoảng 519 file trong `modules/stremio`, gồm 344 file Java production cùng `jlibtorrent` và các native ABI artifact.
