# Kế hoạch Triển khai Toàn diện: Chuyển đổi Toàn bộ Giao diện Fermata Auto sang Theme Kính (Glass Theme) Thuần Java

> **Dành cho kỹ sư & agentic workers:** YÊU CẦU KỸ NĂNG: Sử dụng `superpowers:subagent-driven-development` hoặc `superpowers:executing-plans` để thực thi từng nhiệm vụ tuần tự. Các bước sử dụng cú pháp checkbox (`- [ ]`) để theo dõi tiến độ.

**Mục tiêu (Goal):** Đồng bộ hóa 100% tất cả các màn hình, thanh điều khiển, hộp thoại, menu ngữ cảnh, trang cài đặt, thư viện đa phương tiện và các addon của Fermata Auto sang phong cách giao diện Kính (Glassmorphism) chuẩn Dashboard, xây dựng bằng các View thuần Java (Pure Java View Components) và tái sử dụng ~80% các thành phần từ CarStream, tuyệt đối bảo toàn nguyên vẹn hai theme Classic và Material You.

**Kiến trúc (Architecture):** Xây dựng các widget hạt nhân trong `me.aap.fermata.ui.glass.widget` (`GlassSlider`, `GlassHardwareUtils`, `GlassQuickSheet`, `GlassRadarView`, `GlassCardView`, `GlassSwitch`, `GlassDialogBuilder`) được chuyển thể từ CarStream. Tích hợp có điều kiện theo cơ chế `if (isGlassTheme) { ... }` vào các điểm neo Controller/Mediator của Fermata thay vì sửa layout XML dùng chung.

**Công nghệ & Công cụ (Tech Stack):** Java 17, Android SDK (API 28–36), Android Auto CarActivity SDK, AndroidX, Robolectric & JUnit 4, CarStream decompiled engine (`build/decompiled/carstream`).

