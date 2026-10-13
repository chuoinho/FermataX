# Đặc tả Kỹ thuật Toàn diện: Hệ thống Giao diện Kính (Glass Theme) & Đặc tả Port Code từ CarStream

- **Mã tài liệu:** `SPEC-FERMATA-GLASS-2026-10-13`
- **Nhánh Git:** `feat/aa-glass-theme`
- **Mục tiêu:** Đồng bộ 100% giao diện toàn ứng dụng Fermata Auto sang Theme Kính (Glassmorphism) chuẩn Dashboard, xây dựng bằng Pure Java View Components, kế thừa và tái sử dụng ~80% các thành phần từ CarStream.
- **Trạng thái:** Bản chốt chính thức (Approved Architecture)

---

## 1. TỔNG QUAN KIẾN TRÚC & NGUYÊN TẮC THIẾT KẾ (DESIGN PRINCIPLES)

### 1.1 Nguyên tắc Cách ly Tuyệt đối (Absolute Theme Isolation)
- Toàn bộ giao diện Kính chỉ được kích hoạt khi:
  ```java
  boolean isGlass = activity.getPrefs().isGlassTheme(activity.isCarActivity());
  ```
- Tuyệt đối **không sửa đổi cấu trúc file layout XML dùng chung** (`main_activity_left.xml`, `media_item_list_layout.xml`, `control_panel_view.xml`, v.v.) theo cách phá vỡ `THEME_CLASSIC` (6) hoặc `THEME_MATERIAL_YOU` (7).
- Mọi thành phần Glass đều được kiến trúc theo mô hình **Pure Java Construction & View Swap/Decoration**:
  - Khi `isGlass == true`: View gốc được thay thế hoặc trang trí bằng các đối tượng Canvas/Drawable/View thuần Java.
  - Khi `isGlass == false`: Giữ nguyên 100% luồng XML và Drawable nguyên bản của Fermata.

### 1.2 Nguyên tắc Tái sử dụng CarStream (~80% Capacity Porting)
Thay vì tự viết lại từ đầu, dự án tái sử dụng trực tiếp các thành phần cao cấp đã được tôi ưu và kiểm chứng trên phần cứng ô tô từ dự án CarStream:
1. **`Slider.java`**: Custom interactive slider (120 dòng pure Java) -> Dùng làm seekbar player, EQ band slider, settings slider.
2. **`QsPanel.java` & `Tile`**: Quick Settings overlay panel -> Dùng làm Quick Sheet & Control Sheet.
3. **`RadarView.java` & `BanDoXeView.java`**: Bộ tìm xe Radar với vòng quét đồng tâm và la bàn -> Dùng làm Car Finder.
4. **`Speedo.java` & `SpeedStyles`**: Đồng hồ tốc độ với 10 kiểu hiển thị đồ họa cao cấp -> Dùng cho Dashboard & Player Bar.
5. **`BarChartView.java`**: Biểu đồ cột đồ họa -> Dùng cho thống kê hành trình / âm thanh.
6. **`Ui.java` (`clipRound`)**: Bo góc phần cứng qua `ViewOutlineProvider` -> Dùng để bo tròn các surface phức tạp (VideoView, WebView, MapView).

### 1.3 Hệ thống Design Tokens Đồng bộ (Glass Tokens)
Thừa hưởng bảng màu từ `GlassThemeColors` và `CoDienMau`:
- **Card Surface (`the`)**:
  - Dark Mode: `0x1A181C1E` (Translucent obsidian glass) kết hợp Fallback `0x5A141A28`.
  - Light Mode: `0xE6F2F1EF`.
- **Card Border (`vien`)**:
  - Dark Mode: `0x18FFFFFF` (1dp stroke chống lóa ban đêm).
  - Light Mode: `0x14000000`.
