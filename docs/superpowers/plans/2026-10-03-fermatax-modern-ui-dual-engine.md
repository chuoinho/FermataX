# FermataX 2.0 Modern UI & Dual-Engine Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai bộ giao diện mới FermataX 2.0 (Home SmartTop Hero + Thời tiết Open-Meteo + Quick Stream Bar, Tab Addons Zero-Scroll, 5-slot Navbar) chạy song song với giao diện Cổ điển (Classic UI) thông qua tùy chọn Theme trong Cài đặt mà không làm thay đổi hay gián đoạn bất kỳ tính năng hiện có nào.

**Architecture:** Sử dụng kiến trúc Dual Presentation Layer (Feature Toggle / Strangler Fig). Tầng dịch vụ cốt lõi (Media Engine, ExoPlayer, AddonManager, Android Auto Projection) được giữ nguyên 100%. Tầng giao diện được phân nhánh bởi cấu hình `UI_STYLE` trong `MainActivityPrefs`. Khi người dùng bật giao diện Mới, hệ thống nạp các Fragment và View độc lập (`HomeFragment`, `AddonsCatalogFragment`, `VoiceOverlayView`, `QuickStreamBarView`), đảm bảo rủi ro hồi quy (regression) bằng 0.

**Tech Stack:** Android SDK (Java 17 / AndroidX), Android Auto VirtualDisplay Presentation, Material 3 Expressive / Android 17 design tokens, Open-Meteo REST API (No API Key), Room/SharedPreferences JSON storage, Palette API.