**Đặc tả kỹ thuật (Spec):** [`docs/superpowers/specs/2026-10-13-comprehensive-glass-theme-spec.md`](file:///E:/antigravity/fermata/docs/superpowers/specs/2026-10-13-comprehensive-glass-theme-spec.md)

---

## Các Ràng buộc Bắt buộc (Global Constraints)

1. **Cách ly Theme (Theme Invariant):** Không bao giờ làm ảnh hưởng đến `THEME_CLASSIC = 6` hoặc `THEME_MATERIAL_YOU = 7`. Mọi logic vẽ thẻ kính, đổi màu, bo viền đều phải nằm sau điều kiện kiểm tra `isGlassTheme()`.
2. **Không sửa đổi Layout XML dùng chung (No XML Layout Breakage):** Giữ nguyên các tệp layout XML gốc (`main_activity_left.xml`, `media_item_list_layout.xml`, `body_layout.xml`, `control_panel_view.xml`). Mọi thành phần Glass đều được tạo, gán hoặc thay thế bằng code Java động.
3. **Phần cứng & Tần số khung hình (60FPS Automotive Performance):** Sử dụng bo góc phần cứng qua GPU (`ViewOutlineProvider.setRoundRect`), hạn chế tối đa việc tạo mới đối tượng `Paint` hoặc `Path` trong hàm `onDraw()`.
4. **Chuẩn thao tác Ô tô (Automotive Ergonomics):** Kích thước vùng chạm tối thiểu từ $56\text{ dp}$ đến $64\text{ dp}$; độ tương phản văn bản cao đạt chuẩn an toàn lái xe.
5. **Gói cài đặt Đơn nhất (Single Universal APK):** Tất cả thay đổi phải tương thích trong cùng một bản Universal APK cho cả Điện thoại (Chân dung) và Ô tô Android Auto (Ngang/Vuông).

---

## Tiêu điểm Rà soát & Kiểm thử (Review Focus)

- **Trường hợp 1 (Chạm thanh Player không làm dừng video):** Khi chạm 1 lần để bật/tắt thanh điều khiển trong video fullscreen, video phải tiếp tục phát liên tục 100%, không xảy ra hiện tượng vô tình pause video.
- **Trường hợp 2 (Tiến trình Player đồng bộ thời gian thực):** Thanh `GlassSlider` trên playerbar và dashboard phải chạy mượt mà từng giây và khớp chính xác tuyệt đối với video/nhạc đang phát, không giật lùi vị trí.
- **Trường hợp 3 (Toàn bộ Dropdown/Popup là Kính mờ):** Khi bấm chọn Theme, Tốc độ tua, Hẹn giờ ngủ, Chất lượng YouTube hoặc Menu ngữ cảnh bài hát, khung hiển thị phải là Glass Sheet bo viền kính mờ 1dp và có dải sáng Cyan trên mục được chọn.
- **Trường hợp 4 (Trang Cài đặt & Sub-pages đồng bộ):** Khi lướt sâu vào bất kỳ mục cài đặt nào (Âm thanh, Video, Mạng, Nâng cao), tất cả các thanh trượt, switch gạt và hộp văn bản đều mang phong cách Glass Card đồng nhất.
- **Trường hợp 5 (Các Addon & Dialog con):** Các hộp thoại cấu hình nguồn (Audiobookshelf, OPDS, Podcast, TV M3U) và các addon (TikTok capsule, Web search bar) đều phải mang phong cách Glass.

---

## CÁC NHIỆM VỤ TRIỂN KHAI CHI TIẾT (TASKS)

---

### Giai đoạn 1: Nền tảng Widget Kính Hạt nhân (Core Glass Widgets - Port từ CarStream)

#### Nhiệm vụ 1.1: Port `GlassHardwareUtils` từ `com.carstream.tech.launcher.Ui`
- **Mục tiêu:** Cung cấp tiện ích bo góc phần cứng qua GPU (`clipRound`) và các hàm tạo nhanh `GradientDrawable` viền kính không răng cưa.
- **Tệp tạo mới:**
  - `fermata/src/main/java/me/aap/fermata/ui/glass/theme/GlassHardwareUtils.java`
  - `fermata/src/test/java/me/aap/fermata/ui/glass/theme/GlassHardwareUtilsTest.java`
- **Nguồn CarStream:** `build/decompiled/carstream/sources/com/carstream/tech/launcher/Ui.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết unit test `GlassHardwareUtilsTest.java` kiểm tra logic tạo `roundStroke`, tính toán bán kính DP và gán `ViewOutlineProvider`.
  - [ ] **Bước 2:** Chạy test để xác nhận test thất bại: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.theme.GlassHardwareUtilsTest"`
  - [ ] **Bước 3:** Hiện thực `GlassHardwareUtils.java` với các phương thức tĩnh:
    - `clipRound(View view, float cornerRadiusPx)`
    - `round(int color, float radiusPx)`
    - `roundStroke(int color, float radiusPx, int strokeWidthPx, int strokeColor)`
  - [ ] **Bước 4:** Chạy lại test để xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): port GlassHardwareUtils with GPU clipRound from CarStream"`

#### Nhiệm vụ 1.2: Port & Hoàn thiện `GlassSlider` từ `com.carstream.tech.launcher.Slider`
- **Mục tiêu:** Xây dựng thanh trượt tương tác cảm ứng bằng Pure Java thay thế `SeekBar` của Android cho cả Player, Equalizer và Settings.
- **Tệp tạo mới:**
  - `fermata/src/main/java/me/aap/fermata/ui/glass/widget/GlassSlider.java`
  - `fermata/src/test/java/me/aap/fermata/ui/glass/widget/GlassSliderTest.java`
- **Nguồn CarStream:** `build/decompiled/carstream/sources/com/carstream/tech/launcher/Slider.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết unit test `GlassSliderTest.java` kiểm tra: tỉ lệ tiến trình `ratio` từ `0.0f` đến `1.0f`, sự kiện callback `OnSliderChangeListener`, logic chặn `requestDisallowInterceptTouchEvent`.
  - [ ] **Bước 2:** Chạy test để xác nhận test thất bại: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.widget.GlassSliderTest"`
  - [ ] **Bước 3:** Hiện thực `GlassSlider.java`:
    - Vẽ rãnh nền kính mờ (`drawRoundRect`).
    - Vẽ dải tiến trình đã phát (Played Track) bằng dải chuyển màu Cyan (`0xFF00E5FF` -> `0xFF2979FF`).
    - Vẽ con chạy hình tròn (Thumb) có đổ bóng sáng mờ (`setShadowLayer`) và viền kính.
    - Xử lý cảm ứng `onTouchEvent`: Bắt chính xác tọa độ X, hỗ trợ kéo trượt mượt mà.
  - [ ] **Bước 4:** Chạy lại test để xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): implement GlassSlider pure Java view ported from CarStream"`