- **Accent Primary**: Cyan Neon `#00E5FF` (`0xFF00E5FF`).
- **Accent Secondary**: Electric Blue `#2979FF` (`0xFF2979FF`).
- **Accent Warning/Alert**: Crimson `#D50000` / Rose `#FF1744`.
- **Text Primary**: Pure White `#FFFFFF` (`0xFFFFFFFF`).
- **Text Secondary / Muted**: Slate Grey `#94A3B8` (`0xFF94A3B8`).
- **Corner Radii (`GlassDimensions`)**:
  - Card chính / Sheet: $28\text{ dp}$ (ô tô) / $20\text{ dp}$ (điện thoại).
  - Sub-card / Item container: $14\text{ dp}$ - $16\text{ dp}$.
  - Pill button / Chip: $20\text{ dp}$ - $999\text{ dp}$ (tròn 2 đầu).
  - Icon container: $16\text{ dp}$.

---

## 2. DANH SÁCH & ĐẶC TẢ CODE CẦN PORT TỪ CARSTREAM

### 2.1 Port 1: `GlassSlider` (Kế thừa từ `com.carstream.tech.launcher.Slider`)
* **Nguồn CarStream:** `build/decompiled/carstream/sources/com/carstream/tech/launcher/Slider.java`
* **Đích trong Fermata:** `fermata/src/main/java/me/aap/fermata/ui/glass/widget/GlassSlider.java`
* **Nâng cấp cần thiết:**
  - Thêm hỗ trợ dải sáng đã phát (Played Progress Track) màu Cyan gradient (`0xFF00E5FF` đến `0xFF2979FF`).
  - Thêm sự kiện `OnTrackingListener`: `onStartTrackingTouch()`, `onProgressChanged(float ratio, boolean fromUser)`, `onStopTrackingTouch()`.
  - Hỗ trợ vẽ nhãn mốc (ticks) tùy chọn khi dùng cho Equalizer hoặc Bước nhảy thời gian.
* **Chữ ký lớp (Class Signature):**
  ```java
  package me.aap.fermata.ui.glass.widget;

  public class GlassSlider extends View {
      public interface OnSliderChangeListener {
          void onProgressChanged(GlassSlider slider, float ratio, boolean fromUser);
          void onStartTrackingTouch(GlassSlider slider);
          void onStopTrackingTouch(GlassSlider slider);
      }
      public void setProgress(float ratio); // 0.0f đến 1.0f
      public void setTrackColor(int trackColor);
      public void setProgressColor(int progressColor);
      public void setThumbGlow(boolean enableGlow);
  }
  ```

### 2.2 Port 2: `GlassHardwareUtils` (Kế thừa từ `com.carstream.tech.launcher.Ui`)
* **Nguồn CarStream:** `build/decompiled/carstream/sources/com/carstream/tech/launcher/Ui.java`
* **Đích trong Fermata:** `fermata/src/main/java/me/aap/fermata/ui/glass/theme/GlassHardwareUtils.java`
* **Tính năng kế thừa:**
  - `clipRound(View view, float cornerRadiusPx)`: Ép bo viền GPU thông qua `ViewOutlineProvider` không răng cưa và không giật lag.
  - `round(int color, float radiusPx)`: Tạo nhanh GradientDrawable nền.
  - `roundStroke(int color, float radiusPx, int strokeWidthPx, int strokeColor)`: Tạo nhanh Drawable thẻ kính có viền.

### 2.3 Port 3: `GlassRadarView` (Kế thừa từ `com.carstream.tech.core.timxe.RadarView`)
* **Nguồn CarStream:** `build/decompiled/carstream/sources/com/carstream/tech/core/timxe/RadarView.java`
* **Đích trong Fermata:** `fermata/src/main/java/me/aap/fermata/ui/glass/carfinder/GlassRadarView.java`
* **Tính năng kế thừa:**
  - Hoạt ảnh quét radar bằng `ValueAnimator` với `LinearInterpolator`.
  - Vẽ 3 vòng tròn đồng tâm bán kính động, mũi tên hướng la bàn Neon Cyan.
  - Hiệu ứng phát xung (pulse ping) tại vị trí xe đã lưu.

