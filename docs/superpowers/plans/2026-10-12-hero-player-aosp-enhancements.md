# Hero Player Card AOSP Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai Phương án B (AOSP Car Media Standard) cho Hero Media Player Card trên FermataX: tích hợp Micro Progress Bar 2dp cho Tier 3, tách biệt Scrubber triệt tiêu lỗi quẹt nhầm tua nhạc, căn giữa đối xứng cụm nút điều khiển, bo góc Squircle viền mờ 1dp cho Album Art, và đồng bộ tiến trình phát.

**Architecture:** Nâng cấp cấu trúc phân tầng trong `hero_player_card.xml`, điều phối hiển thị trong `ModernDashboardController.java` dựa trên 3 Tier của `DynamicDashboardGeometryEngine`, cập nhật `PlaybackCardBinding.java` để truyền tiến trình vào Micro Progress Bar, duy trì vùng chạm Decoupled Touch Targets (≥76dp xe / ≥48dp phone) và Zero-Scroll trên Android Auto.

**Tech Stack:** Android Java, AndroidX ConstraintLayout / LinearLayout, Vector Drawables, Robolectric Unit Tests, Gradle.

**Spec / References:**
- `2026-10-12-modern-responsive-design-spec.md`
- `fermata-modern-responsive-design-audit.md`
- Visual Artifacts: `hero_media_option_b_preview.md`, `dashboard_modern_2col_showcase.md`, `automotive_screen_sizes_showcase.md`

## Global Constraints
- **TUYỆT ĐỐI KHÔNG COMMIT HOẶC PUSH**: Toàn bộ thay đổi phải giữ nguyên trong working tree, không tạo git commit hay push theo yêu cầu bắt buộc của người dùng.
- **Dọn dẹp cơ chế cũ trước khi làm mới**: Dọn sạch các thuộc tính layout thừa, khoảng cách cứng cũ trước khi bổ sung view mới.
- **Decoupled Touch Targets**: Vùng chạm tương tác ≥ 76dp trên xe (Android Auto), ≥ 48dp trên Phone. Hitbox không được chồng lấn.
- **Zero-Scroll trên Android Auto**: Toàn bộ nội dung Hero Card phải co giãn trong ngân sách chiều cao được cấp, không làm xuất hiện cuộn dọc trên dashboard xe.
- **100% Pass Unit Tests**: Cả 2 bộ test suite `:fermata:testMobileDebugUnitTest` và `:fermata:testAutoDebugUnitTest` phải đạt 100% pass.

---

### Task 1: Clean Up & Enhance Layout XML (Accidental Seeking Protection, Micro Progress Bar & Squircle Border)

**Files:**
- Modify: `fermata/src/main/res/layout/hero_player_card.xml`
- Modify / Create: `fermata/src/main/res/drawable/hero_album_art_outline.xml` (hoặc background drawable bo góc có viền hairline 1dp)

**Interfaces / Views:**
- Produces: `@id/hero_micro_progress` (`ProgressBar`, height 2dp, max 1000, progressBarStyleHorizontal, visibility GONE by default).
- Modifies: `@id/hero_album_art` có `clipToOutline="true"`, background hoặc viền 1dp `#1AFFFFFF`.
- Modifies: Thứ tự và khoảng cách của `@id/hero_transport_controls` và `@id/hero_progress` để tách biệt an toàn, triệt tiêu lỗi quẹt nhầm tua nhạc (Accidental Seeking Hazard).

- [ ] **Step 1: Viết test kiểm tra sự tồn tại và thuộc tính của `hero_micro_progress` và layout hierarchy**
- [ ] **Step 2: Chạy test để xác nhận test FAIL trước khi sửa XML**
- [ ] **Step 3: Dọn dẹp layout cũ và cập nhật `hero_player_card.xml`**
  - Thêm `hero_micro_progress` sát đáy thẻ với chiều cao 2dp, `layout_gravity="bottom"`.
  - Cập nhật `hero_album_art` với thuộc tính bo góc và viền hairline mờ.
  - Tách khoảng cách giữa Transport Controls và Seekbar Scrubber.
- [ ] **Step 4: Chạy lại test để xác nhận test PASS**

---

### Task 2: Controller Multi-Tier Adaptation & Playback Binding Progress Sync

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/ModernDashboardController.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/control/PlaybackCardBinding.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/ModernDashboardControllerTest.java`

**Interfaces / Logic:**
- `ModernDashboardController.java`:
  - Khởi tạo và tham chiếu `heroMicroProgress = root.findViewById(R.id.hero_micro_progress)`.
  - Trong `applyHeroLayoutTier(HeroLayoutTier tier, ...)`:
    - `tier == HeroLayoutTier.SHORT_LOW_HEIGHT`: Ẩn seekbar lớn, hiển thị `heroMicroProgress` (`View.VISIBLE`).
    - `tier == HeroLayoutTier.WIDE_AND_TALL` hoặc `NARROW_AND_TALL`: Hiển thị seekbar lớn (khi đủ điều kiện), ẩn `heroMicroProgress` (`View.GONE`). Căn giữa cụm `heroTransportControls` trong Wide mode.
    - Cập nhật `enforceTimelineVisibility()` để tôn trọng trạng thái của `heroMicroProgress`.
- `PlaybackCardBinding.java`:
  - Cho phép bind thêm `microProgressBar(heroMicroProgress)`.
  - Khi timeline snapshot cập nhật tiến trình (`updateTimeline` / `render`), đồng bộ `microProgressBar.setProgress(progress)`.

- [ ] **Step 1: Viết failing test trong `ModernDashboardControllerTest.java` kiểm tra `heroMicroProgress` chuyển đổi giữa các Tier**
- [ ] **Step 2: Chạy test để xác nhận FAIL**
- [ ] **Step 3: Triển khai logic trong `ModernDashboardController.java` và `PlaybackCardBinding.java`**
- [ ] **Step 4: Chạy test để xác nhận PASS**

---

### Task 3: Regression Test Suite, Universal APK Build & Physical Device Verification

**Files:**
- Test: Toàn bộ suite `:fermata:testMobileDebugUnitTest` và `:fermata:testAutoDebugUnitTest`
- Device: Redmi Note 8 (`15c36230`)

- [ ] **Step 1: Chạy toàn bộ unit test suites**
  - `.\gradlew.bat :fermata:testMobileDebugUnitTest`
  - `.\gradlew.bat :fermata:testAutoDebugUnitTest`
  - Đảm bảo 100% tests pass.
- [ ] **Step 2: Build universal release APK bằng `./build.sh`**
- [ ] **Step 3: Cài đặt và nghiệm thu trên thiết bị thật `15c36230`**
  - Cài đặt APK mới: `adb -s 15c36230 install -r dist/FermataX-2.0.1.apk`
  - Khởi chạy ứng dụng: `adb -s 15c36230 shell monkey -p me.app.fermataX.auto -c android.intent.category.LAUNCHER 1`
  - Chụp ảnh màn hình nghiệm thu thực tế và lưu vào artifact directory.
- [ ] **Step 4: Đối chiếu kết quả và báo cáo cho người dùng**