#### Nhiệm vụ 1.3: Hiện thực `GlassSwitch` & `GlassCardView` thuần Java
- **Mục tiêu:** Cung cấp container thẻ kính mờ có viền sẵn và công tắc chuyển trạng thái phong cách kính thay thế `SwitchCompat`.
- **Tệp tạo mới:**
  - `fermata/src/main/java/me/aap/fermata/ui/glass/widget/GlassCardView.java`
  - `fermata/src/main/java/me/aap/fermata/ui/glass/widget/GlassSwitch.java`
  - `fermata/src/test/java/me/aap/fermata/ui/glass/widget/GlassWidgetTest.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test `GlassWidgetTest.java` kiểm tra trạng thái Toggle của `GlassSwitch` và thuộc tính nền của `GlassCardView`.
  - [ ] **Bước 2:** Chạy test để xác nhận thất bại.
  - [ ] **Bước 3:** Hiện thực `GlassCardView` (kế thừa `FrameLayout`, tự động gán nền kính `GlassDrawables.createGlassCardDrawable`) và `GlassSwitch` (vẽ nút gạt tròn trượt mượt qua lại với màu Cyan rực rỡ khi BẬT).
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): add GlassCardView and GlassSwitch pure Java widgets"`

---

### Giai đoạn 2: Trình phát Fullscreen & Điều khiển Player (Player & Control Panel)

#### Nhiệm vụ 2.1: Hoàn thiện `ControlPanelView` thành Floating Glass Player Capsule
- **Mục tiêu:** Thay thế giao diện thanh điều khiển player mặc định bằng thẻ kính nổi, tích hợp `GlassSlider` thời gian thực, đảm bảo chống lỗi chạm gián đoạn video.
- **Tệp sửa đổi:**
  - `fermata/src/main/java/me/aap/fermata/ui/view/ControlPanelView.java`
  - `fermata/src/main/java/me/aap/fermata/ui/view/ControlPanelSeekView.java`
- **Tệp kiểm thử:**
  - `fermata/src/test/java/me/aap/fermata/ui/view/ControlPanelViewGestureTest.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test kiểm tra:
    - Khi `isGlassTheme == true`: `ControlPanelView` sử dụng nền kính nổi bo góc 24dp.
    - Cử chỉ chạm: `onSingleTapUp` hiển thị panel ngay, `onDoubleTap` tua 10 giây mà không gọi `togglePlayPause`.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Cập nhật `ControlPanelView.java`:
    - Nếu `isGlassTheme()`: gán nền thẻ kính nổi cách đáy, thay `SeekBar` bằng `GlassSlider`.
    - Kết nối `GlassSlider` với `FermataServiceUiBinder` để cập nhật vị trí thời gian thực từ timeline interpolation.
    - Duy trì logic debounce 400ms và grace period 500ms cho cử chỉ chạm fullscreen.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): overhaul ControlPanelView into floating Glass Player with GlassSlider"`

#### Nhiệm vụ 2.2: Đồng bộ Giao diện Phụ đề & Hộp thông tin Video
- **Mục tiêu:** Chuyển màn hình duyệt phụ đề hai danh sách và hộp thông tin chi tiết video sang giao diện Kính.
- **Tệp sửa đổi:**
  - `fermata/src/main/java/me/aap/fermata/ui/view/SubtitlesView.java`
  - `fermata/src/main/java/me/aap/fermata/ui/fragment/SubtitlesFragment.java`
  - `fermata/src/main/java/me/aap/fermata/ui/view/VideoInfoView.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test kiểm tra việc áp dụng Glass Cards cho danh sách phụ đề và tay kéo chia đôi màn hình (`sub_split_handle`).
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Cập nhật `SubtitlesView.java` và `VideoInfoView.java`:
    - Bọc danh sách phụ đề vào 2 thẻ Glass Card.
    - Đổi tay cầm chia đôi màn hình (`sub_split_handle`) sang màu Cyan với viền kính 1dp.
    - Định dạng lại các thẻ thông tin codec, bitrate, resolution trong `VideoInfoView` thành các ô Glass Chip hiện đại.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): synchronize SubtitlesView and VideoInfoView with Glass Bento layout"`

---