### 2.4 Port 4: `GlassQuickSheet` & `GlassTile` (Kế thừa từ `com.carstream.tech.launcher.QsPanel`)
* **Nguồn CarStream:** `build/decompiled/carstream/sources/com/carstream/tech/launcher/QsPanel.java`
* **Đích trong Fermata:** `fermata/src/main/java/me/aap/fermata/ui/glass/widget/GlassQuickSheet.java`
* **Tính năng kế thừa:**
  - Bảng trượt / Popup dạng lưới (Grid of Tiles).
  - Mỗi `GlassTile` có icon Glyph/Vector, nhãn văn bản, và trạng thái sáng Cyan khi active.
  - Tích hợp 2 thanh `GlassSlider` điều chỉnh Độ sáng màn hình (Brightness) và Âm lượng (Volume).

---

## 3. ĐẶC TẢ CHI TIẾT 6 TRỤ CỘT GIAO DIỆN CẦN CHUYỂN ĐỔI SANG THUẦN JAVA

### TRỤ CỘT 1: Shell Hệ thống & Điều hướng (TopBar, NavBar, Phone Root & Strips)
1. **`FermataToolBarView` & `TopBarController`**:
   - Khi `isGlassTheme()`: Nền TopBar áp dụng `GlassDrawables.createGlassCardDrawable()` dạng thanh ngang bán trong suốt.
   - Nút Back, Title, và Action Buttons được tint màu `0xFFFFFFFF`, hiệu ứng ripple bo tròn viền kính 1dp.
2. **`FermataNavBarView` & `NavRailViewport`**:
   - Khi ở cạnh trái hoặc phải, NavBar chuyển thành một **Glass Capsule Rail** dựng đứng độc lập.
   - Item đang chọn được bọc trong một **Glass Pill sáng Cyan**, icon phóng to nhẹ và có vệt sáng viền.
3. **`PhonePlaybackCarHeaderController` (`phone_open_on_car_strip.xml`)**:
   - Chuyển dải tiêu đề Phone mode thành **Glass Header Capsule**:
     - AA Status Capsule: Bo tròn dạng viên thuốc, chấm tròn phát sáng neon xanh lá/cyan.
     - Mini Player Controls: Ảnh bìa bo góc 12dp bằng `GlassHardwareUtils.clipRound`, nút Play/Pause dạng tròn có viền kính, thanh Seekbar mini dùng `GlassSlider`.
4. **`PhoneSupportBannerController` (`phone_support_banner.xml`)**:
   - Thay nền chữ nhật đen bằng **Floating Glass Pill** bo tròn 999dp, icon tách biệt tinh tế, tự trôi và biến mất sau 4 giây.
5. **`PhoneBottomMenuController` (`phone_bottom_menu.xml`)**:
   - Gán `ColorStateList` cho `BottomNavigationView`: Tab được chọn phát sáng `0xFF00E5FF`, tab không chọn mờ `0x8094A3B8`.

### TRỤ CỘT 2: Trình phát Fullscreen & Thanh điều khiển Player (Player & Control Panel)
1. **Đồng bộ Tiến trình Thời gian thực (Timeline Engine Parity)**:
   - Sử dụng cơ chế đã kiểm chứng trong `GlassMediaPlayerController`:
     - Ticker loop 1 giây khi đang phát.
     - Thuật toán nội suy tuyến tính: `interpolatedPosition = anchorPosition + (now - anchorTimestamp) * playbackSpeed`.
     - Chặn giật lùi (Monotonic clamp): Không bao giờ hiển thị thụt lùi khi media chưa kết thúc.
2. **Chống Chạm Gián đoạn Video (Zero Video Pause on Panel Toggle)**:
   - Khi ở chế độ Fullscreen:
     - `onSingleTapUp()`: Đánh thức/hiển thị thanh điều khiển ngay lập tức (zero latency).
     - `onDoubleTap()`: Bỏ qua hoàn toàn lệnh pause, chỉ thực hiện tua 10 giây với debounce 400ms.
     - `onSingleTapConfirmed()`: Đặt thời gian ân hạn (grace period 500ms) để không gửi lệnh play/pause nhầm vào video player surface.
3. **`ControlPanelView` & `ControlPanelSeekView`**:
   - Viết lại phần hiển thị điều khiển thành **Floating Glass Player Capsule**:
     - Nền: Thẻ kính mờ nổi cách đáy 16dp, bo góc 24dp.
     - Seekbar: Thay thế `SeekBar` Android bằng `GlassSlider` hỗ trợ kéo mượt, hiển thị thời gian hiện tại / tổng thời gian với font chữ mono rõ nét.
     - Nút Play/Pause: Nút tròn trung tâm đường kính 56dp, viền neon Cyan.