**Spec:** [fermatax_ui_redesign_spec.md](file:///C:/Users/ttanh/.gemini/antigravity/brain/564cac3f-a02d-4514-80a6-b2549bd57338/fermatax_ui_redesign_spec.md)

---

## Global Constraints

- **Zero Regression on Classic UI**: Tất cả người dùng ở chế độ Classic UI (`STYLE_CLASSIC`) phải có hành vi, bố cục và hiệu năng giống hệt 100% như trước khi cập nhật.
- **Zero API Key for Weather**: Tuyệt đối không yêu cầu người dùng nhập API key thời tiết; sử dụng Open-Meteo REST API mở.
- **Zero Runtime Permission on AA Display**: Không gọi `requestPermissions` trên ngữ cảnh `Presentation` / Android Auto VirtualDisplay. Quyền vị trí chỉ lấy thụ động hoặc cho phép chọn thành phố mặc định.
- **Zero-Scroll Addons Guarantee**: Trang Addons phải tính toán kích thước động theo chiều cao màn hình khả dụng (2x4 trên màn ngang, 4x2 trên màn dọc), không bao giờ kích hoạt thanh cuộn.
- **Audio/Video Stream Persistence**: Việc chuyển đổi giữa các tab (Home <-> Addons) hoặc chuyển đổi Theme trong Settings không được phép ngắt hoặc khởi tạo lại Media Session đang phát.
- **Minimum Touch Target**: Mọi nút bấm trên xe hơi phải đạt tối thiểu 56dp x 56dp (vùng chạm 64dp).

---

## Review Focus

1. **Chuyển đổi Theme khi đang phát media**: Khi người dùng đổi từ Classic sang Modern (hoặc ngược lại) trong Settings, `AutoFermataMediaService` không bị dừng và vị trí phát nhạc/video không bị mất.
2. **Màn hình ô tô độ phân giải thấp (800x480 mdpi)**: Trang Addons không bị tràn chữ hoặc sinh thanh cuộn trên màn hình nhỏ.
3. **Mất kết nối mạng / Ngoại tuyến**: Widget Thời tiết hiển thị thông tin lưu đệm (cache) hoặc biểu tượng chờ nhẹ nhàng, không làm crash hoặc đơ giao diện Home.
4. **Kích hoạt Voice Assistant trên VirtualDisplay**: Modal Voice mở tức thì dưới dạng In-layout View, không dùng System Dialog để tránh `WindowManager.BadTokenException`.
5. **Thao tác phím Back cứng trên xe hơi**: Nhấn Back từ Addons/Settings luôn quay về Home; nhấn Back tại Home không bị văng ứng dụng đột ngột.

---

## File Structure

```
fermata/src/main/java/me/aap/fermata/
├── ui/
│   ├── activity/
│   │   ├── MainActivityPrefs.java                [Modify: thêm UI_STYLE preference]
│   │   ├── InterfacePrefsBuilder.java            [Modify: thêm mục chọn Kiểu giao diện]
│   │   └── MainActivityDelegate.java             [Modify: điều phối nạp layout Classic vs Modern]
│   ├── modern/                                   [Create new package: Giao diện Modern 2.0]
│   │   ├── HomeFragment.java                     [Create: SmartTop Hero + Weather + Quick Stream]
│   │   ├── AddonsCatalogFragment.java            [Create: Zero-Scroll 8-tile Bento grid]
│   │   ├── ZeroScrollLayoutHelper.java           [Create: thuật toán chia lưới động theo viewport]
│   │   ├── quickstream/
│   │   │   ├── QuickStreamShortcut.java          [Create: model dữ liệu lối tắt]
│   │   │   ├── QuickStreamManager.java           [Create: CRUD & Preset mặc định Việt Nam]
│   │   │   ├── QuickStreamBarView.java           [Create: thanh ngang hiển thị thẻ lối tắt]
│   │   │   └── AddShortcutBottomSheet.java       [Create: hộp thoại chọn kênh/addon để ghim]
│   │   ├── weather/
│   │   │   ├── OpenMeteoWeatherService.java      [Create: gọi REST API không cần key]
│   │   │   ├── WeatherData.java                  [Create: model thời tiết & mã WMO]
│   │   │   └── BentoWeatherCardView.java         [Create: widget Thời tiết + Đồng hồ]
│   │   └── voice/
│   │       └── VoiceOverlayView.java             [Create: in-layout visualizer & suggestions]
│   └── voice/
│       └── VoiceIntentParser.java                [Modify: thêm alias cho các kênh Quick Stream]
fermata/src/main/res/
├── layout/
│   ├── main_activity_modern_left.xml             [Create: layout AA màn ngang 5-slot rail]
│   ├── main_activity_modern_bottom.xml           [Create: layout Mobile màn dọc 5-slot bottom bar]
│   ├── home_modern_fragment.xml                  [Create: layout Home Bento]
│   ├── addons_catalog_fragment.xml               [Create: layout Addons Zero-Scroll]
│   ├── addon_bento_card_item.xml                 [Create: item thẻ Bento Addon xúc giác]
│   └── quick_stream_pill_item.xml                [Create: item con nhộng lối tắt nhanh]
```

---

## Task Decomposition

### Task 1: Dual-UI Preference & Settings Toggle Scaffolding

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityPrefs.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/activity/InterfacePrefsBuilder.java`
- Modify: `fermata/src/main/res/values/strings.xml`
- Create Test: `fermata/src/test/java/me/aap/fermata/ui/activity/UiStylePreferenceTest.java`

**Interfaces:**
- Produces:
  - `MainActivityPrefs.UI_STYLE`: `PreferenceStore.Pref<UiStyle>`
  - `MainActivityPrefs.UiStyle` enum: `CLASSIC(0)`, `MODERN_V2(1)`
  - `MainActivityPrefs.getUiStylePref()`: `() -> UiStyle`

- [ ] **Step 1: Write the failing test for UI_STYLE preference**
```java
// UiStylePreferenceTest.java
@Test
public void testUiStylePreferenceDefaultAndPersistence() {
    MainActivityPrefs prefs = MainActivityPrefs.get();
    assertEquals(MainActivityPrefs.UiStyle.CLASSIC, prefs.getUiStylePref());
    prefs.setUiStylePref(MainActivityPrefs.UiStyle.MODERN_V2);
    assertEquals(MainActivityPrefs.UiStyle.MODERN_V2, prefs.getUiStylePref());
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.activity.UiStylePreferenceTest"`
Expected: FAIL (symbol `UI_STYLE` / `UiStyle` unresolved).

- [ ] **Step 3: Implement `UiStyle` enum and preference in `MainActivityPrefs.java` and `InterfacePrefsBuilder.java`**
Thêm enum `UiStyle { CLASSIC(0), MODERN_V2(1) }`, default là `CLASSIC`.
Trong `InterfacePrefsBuilder.java`, thêm tùy chọn `pref_ui_style` ("Kiểu giao diện: Cổ điển / Hiện đại FermataX 2.0") với listener gọi `activity.recreate()` khi giá trị thay đổi.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.activity.UiStylePreferenceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityPrefs.java \
        fermata/src/main/java/me/aap/fermata/ui/activity/InterfacePrefsBuilder.java \
        fermata/src/main/res/values/strings.xml \
        fermata/src/test/java/me/aap/fermata/ui/activity/UiStylePreferenceTest.java
git commit -m "feat(ui): add dual-engine UI_STYLE preference and settings toggle"
```

---

### Task 2: Quick Stream Data Model & Persistence

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamShortcut.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamManager.java`
- Create Test: `fermata/src/test/java/me/aap/fermata/ui/modern/quickstream/QuickStreamManagerTest.java`

**Interfaces:**
- Produces:
  - `QuickStreamShortcut`: `{ id, title, subtitle, targetUri, iconRes, accentColor }`
  - `QuickStreamManager.getShortcuts()`: `List<QuickStreamShortcut>`
  - `QuickStreamManager.addShortcut(QuickStreamShortcut shortcut)`: `void`
  - `QuickStreamManager.removeShortcut(String id)`: `void`
  - `QuickStreamManager.getDefaultShortcuts()`: `List<QuickStreamShortcut>` (VTV1, VTV3, VOV GT 91MHz, YouTube Chill)

- [ ] **Step 1: Write the failing test for Quick Stream serialization and defaults**
```java
// QuickStreamManagerTest.java
@Test
public void testDefaultPresetsAndJsonSerialization() {
    QuickStreamManager mgr = new QuickStreamManager(mockPrefs);
    List<QuickStreamShortcut> list = mgr.getShortcuts();
    assertFalse(list.isEmpty());
    assertTrue(list.stream().anyMatch(s -> s.getTitle().contains("VTV3")));
    
    QuickStreamShortcut custom = new QuickStreamShortcut("custom_1", "Nhạc Test", "Audio", "file:///test.mp3", 0, 0xFF00E5FF);
    mgr.addShortcut(custom);
    assertTrue(mgr.getShortcuts().contains(custom));
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.modern.quickstream.QuickStreamManagerTest"`
Expected: FAIL (classes not found).

- [ ] **Step 3: Implement `QuickStreamShortcut` and `QuickStreamManager`**
Tạo model bất biến `QuickStreamShortcut` và bộ lưu trữ `QuickStreamManager` sử dụng JSON string lưu vào `PreferenceStore`. Khởi tạo 4 preset mặc định thân thuộc cho xe hơi Việt Nam nếu danh sách đang rỗng.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.modern.quickstream.QuickStreamManagerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/ \
        fermata/src/test/java/me/aap/fermata/ui/modern/quickstream/
git commit -m "feat(quickstream): add QuickStreamShortcut model and QuickStreamManager persistence"
```

---

### Task 3: Open-Meteo Weather Service (No API Key Required)

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/WeatherData.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/OpenMeteoWeatherService.java`
- Create Test: `fermata/src/test/java/me/aap/fermata/ui/modern/weather/OpenMeteoWeatherServiceTest.java`

**Interfaces:**
- Produces:
  - `WeatherData`: `{ temperatureC, weatherCode, description, iconRes, cityName, timestamp }`
  - `OpenMeteoWeatherService.fetchCurrentWeather(double lat, double lon, Consumer<WeatherData> callback)`: `void`
  - `OpenMeteoWeatherService.getCachedWeather()`: `WeatherData`

- [ ] **Step 1: Write the failing test for Open-Meteo JSON parser and WMO code mapping**
```java
// OpenMeteoWeatherServiceTest.java
@Test
public void testWmoWeatherCodeMapping() {
    String sampleJson = "{\"current\":{\"temperature_2m\":28.4,\"weather_code\":1}}";
    WeatherData data = OpenMeteoWeatherService.parseResponse(sampleJson, "Hà Nội");
    assertEquals(28, Math.round(data.getTemperatureC()));
    assertEquals("Ít mây / Nắng nhẹ", data.getDescription());
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.modern.weather.OpenMeteoWeatherServiceTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `WeatherData` and `OpenMeteoWeatherService`**
Sử dụng `HttpURLConnection` kết nối endpoint Open-Meteo REST API. Tích hợp bộ đệm thời gian 45 phút và bảng ánh xạ chuẩn WMO sang chuỗi tiếng Việt + Resource ID icon vector cục bộ. Fallback về tọa độ Hà Nội (21.0285, 105.8542) nếu không có GPS.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.modern.weather.OpenMeteoWeatherServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add fermata/src/main/java/me/aap/fermata/ui/modern/weather/ \
        fermata/src/test/java/me/aap/fermata/ui/modern/weather/
git commit -m "feat(weather): implement Open-Meteo service without API key requirement"
```

---

### Task 4: Zero-Scroll Addons Grid Component (`AddonsCatalogFragment`)

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/ZeroScrollLayoutHelper.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/AddonsCatalogFragment.java`
- Create: `fermata/src/main/res/layout/addons_catalog_fragment.xml`
- Create: `fermata/src/main/res/layout/addon_bento_card_item.xml`
- Create Test: `fermata/src/test/java/me/aap/fermata/ui/modern/ZeroScrollLayoutHelperTest.java`

**Interfaces:**
- Produces:
  - `ZeroScrollLayoutHelper.calculateCardHeight(int availableHeight, int rows, int verticalPadding)`: `int`
  - `ZeroScrollLayoutHelper.getSpanCount(boolean isLandscape)`: `4` (Landscape) / `2` (Portrait)
  - `AddonsCatalogFragment`: Fragment hiển thị 8 thẻ Addons với diện tích chia đều 100% không cuộn

- [ ] **Step 1: Write the failing test for Zero-Scroll dimension calculations**
```java
// ZeroScrollLayoutHelperTest.java
@Test
public void testZeroScrollGridDimensions() {
    int availableHeight = 480; // Chuẩn DHU xe hơi nhỏ
    int rows = 2;
    int paddingTotal = 32;
    int cardHeight = ZeroScrollLayoutHelper.calculateCardHeight(availableHeight, rows, paddingTotal);
    assertTrue(cardHeight > 0);
    assertEquals(availableHeight, (cardHeight * rows) + paddingTotal);
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.modern.ZeroScrollLayoutHelperTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `ZeroScrollLayoutHelper` and `AddonsCatalogFragment`**
Tạo Fragment nạp danh sách 8 Addon (YouTube, TV, Radio, Car Finder, VLC, Browser, Sách nói, Files). Cài đặt `RecyclerView` với `overScrollMode="never"` và ghi đè `onMeasure()` của Item để chia đều chuẩn xác chiều cao, áp dụng `TextViewCompat` auto-sizing.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.modern.ZeroScrollLayoutHelperTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add fermata/src/main/java/me/aap/fermata/ui/modern/ZeroScrollLayoutHelper.java \
        fermata/src/main/java/me/aap/fermata/ui/modern/AddonsCatalogFragment.java \
        fermata/src/main/res/layout/addons_catalog_fragment.xml \
        fermata/src/main/res/layout/addon_bento_card_item.xml \
        fermata/src/test/java/me/aap/fermata/ui/modern/ZeroScrollLayoutHelperTest.java
git commit -m "feat(addons): create AddonsCatalogFragment with guaranteed Zero-Scroll layout"
```

---

### Task 5: Home Fragment với SmartTop Hero Player & Bento Weather

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/HomeFragment.java`
- Create: `fermata/src/main/res/layout/home_modern_fragment.xml`
- Create: `fermata/src/main/res/layout/bento_weather_card.xml`

**Interfaces:**
- Consumes:
  - `OpenMeteoWeatherService` (Task 3)
  - `QuickStreamBarView` (Task 6)
  - `FermataServiceUiBinder` (Media Service)
- Produces:
  - `HomeFragment`: Fragment trang chủ thông minh kết hợp Hero Media Player Card phóng to + Bento Thời tiết/Đồng hồ + Quick Stream Bar

- [ ] **Step 1: Write UI component layout XML and View bindings**
Thiết kế `home_modern_fragment.xml` với `ConstraintLayout`: Khối SmartTop Hero Media kích thước lớn chiếm vị trí trung tâm, khối Thời tiết & Đồng hồ tinh gọn ở góc, và dải Quick Stream ở phía dưới.

- [ ] **Step 2: Implement `BentoWeatherCardView`**
Tạo Custom View tự động đăng ký lắng nghe `OpenMeteoWeatherService`, hiển thị đồng hồ thời gian thực và nhiệt độ/icon thời tiết.

- [ ] **Step 3: Implement `HomeFragment.java`**
Kết nối `HomeFragment` với `FermataServiceUiBinder.Listener`, cập nhật thông tin bài hát từ `PlaybackSnapshot` và thanh tiến trình `PlaybackTimelineSnapshot`, tích hợp trích xuất màu `Palette` bất đồng bộ để tạo viền sáng Ambient Glow.

- [ ] **Step 4: Verify compilation & layout inflation**
Run: `./gradlew :fermata:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**
```bash
git add fermata/src/main/java/me/aap/fermata/ui/modern/HomeFragment.java \
        fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java \
        fermata/src/main/res/layout/home_modern_fragment.xml \
        fermata/src/main/res/layout/bento_weather_card.xml
git commit -m "feat(home): implement modern HomeFragment with SmartTop Hero Player and Weather Bento"
```

---

### Task 6: Quick Stream Bar UI & Hộp Thoại Thêm Lối Tắt

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/AddShortcutBottomSheet.java`
- Create: `fermata/src/main/res/layout/quick_stream_bar_view.xml`
- Create: `fermata/src/main/res/layout/quick_stream_pill_item.xml`
- Create: `fermata/src/main/res/layout/add_shortcut_bottom_sheet.xml`

**Interfaces:**
- Consumes: `QuickStreamManager` (Task 2)
- Produces:
  - `QuickStreamBarView`: Thanh cuộn ngang hiển thị các thẻ con nhộng lối tắt + nút `+ Thêm`
  - `AddShortcutBottomSheet`: Hộp thoại chọn mục gần đây hoặc addon để ghim lên Home

- [ ] **Step 1: Create layout files for Quick Stream bar and shortcut pills**
Xây dựng thẻ con nhộng bo tròn 20dp với icon phát quang và tiêu đề kênh.

- [ ] **Step 2: Implement `QuickStreamBarView`**
Custom ViewGroup quản lý danh sách lối tắt. Khi chạm vào lối tắt, giải mã `targetUri` và gọi `MainActivityDelegate.playItem()` hoặc điều hướng tới Addon tương ứng.

- [ ] **Step 3: Implement `AddShortcutBottomSheet`**
Cho phép người dùng chọn nhanh từ lịch sử phát gần nhất hoặc chọn Addon gốc để ghim vào `QuickStreamManager`.

- [ ] **Step 4: Run unit tests and compilation check**
Run: `./gradlew :fermata:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**
```bash
git add fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java \
        fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/AddShortcutBottomSheet.java \
        fermata/src/main/res/layout/quick_stream_*.xml \
        fermata/src/main/res/layout/add_shortcut_bottom_sheet.xml
git commit -m "feat(quickstream): build QuickStreamBarView and AddShortcutBottomSheet selector"
```

---

### Task 7: Voice Assistant In-Layout Overlay (`VoiceOverlayView`)

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/voice/VoiceOverlayView.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/voice/VoiceIntentParser.java`
- Create: `fermata/src/main/res/layout/voice_overlay_view.xml`
- Create Test: `fermata/src/test/java/me/aap/fermata/ui/voice/QuickStreamVoiceIntentTest.java`

**Interfaces:**
- Consumes:
  - `VoiceInteractionCoordinator` (Core voice controller)
  - `QuickStreamManager` (Shortcuts)
- Produces:
  - `VoiceOverlayView`: In-layout modal sheet hiển thị sóng âm visualizer, câu lệnh thời gian thực và 4 chip gợi ý khẩu lệnh nhanh

- [ ] **Step 1: Write the failing test for Vietnamese Quick Stream voice commands**
```java
// QuickStreamVoiceIntentTest.java
@Test
public void testQuickStreamChannelVoiceCommands() {
    VoiceIntent intent = VoiceIntentParser.parse("mở kênh vtv3", Locale.forLanguageTag("vi"));
    assertNotNull(intent);
    assertEquals("vtv3", intent.getTargetId());
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.voice.QuickStreamVoiceIntentTest"`
Expected: FAIL.

- [ ] **Step 3: Extend `VoiceIntentParser.java` and implement `VoiceOverlayView.java`**
Bổ sung từ khóa nhận diện các kênh TV/Radio thông dụng vào `VoiceIntentParser`. Xây dựng `VoiceOverlayView` kế thừa `FrameLayout` hiển thị nổi trên màn hình với hiệu ứng làm tối nền nhẹ, thanh sóng âm và các nút chip tương tác.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.voice.QuickStreamVoiceIntentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add fermata/src/main/java/me/aap/fermata/ui/modern/voice/VoiceOverlayView.java \
        fermata/src/main/java/me/aap/fermata/ui/voice/VoiceIntentParser.java \
        fermata/src/main/res/layout/voice_overlay_view.xml \
        fermata/src/test/java/me/aap/fermata/ui/voice/QuickStreamVoiceIntentTest.java
git commit -m "feat(voice): implement in-layout VoiceOverlayView and quick stream voice parsing"
```

---

### Task 8: Navigation Rail (5-Slot Way B) & Activity Delegation Routing

**Files:**
- Create: `fermata/src/main/res/layout/main_activity_modern_left.xml`
- Create: `fermata/src/main/res/layout/main_activity_modern_bottom.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/fragment/NavBarMediator.java`
- Create Test: `fermata/src/test/java/me/aap/fermata/ui/activity/ModernLayoutRoutingTest.java`

**Interfaces:**
- Consumes:
  - `MainActivityPrefs.getUiStylePref()` (Task 1)
  - `HomeFragment` (Task 5)
  - `AddonsCatalogFragment` (Task 4)
  - `VoiceOverlayView` (Task 7)
- Produces:
  - Định tuyến hoàn chỉnh: Classic UI nạp layout và mediator cũ; Modern UI nạp thanh 5 nút cố định `[Home] [Voice] [Addons] [Settings] [About]`.

- [ ] **Step 1: Write test for layout and initial fragment selection based on UI_STYLE**
```java
// ModernLayoutRoutingTest.java
@Test
public void testModernLayoutResolution() {
    mockPrefs.setUiStylePref(MainActivityPrefs.UiStyle.MODERN_V2);
    int layoutRes = MainActivityDelegate.resolveEffectiveLayout(mockPrefs, true);
    assertEquals(R.layout.main_activity_modern_left, layoutRes);
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.activity.ModernLayoutRoutingTest"`
Expected: FAIL.

- [ ] **Step 3: Implement Modern Layouts and route logic in `MainActivityDelegate`**
Tạo `main_activity_modern_left.xml` và `main_activity_modern_bottom.xml`. Trong `MainActivityDelegate.java`, cập nhật `getLayout()` để nạp layout Modern khi `UI_STYLE == MODERN_V2`. Nút Voice kích hoạt `VoiceOverlayView.show()` mà không làm gián đoạn bài hát.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew :fermata:test --tests "me.aap.fermata.ui.activity.ModernLayoutRoutingTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add fermata/src/main/res/layout/main_activity_modern_*.xml \
        fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java \
        fermata/src/main/java/me/aap/fermata/ui/fragment/NavBarMediator.java \
        fermata/src/test/java/me/aap/fermata/ui/activity/ModernLayoutRoutingTest.java
git commit -m "feat(routing): integrate 5-slot modern navigation and dual-engine layout dispatch"
```

---

### Task 9: End-to-End Verification & Automotive Regression Testing

**Files:**
- Test verification scripts & suite execution

- [ ] **Step 1: Run full unit test suite**
Run: `./gradlew test`
Expected: Tất cả các bài kiểm tra (cũ và mới) PASS 100%.

- [ ] **Step 2: Verify Android Auto Projection build**
Run: `./gradlew :fermata:assembleAutoDebug`
Expected: BUILD SUCCESSFUL, APK sinh ra hoàn chỉnh không lỗi manifest hay resource.

- [ ] **Step 3: Verification Checkpoints**
  1. Kiểm tra chuyển đổi qua lại giữa Classic và Modern trong Settings.
  2. Kiểm tra tính liên tục của playback: Bật bài hát -> đổi Theme -> bài hát tiếp tục phát mượt mà.
  3. Kiểm tra trang Addons trên giả lập 800x480 và 1920x1080: Không xuất hiện scrollbar.
  4. Kiểm tra nút Voice kích hoạt overlay và audio ducking hoạt động chính xác.

- [ ] **Step 4: Commit**
```bash
git commit --allow-empty -m "chore(release): verify FermataX 2.0 dual-engine modern UI readiness"
```

---

Plan complete and saved to `docs/superpowers/plans/2026-10-03-fermatax-modern-ui-dual-engine.md`. Please review the plan. Which execution approach would you prefer?

- **Subagent-driven** - A fresh subagent implements each task and a fresh reviewer checks it before the next one starts, then a whole-branch review at the end. Most thorough; costs a fresh context per task and per review.
- **Native** - I implement every task myself in this session, the way this harness runs work, then one fresh reviewer on the most capable model checks the whole branch. Cheapest and fastest; no independent review until the end. Runs well with a mid-tier session model, since the plan carries the design.

**For this plan I recommend Native**, because các task triển khai theo mô hình mở rộng sạch (tạo file mới, ghép nối qua preference `UI_STYLE` và không đụng vào code media cốt lõi), việc thực thi tuần tự trong cùng một context sẽ kiểm soát chặt chẽ tính tương thích và kiểm tra build Android Gradle liên tục. Does the plan capture what you want, and which approach should we use?