### Giai đoạn 3: Hệ thống Menu, Popup, Dropdown & Hộp thoại Toàn App

#### Nhiệm vụ 3.1: Chuyển đổi `OverlayMenuView` & `OverlayMenuItemView` thành Glass Sheet
- **Mục tiêu:** Biến toàn bộ menu popup, dropdown chọn giá trị, và menu ngữ cảnh trong toàn bộ ứng dụng thành Glass Sheet bo viền kính mờ với highlight Cyan.
- **Tệp sửa đổi:**
  - `depends/utils/src/main/java/me/aap/utils/ui/menu/OverlayMenuView.java`
  - `depends/utils/src/main/java/me/aap/utils/ui/menu/OverlayMenuItemView.java`
- **Tệp kiểm thử:**
  - `fermata/src/test/java/me/aap/fermata/ui/glass/GlassMenuTest.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test xác nhận: Khi hiển thị `OverlayMenuView` trong môi trường Glass, nền view nhận Drawable thẻ kính, các mục con nhận màu chữ trắng và highlight Cyan khi `isSelected()` hoặc hover.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Cập nhật `OverlayMenuView.java` & `OverlayMenuItemView.java`:
    - Khi `isGlassTheme()`: gán `setBackground(GlassDrawables.createGlassCardDrawable(ctx, 16dp, isNight))`.
    - Thêm vệt sáng viền Cyan `0x2500E5FF` khi một item được chọn hoặc được focus bởi núm xoay D-pad ô tô.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): transform OverlayMenuView and OverlayMenuItemView into Glass Sheet"`

#### Nhiệm vụ 3.2: Xây dựng `GlassDialogBuilder` & Đồng bộ `GenericDialogFragment`
- **Mục tiêu:** Chuẩn hóa toàn bộ hộp thoại thông báo, xác nhận, thêm nguồn addon sang giao diện Kính.
- **Tệp tạo mới:**
  - `fermata/src/main/java/me/aap/fermata/ui/glass/dialog/GlassDialogBuilder.java`
- **Tệp sửa đổi:**
  - `depends/utils/src/main/java/me/aap/utils/ui/fragment/GenericDialogFragment.java`
  - `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test kiểm tra `MainActivityDelegate.createDialogBuilder(ctx)` trả về `GlassDialogBuilder` khi ở theme Glass.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Hiện thực `GlassDialogBuilder.java`:
    - Khung dialog dạng thẻ kính nổi bo góc 20dp.
    - Nút Hủy dạng kính xám, nút Đồng ý dạng Glass Action Pill viền sáng Cyan.
    - Cập nhật `GenericDialogFragment` bọc nội dung vào thẻ kính.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): implement GlassDialogBuilder and align GenericDialogFragment"`

---

### Giai đoạn 4: Cài đặt Toàn diện, Equalizer & Trình tìm xe (Settings, EQ & Car Finder)

#### Nhiệm vụ 4.1: Đồng bộ hóa Toàn bộ 6 Loại Dòng Cài đặt trong `PreferenceView`
- **Mục tiêu:** Biến toàn bộ các mục cài đặt con (Switch, Slider, Input, File, List, Submenu) trong `SettingsFragment` thành các thành phần Kính đồng bộ.
- **Tệp sửa đổi:**
  - `depends/utils/src/main/java/me/aap/utils/pref/PreferenceView.java`
- **Tệp kiểm thử:**
  - `fermata/src/test/java/me/aap/fermata/ui/glass/GlassPreferenceViewTest.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test xác nhận `PreferenceView` tạo View con dạng Glass khi bật theme Glass.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Cập nhật `PreferenceView.java`:
    - `setBooleanPreference`: Thay switch mặc định bằng `GlassSwitch`.
    - `setIntPreference` / `setFloatPreference`: Thay SeekBar bằng `GlassSlider`.
    - `setStringPreference`: Định dạng `EditText` thành ô nhập liệu kính mờ viền sáng Cyan.
    - `setFilePreference`: Định dạng nút duyệt file thành Glass Pill.
    - `setPreferenceSet`: Thêm hiệu ứng gợn sáng kính và mũi tên `>` màu Cyan.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): transform PreferenceView sub-components to pure Glass controls"`