4. **Phụ đề & Thanh chia màn hình (`SubtitlesFragment`, `SubtitlesView`, `subtitles_layout.xml`)**:
   - Hai danh sách phụ đề song ngữ hiển thị trong 2 thẻ Glass Card riêng biệt.
   - Thanh chia (`sub_split_handle`): Chuyển thành thanh kẹp kính Cyan bo tròn 2 đầu, hỗ trợ kéo chia tỉ lệ trực quan.
5. **Hộp thông tin Video & Track (`VideoInfoView`, `video_info_layout.xml`)**:
   - Chuyển thành **Glass Bento Sheet**: Hiện Codec, Resolution, Bitrate, Audio Streams dưới dạng các ô thẻ thông tin kính mờ (chips) có viền sáng.

### TRỤ CỘT 3: Cài đặt Toàn diện & Các mục con (`PreferenceView` & Sub-preferences)
1. **Nền trang Cài đặt & Sub-pages**:
   - `SettingsFragment` và mọi trang cài đặt con thừa kế nền tối trong suốt đồng bộ với Dashboard.
2. **Xử lý toàn bộ 6 loại dòng Cài đặt (`PreferenceView.java`)**:
   - **`set_pref_layout` (Mục chuyển tiếp)**: Thẻ kính có hiệu ứng chạm mờ, mũi tên `>` màu Cyan.
   - **`boolean_pref_layout` (Bật/Tắt)**: Thay Checkbox/Switch mặc định bằng Glass Switch có nút gạt bo tròn và phát sáng Cyan khi BẬT.
   - **`number_pref_layout` (Thanh kéo giá trị)**: Thay `SeekBar` bằng `GlassSlider`, hiển thị số đo kèm đơn vị (%, giây, px) trên chip kính.
   - **`string_pref_layout` (Hộp nhập văn bản)**: `EditText` bo cong 14dp, nền kính mờ `0x25FFFFFF`, viền sáng khi được focus.
   - **`file_pref_layout` (Chọn đường dẫn)**: Hàng hiển thị đường dẫn dạng chip kính + nút "Browse" hình viên thuốc kính.
   - **`list_pref_layout` (Lựa chọn danh sách)**: Hiển thị giá trị hiện tại trong chip kính, khi bấm mở `GlassMenu`.

### TRỤ CỘT 4: Hệ thống Menu, Popup, Dropdown & Dialogs (Overlay & Modals)
1. **Menu Ngữ cảnh & Dropdown (`OverlayMenuView` & `OverlayMenuItemView`)**:
   - Khi `isGlassTheme()`:
     - Nền menu: `GlassDrawables.createGlassCardDrawable(ctx, 16dp, isNight)` với viền 1dp `BORDER_DEFAULT`.
     - Tiêu đề (`R.id.overlay_menu_title`): Chữ hoa in đậm, font sắc nét, có đường kẻ kính ngăn cách bên dưới.
     - Các dòng con (`OverlayMenuItemView`): Khi được chọn hoặc hover (D-pad ô tô), chuyển sang nền Cyan mờ `0x2500E5FF` và icon phát sáng.
   - **Áp dụng tự động cho toàn bộ app**:
     - Menu chọn Theme (Classic / Material You / Glass).
     - Menu chọn Tốc độ tua video (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x).
     - Menu hẹn giờ tắt nhạc (Sleep timer).
     - Menu chất lượng YouTube (`YoutubeQualityMenu`).
     - Menu danh sách phát (Playlist add menu).
     - Menu nhấn giữ bài hát trong MediaLib.
2. **Hộp thoại Thông báo & Nhập liệu (`GenericDialogFragment` & `DialogView` / `DialogBuilder`)**:
   - Xây dựng `GlassDialogBuilder`:
     - Khung dialog: Thẻ kính nổi ở trung tâm, bo góc 20dp, đổ bóng mềm và viền 1dp.
     - Cụm nút: Nút "Hủy" là Glass Dark Pill, nút "Xác nhận / OK" là Glass Cyan Action Pill nổi bật.
   - Tự động đồng bộ cho các dialog cấu hình nguồn:
     - Hộp thoại thêm Audiobookshelf (`AudiobookshelfSourceDialog`).
     - Hộp thoại thêm OPDS (`OpdsSourceDialog`).
     - Hộp thoại thêm Podcast (`PodcastSourceDialog`).
     - Hộp thoại thêm IPTV M3U / Xtream Codes.