#### Nhiệm vụ 4.2: Chuyển đổi Bộ Equalizer sang Giao diện Kính Neon (`AudioEffectsScreenView`)
- **Mục tiêu:** Cung cấp trải nghiệm chỉnh âm thanh cao cấp với đồ thị curve neon Cyan và các cần gạt tần số dạng `GlassSlider`.
- **Tệp sửa đổi:**
  - `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsScreenView.java`
  - `fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsBandView.java`
  - `fermata/src/main/java/me/aap/fermata/ui/view/EqualizerCurveView.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test kiểm tra các thành phần của EQ nhận đúng màu tokens của Glass Theme khi khởi tạo.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Cập nhật:
    - `EqualizerCurveView`: Đổi đường vẽ cong sang màu Cyan Neon `0xFF00E5FF`, thêm gradient mờ phát sáng phía dưới đường cong.
    - `AudioEffectsBandView`: Thay cần gạt bằng `GlassSlider` đặt dọc.
    - Các thẻ Bass Boost, Loudness: Bọc trong thẻ `GlassCardView` với công tắc `GlassSwitch`.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): overhaul AudioEffectsScreenView with neon curve and GlassSlider bands"`

#### Nhiệm vụ 4.3: Port `GlassRadarView` và Cập nhật Giao diện Tìm xe (`CarFinderFragment`)
- **Mục tiêu:** Tích hợp radar tìm xe cao cấp từ CarStream vào tính năng Car Finder và loại bỏ toàn bộ mã màu cứng Navy cũ.
- **Tệp tạo mới:**
  - `fermata/src/main/java/me/aap/fermata/ui/glass/carfinder/GlassRadarView.java`
- **Tệp sửa đổi:**
  - `fermata/src/main/java/me/aap/fermata/carfinder/CarFinderFragment.java`
  - `fermata/src/main/java/me/aap/fermata/carfinder/CarFinderSettingsDialog.java`
- **Nguồn CarStream:** `build/decompiled/carstream/sources/com/carstream/tech/core/timxe/RadarView.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test kiểm tra `GlassRadarView` tính toán góc xoay và vòng quét radar chính xác.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Port `RadarView.java` từ CarStream sang `GlassRadarView.java`, đổi màu các vòng tròn sang dải Cyan/Electric Blue.
  - [ ] **Bước 4:** Cập nhật `CarFinderFragment.java` sử dụng `GlassRadarView` và các token của Glass Theme, cập nhật `CarFinderSettingsDialog` thành Glass Sheet.
  - [ ] **Bước 5:** Chạy test xác nhận PASS.
  - [ ] **Bước 6:** Commit: `git commit -m "feat(glass): port GlassRadarView and modernize CarFinder with Glass tokens"`

---

### Giai đoạn 5: Thư viện MediaLib, Trình duyệt Tệp, Shell & Các Addon

#### Nhiệm vụ 5.1: Đồng bộ `MediaItemView`, Trạng thái Rỗng & Trình duyệt File VFS
- **Mục tiêu:** Thẻ bài hát có hiệu ứng kính mờ, bài đang phát sáng Cyan rực rỡ, danh sách tệp VFS và bảng trạng thái rỗng đồng bộ.
- **Tệp sửa đổi:**
  - `fermata/src/main/java/me/aap/fermata/ui/view/MediaItemView.java`
  - `fermata/src/main/java/me/aap/fermata/ui/fragment/MediaLibFragment.java`
  - `depends/utils/src/main/java/me/aap/utils/ui/fragment/FilePickerFragment.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test kiểm tra trạng thái kích hoạt của `MediaItemView` khi bài hát đang phát trong theme Glass.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Cập nhật `MediaItemView.java`:
    - Áp dụng nền thẻ kính bo góc 12dp cho từng item.
    - Khi đang phát (`item.equals(cur)`): Tô màu chữ Cyan, phát sáng viền và đổi màu thanh `LinearProgressIndicator` sang Cyan.
    - Cập nhật `FilePickerFragment` và bảng rỗng `media_items_state_panel` sang phong cách Glass Card.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): modernize MediaItemView, empty state panel, and FilePickerFragment"`