### TRỤ CỘT 5: Thư viện MediaLib, Trình duyệt File & Trạng thái Rỗng
1. **Item Danh sách & Lưới bài hát (`MediaItemView`)**:
   - Mỗi hàng bài hát/video/thư mục: Bọc trong dải thẻ kính mờ nhẹ (`0x15FFFFFF`).
   - Khi đang phát (`item.equals(cur)`): Nền chuyển sang dải chuyển màu Cyan mờ, tên bài hát đổi sang màu Cyan rực rỡ, thanh tiến trình bài hát `LinearProgressIndicator` phát sáng Neon.
   - Checkbox chế độ chọn nhiều bài: Thay bằng vòng tròn kính có dấu tích Cyan.
2. **Bảng Trạng thái Rỗng / Đang tải / Lỗi (`media_items_state_panel`)**:
   - Bảng thông báo ở giữa màn hình: Bọc trong thẻ **Glass State Card**.
   - Spinner quay: Vòng xoay neon Cyan thay cho ProgressBar tròn xám.
   - Nút hành động (Thử lại / Chọn thư mục): Nút **Glass Action Pill** có viền 1dp.
3. **Trình duyệt File VFS (`FilePickerFragment` / `VirtualResourceAdapter` / `list_item.xml`)**:
   - Danh sách tệp và thư mục hiển thị theo phong cách Glass List.
   - Thư mục hiển thị icon Folder màu Cyan/Electric Blue, tệp nhạc/video hiển thị icon media phát sáng.

### TRỤ CỘT 6: Trợ lý Giọng nói, Car Finder & Các Addon Chuyên biệt
1. **Giao diện Trợ lý Giọng nói (`VoiceRecognitionSession`)**:
   - Thay thế popup đơn điệu bằng **Glass Voice Sheet**:
     - Vòng tròn Micro trung tâm đường kính 80dp, nền kính tròn viền Cyan.
     - Vòng sóng âm phát xung (Pulsing sound waves) lan tỏa theo âm lượng giọng nói thực tế.
     - Dòng chữ nhận diện hiển thị font lớn, sắc nét trên nền kính mờ.
2. **Tính năng Tìm Xe (`CarFinderFragment` & `CarFinderSettingsDialog`)**:
   - Thay thế toàn bộ mã màu hardcoded Navy (`0xFF131926`, `0xFF3B82F6`) bằng Design Tokens của Glass Theme:
     - Tích hợp `GlassRadarView` (từ CarStream) với các vòng quét radar mượt mà.
     - Map tile view được bo góc phần cứng qua `GlassHardwareUtils.clipRound`.
     - Hộp thoại `CarFinderSettingsDialog`: Xây dựng lại thành Glass Sheet bo viền kính 1dp.
3. **Trình duyệt Web (`WebBrowserFragment` & `WebToolBarMediator`)**:
   - Thanh nhập địa chỉ URL (`browser_addr`): Chuyển thành **Glass Search Capsule** bo tròn 2 đầu, nền kính mờ, có viền sáng Cyan khi con trỏ gõ văn bản.
4. **Điều khiển Lướt TikTok (`TikTokFloatingNavView` / `tiktok_nav_capsule.xml`)**:
   - Chuyển capsule 3 nút (Lên, Dừng, Xuống) thành **Glass Floating Nav Capsule** trong suốt 80%, viền neon Cyan.
5. **Nút Google Cast (`CastAddon`) & Pull to Refresh (`SwipeRefresh`)**:
   - Nút Cast trên TopBar được phủ tint Cyan đồng bộ.
   - Vòng xoay kéo-để-làm-mới (`SwipeRefreshLayout`) đổi dải màu sang Cyan & Electric Blue (`0xFF00E5FF`, `0xFF2979FF`).