#### Nhiệm vụ 5.2: Hoàn thiện Dải Header Điện thoại, Banner Nổi & Menu Đáy
- **Mục tiêu:** Đồng bộ dải điều khiển mini trên đỉnh điện thoại, banner ủng hộ nổi và thanh điều hướng đáy.
- **Tệp sửa đổi:**
  - `fermata/src/main/java/me/aap/fermata/ui/view/PhonePlaybackCarHeaderController.java`
  - `fermata/src/main/java/me/aap/fermata/ui/view/PhoneSupportBannerController.java`
  - `fermata/src/main/java/me/aap/fermata/ui/view/PhoneBottomMenuController.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test kiểm tra dải header Phone mode và banner nhận drawable kính trong theme Glass.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Cập nhật:
    - `PhonePlaybackCarHeaderController`: Gán nền Glass Header Card, tích hợp mini `GlassSlider` cho seekbar.
    - `PhoneSupportBannerController`: Gán nền Glass Pill nổi 999dp.
    - `PhoneBottomMenuController`: Gán màu tab active Cyan phát sáng.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): modernize phone header strip, support banner, and bottom navigation"`

#### Nhiệm vụ 5.3: Đồng bộ Trợ lý Giọng nói, Màn hình Thiết lập Ban đầu & Các Addon Web
- **Mục tiêu:** Hoàn thiện nốt các góc khuất còn lại: Voice sheet, Setup wizard, thanh địa chỉ Web và capsule TikTok.
- **Tệp sửa đổi:**
  - `fermata/src/main/java/me/aap/fermata/ui/activity/VoiceRecognitionSession.java`
  - `fermata/src/main/java/me/aap/fermata/ui/fragment/InitialSetupFragment.java`
  - `modules/web/src/main/java/me/aap/fermata/addon/web/WebToolBarMediator.java`
  - `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokFloatingNavView.java`
- **Các bước thực hiện:**
  - [ ] **Bước 1:** Viết test kiểm tra Voice sheet hiển thị dạng Glass Card và thanh URL web nhận style capsule.
  - [ ] **Bước 2:** Chạy test xác nhận thất bại.
  - [ ] **Bước 3:** Cập nhật:
    - `VoiceRecognitionSession`: Xây dựng Glass Voice Sheet với vòng tròn micro viền Cyan phát sáng.
    - `InitialSetupFragment`: Chuyển giao diện thiết lập lần đầu sang thẻ kính trung tâm.
    - `WebToolBarMediator`: Đổi ô nhập URL thành Glass Search Capsule bo tròn 2 đầu.
    - `TikTokFloatingNavView`: Áp dụng nền kính mờ trong suốt 80% cho thanh điều khiển 3 nút.
  - [ ] **Bước 4:** Chạy test xác nhận PASS.
  - [ ] **Bước 5:** Commit: `git commit -m "feat(glass): synchronize voice assistant sheet, initial setup wizard, and web addons"`

---

### Giai đoạn 6: Kiểm thử Tích hợp & Kiểm chứng Thiết bị Thực tế (Live Hardware Verification)

#### Nhiệm vụ 6.1: Biên dịch Toàn bộ và Chạy Kiểm thử Đơn vị Hệ thống
- **Mục tiêu:** Đảm bảo toàn bộ test suite của cả ứng dụng và các module đều PASS 100%, không phát sinh lỗi biên dịch hay suy giảm hiệu năng.
- **Lệnh thực thi:**
  - [ ] Chạy kiểm thử đơn vị: `./gradlew testAutoDebugUnitTest`
  - [ ] Xác nhận toàn bộ bài test đều PASS.

#### Nhiệm vụ 6.2: Build Bản Universal APK & Sideload Lên Thiết bị Thật
- **Mục tiêu:** Cài đặt trực tiếp lên điện thoại Redmi Note 8 (`15c36230`) và kiểm chứng cả chế độ Điện thoại (Dọc) lẫn mô phỏng Ô tô (Ngang).
- **Lệnh thực thi:**
  - [ ] Build Universal APK: `./gradlew :fermata:assembleAutoDebug`
  - [ ] Gỡ bản cũ và cài bản mới qua ADB:
    ```bash
    adb -s 15c36230 uninstall me.aap.fermata.auto
    adb -s 15c36230 install -r fermata/build/outputs/apk/auto/debug/fermata-auto-debug.apk
    ```
  - [ ] Kiểm chứng thực tế: Chụp screenshot kiểm tra độ mượt của `GlassSlider`, kiểm tra Fullscreen Player không bị dừng video khi chạm, kiểm tra trang Cài đặt và Menu ngữ cảnh.