6. **Màn hình Thiết lập ban đầu (`InitialSetupFragment` / `initial_setup.xml`)**:
   - Thiết kế lại dưới dạng **Glass Setup Wizard Card** chính giữa màn hình với cụm nút chọn vị trí Navbar dạng Segmented Glass Pill.

---

## 4. MA TRẬN TỔNG HỢP CÁC FILE CẦN TẠO MỚI VÀ SỬA ĐỔI

### 4.1 Các file tạo mới (Pure Java Components trong `me.aap.fermata.ui.glass.*`):
| Đường dẫn file | Trách nhiệm chính |
|---|---|
| `fermata/.../ui/glass/widget/GlassSlider.java` | Port từ CarStream Slider.java, thanh trượt kính mờ đa năng |
| `fermata/.../ui/glass/theme/GlassHardwareUtils.java` | Port từ CarStream Ui.java, bo góc GPU `clipRound`, tạo drawable |
| `fermata/.../ui/glass/widget/GlassQuickSheet.java` | Port từ CarStream QsPanel.java, panel cài đặt nhanh / điều khiển |
| `fermata/.../ui/glass/carfinder/GlassRadarView.java` | Port từ CarStream RadarView.java, radar tìm xe đồ họa cao cấp |
| `fermata/.../ui/glass/widget/GlassCardView.java` | Container thẻ kính thuần Java có sẵn bo góc, viền sáng và padding |
| `fermata/.../ui/glass/widget/GlassSwitch.java` | Công tắc bật/tắt kính mờ thuần Java thay thế SwitchCompat |
| `fermata/.../ui/glass/dialog/GlassDialogBuilder.java` | Trình tạo hộp thoại kính mờ thay thế MaterialAlertDialogBuilder |

### 4.2 Các file sửa đổi (Tích hợp kích hoạt có điều kiện `isGlassTheme`):
| Đường dẫn file | Thay đổi kỹ thuật |
|---|---|
| `fermata/.../ui/view/ControlPanelView.java` | Thay Seekbar bằng `GlassSlider`, bo tròn panel nổi, debounce tap |
| `fermata/.../ui/view/AudioEffectsScreenView.java` | Thay EQ sliders bằng `GlassSlider`, tô màu neon cho curve đồ thị |
| `depends/utils/.../pref/PreferenceView.java` | Áp dụng thẻ kính, switch kính, slider kính cho 6 loại preferences |
| `depends/utils/.../ui/menu/OverlayMenuView.java` | Áp dụng nền thẻ kính và hiệu ứng hover/active Cyan cho popup/menu |
| `depends/utils/.../ui/menu/OverlayMenuItemView.java` | Highlight Cyan khi selected/hover |
| `depends/utils/.../ui/fragment/GenericDialogFragment.java` | Bọc content trong Glass Card khi hiển thị |
| `depends/utils/.../ui/fragment/FilePickerFragment.java` | Đồng bộ dải kính cho danh sách tệp/thư mục VFS |
| `fermata/.../ui/view/MediaItemView.java` | Highlight bài hát đang phát phát sáng Cyan, bo góc thẻ bài hát |
| `fermata/.../ui/view/PhonePlaybackCarHeaderController.java` | Thay dải header Phone mode bằng Glass Header Card |
| `fermata/.../ui/view/PhoneSupportBannerController.java` | Chuyển banner support thành Glass Pill |
| `fermata/.../ui/view/PhoneBottomMenuController.java` | Tint màu tab active Cyan |
| `fermata/.../ui/activity/VoiceRecognitionSession.java` | Hiển thị Glass Voice Sheet với hiệu ứng sóng âm |
| `fermata/.../carfinder/CarFinderFragment.java` | Tích hợp `GlassRadarView` và đổi màu sang Glass Tokens |
| `modules/web/.../addon/web/WebToolBarMediator.java` | Chuyển ô địa chỉ URL thành Glass Capsule |
| `modules/web/.../addon/web/tiktok/TikTokFloatingNavView.java` | Bo viền kính cho capsule lướt video |
| `fermata/.../ui/fragment/InitialSetupFragment.java` | Trình bày wizard thiết lập bằng thẻ kính trung tâm |
