# Audit toàn bộ nhánh `feat/aa-glass-theme` — 2026-10-22

## 1. Phạm vi

| Hạng mục | Giá trị |
|---|---|
| Base | `main` @ `984bbae4` (merge-base; `main` không có commit mới) |
| HEAD | `291f3acc` — 40 commit |
| Diff đã commit | 267 file, +21 720 / −1 664 (93 thêm, 139 sửa, 35 xoá) |
| Chưa commit | 23 file sửa (+1 607 / −496), 19 file/thư mục untracked (TripEngine, TripStore, SpeedoKinematicFusionHub, test, plan…) |
| Tác giả commit | `Codex <codex@local>` |

Lưu ý: phần chưa commit (speedo fusion, car finder đa nguồn, FGS location, rail kéo/vuốt) là phần **rủi ro nhất** và chưa từng được audit trước đây (audit 2026-10-18 dừng ở `e108dcea`).

## 2. Kết quả xác minh tự động (trên working tree hiện tại)

Lệnh: `ALLOW_LEGACY_TEST_SIGNING=true APP_ID_SFX=.test ./gradlew testAutoDebugUnitTest lintAutoDebug :fermata:testMobileDebugUnitTest --offline --continue`

| Kiểm tra | Kết quả |
|---|---|
| Unit test (auto + mobile, mọi module) | ✅ 3 729 test, 0 fail, 0 error, 6 skip |
| Lint `autoDebug` | ✅ 0 error, 535 warning |
| Build không có cờ test | ❌ `settings.gradle:79` chặn: "insecure deterministic legacy upload certificate" — `local.properties` đang trỏ keystore legacy. Đúng như thiết kế, nhưng nghĩa là máy này không build được bản thật. |

Warning lint đáng chú ý trong mã của nhánh: 10 string `speedo_*`/glass không dùng (xem F-7, F-8), 15 `SetTextI18n` (CarFinderFragment), 5 `ClickableViewAccessibility` (GlassMediaPlayerView:166), `DrawAllocation` (GlassSlider:165). 50 cảnh báo `TrustAllX509TrustManager` nằm trong jar bên thứ ba, không phải mã của nhánh.

**Test pass nhưng không bắt được các lỗi bên dưới**: test của speedo controller gọi heartbeat trực tiếp, còn test FGS chỉ kiểm tra cờ `isAutomotiveSessionActive()`.

## 3. Các phát hiện

Mức độ: **Critical** = crash/hỏng chức năng chính hoặc chặn phát hành · **High** = sai dữ liệu hoặc hỏng nhìn thấy được · **Medium** = rò rỉ tài nguyên, hao pin, UX · **Low** = chất lượng mã.

### 🔴 F-1 (High → có thể Critical) — FGS `location` có thể làm hỏng việc bắt đầu phát nhạc từ nền *(chưa commit)*
`FermataMediaService.java:114-124`, `AndroidManifest.xml` (main + auto)

`promote()` thêm `FOREGROUND_SERVICE_TYPE_LOCATION` mỗi khi đã có quyền vị trí, **trên cả điện thoại, không chỉ Android Auto**. Từ Android 14, `startForeground()` với type `location` khi app đang ở nền (phát qua nút media Bluetooth, vô lăng, AA điều khiển lúc app chưa ở tiền cảnh) có thể ném `SecurityException`, vì quyền "while-in-use" không áp dụng cho FGS khởi chạy từ nền. `PlaybackForegroundCoordinator.acquire()` bắt lỗi → `host.failed()` chỉ ghi log → lease trả `null` → **lượt phát đó thất bại**.
Kèm theo:
- `updateNotification(STATE_PAUSED)` giờ gọi `keepForeground()` trong phiên AA (`:412`). Nếu service đã rời foreground thì đây là một lần khởi chạy FGS từ nền với type location → cùng loại lỗi.
- Nếu service đã foreground với type `mediaPlayback` thì `keepForeground()` không làm gì, nên type `location` không bao giờ được thêm. Lý do "giữ GPS chạy" vì thế không thực sự được đảm bảo.
- Chính sách Google Play: khai báo FGS `location` cho một media service đòi hỏi form khai báo và video minh hoạ trên Play Console. Mục đích "dashboard speedo" có thể bị từ chối.

**Đề xuất:** bỏ type `location` khỏi media service. Nếu thật sự cần GPS chạy nền, dùng một service riêng do người dùng chủ động bật (ví dụ "Recording trip", có sẵn string `speedo_recording_*`). Nếu vẫn giữ: chỉ thêm type khi AA đang chiếu **và** app đủ điều kiện, bọc `startForeground` để thử lại với `mediaPlayback` khi gặp `SecurityException`. Cần kiểm tra trên thiết bị Android 14/15.

### 🔴 F-2 (High) — Phát hiện đứng yên bằng IMU không bao giờ kích hoạt *(chưa commit)*
`GlassSpeedoTripController.java:224, 277, 506-512`

Trong `heartbeat`, `stationaryDetector.update()` được gọi ở dòng 277, sau đó lại gọi ở `drainToEngine()` (dòng 224, chạy ngay ở dòng 278). Lần gọi thứ hai thấy `n == 0`, nên `var = Double.MAX_VALUE` và `quietWindows` bị reset về 0. Vì `update()` cần `quietWindows >= 2`, nó **không bao giờ trả `true`**.
Hệ quả: khi mất GPS (hầm, hầm xe), `TripEngine.motion()` luôn coi xe đang chạy và cộng `gapMeters` theo tốc độ cuối cùng. Màn hình hiển thị quãng đường và tốc độ tăng liên tục khi xe đứng yên trong hầm. Nhánh "A4: parked in place" trong `recover()` cũng hiếm khi xảy ra.
**Đề xuất:** chỉ gọi `update()` một lần mỗi chu kỳ heartbeat, rồi truyền kết quả vào `drainToEngine(time, stationary)`. Thêm test tích hợp có IMU giả lập.

### 🔴 F-3 (High) — Vị trí seed cũ đến 60 giây bị gắn thời điểm hiện tại *(chưa commit)*
`GlassSpeedoTripController.java:110-122`, `SpeedoKinematicFusionHub.java:29, 89`

`queryRecentSeedLocation()` nhận fix cũ tối đa 60 giây. Fix đó đi qua `handleLocation()` → `fusionHub.addSample(location, now())` → `Fix.timeMs = nowTime`. Engine coi đây là vị trí **hiện tại**:
- Khi đang lái, fix thật tiếp theo cách seed hàng trăm mét trong ~1 giây → `plausible()` sai → bị loại. Mọi fix tiếp theo đều bị loại cho tới khi `dt > 8 s` → vào nhánh `recover()` cần thêm 3 fix. **Speedo trống khoảng 10 giây khi bắt đầu**.
- Khi `recover()` nối được (`chord ≤ 700 m`), quãng đường **trước khi bắt đầu trip** bị cộng vào `distance`.
- Fusion cũng lấy thời điểm nhận (`receiveTime`) thay cho `Location.getElapsedRealtimeNanos()` với mọi fix, nên các fix NETWORK đã cache được tính như fix mới.

**Đề xuất:** dùng `elapsedRealtimeNanos` của fix làm `timeMs`. Chỉ dùng seed để hiển thị vị trí, không dùng làm anchor. Hoặc loại seed cũ hơn ~2 giây.

### 🟠 F-4 (Medium/High) — Mỗi fix bị đếm 2–3 lần; độ chính xác bị ước lượng thấp hơn thực tế *(chưa commit)*
`GlassSpeedoTripController.java:350-402`, `CarLocatorManager.java:~545-575`, `ParkingLocationSmoother.java:144`

Cả speedo và CarLocator đều đăng ký **GPS + NETWORK + FUSED + PASSIVE** ở 1 Hz, 0 m. PASSIVE trả lại chính các fix GPS/FUSED của app; FUSED thường cũng là GPS. Phép trung bình nghịch đảo phương sai coi chúng là quan sát độc lập:
- `fusedAccuracy = sqrt(1/ΣW)` nhỏ hơn thực tế khoảng √2–√3 → `positionTrusted()` (≤ 20 m) dễ đạt → engine tin vị trí quá mức.
- Trong `ParkingLocationSmoother`, độ tin cậy "high" (≤ 12 m) bị thổi phồng, và điều kiện `stationaryFixes.size() >= 2` đạt được chỉ từ **một** fix thật.
- `handleTrackedLocation` ghi đè `currentUserLocation` bằng fix mới nhất bất kể độ chính xác: một fix cell 1 km có thể thay một fix GPS 5 m.
- Pin: NETWORK ở 1 Hz cộng thêm 4 listener, chạy suốt khi dashboard mở.

**Đề xuất:** khử trùng lặp theo (provider gốc, `elapsedRealtimeNanos`). Bỏ PASSIVE khi đã có GPS/FUSED. Chỉ dùng NETWORK làm phương án dự phòng khi không có fix GPS trong N giây. Chọn fix "tốt nhất" theo độ chính xác có tính đến tuổi của fix.

### 🟠 F-5 (Medium) — Rò thread mỗi lần dashboard được tạo lại *(đã commit + chưa commit)*
`DashboardFragment.java:131, 298-305`, `GlassDashboardController.java:279-293`, `GlassSpeedoTripController.java:78-84, 130-143`, `GlassOpenMeteoClient.java:121, 133`

`GlassDashboardController` được tạo lại ở mỗi `onViewCreated` và mỗi `reload()` (đổi day/night, đổi đơn vị, đổi pref…). Mỗi instance tạo:
- một `HandlerThread("GlassSpeedo")`, và `stop()` **không bao giờ gọi `quitSafely()`**;
- một `Executors.newSingleThreadExecutor()`, trong khi `GlassOpenMeteoClient.shutdown()` **không được gọi ở đâu cả**.

Bật/tắt day/night 10 lần để lại 20 thread sống. Ngoài ra, instance cũ ghi checkpoint *sau khi* instance mới đã `load()` → có thể mất vài giây dữ liệu trip.
**Đề xuất:** trong `stop()`, post checkpoint rồi gọi `thread.quitSafely()`. Gọi `weatherClient.shutdown()`. Tốt hơn nữa: giữ controller speedo ở mức Activity hoặc singleton.

### 🟠 F-6 (Medium) — Cache thời tiết bỏ qua đơn vị đo
`GlassOpenMeteoClient.java:139-143`

Khoá cache chỉ gồm lat/lon. Đổi từ km/h sang mph trong 20 phút vẫn nhận lại forecast °C, nhưng `bindWeather(..., isMetric)` hiển thị với nhãn °F. `lastLat`/`lastLon`/`cachedForecast` được ghi trên executor và đọc trên main thread mà không có `volatile`.
**Đề xuất:** đưa `isMetric` vào khoá cache (hoặc luôn lấy dữ liệu metric rồi tự quy đổi), và đánh dấu các field là `volatile`.

### 🟠 F-7 (Medium) — Long-press reset trip ngay lập tức, không xác nhận
`GlassSpeedoTripView.java:195, 358-365`

`confirmResetTrip()` không xác nhận gì mà chạy `resetTrip` ngay. String `speedo_reset_trip_confirm` có sẵn nhưng không dùng (lint UnusedResources). Một cú chạm giữ nhầm trên màn hình xe sẽ xoá toàn bộ trip, không có undo.
**Đề xuất:** hiển thị dialog xác nhận (dùng `GlassDialogBuilder`), hoặc snackbar "Undo" trong 5 giây.

### 🟠 F-8 (Medium) — Tính năng Pause/Resume trip chưa nối dây; string hứa hẹn sai
`GlassSpeedoTripView.java:60, 386`, `values/strings.xml`

`onToggleTripListener` được set nhưng **không bao giờ được gọi**, nên `pauseTrip()` không có đường vào từ UI. Các string `speedo_pause_trip`, `speedo_resume_trip`, `speedo_recording_title/text` ("GPS recording continues with the screen off") đều không dùng. Thực tế speedo dừng ở `onDestroyView`, không chạy khi tắt màn hình.
**Đề xuất:** hoặc hoàn thiện (nút trên card + service ghi trip riêng), hoặc xoá các string này trước khi phát hành để tránh bản dịch thừa và lời hứa sai.

### 🟡 F-9 (Medium) — Nav rail: timer auto-hide bị đặt lại ở mỗi lần layout; vuốt mép để back xung đột với cuộn *(chưa commit)*
`GlassNavigationRailView.java:548-609, 825+`
- `onLayout()` gọi `scheduleAutoHide()` (dòng 609). Mỗi `requestLayout` (ví dụ tiến trình media, cập nhật speedo) đặt lại timer 5 giây → rail có thể không bao giờ tự ẩn. `onLayout` cũng đọc pref `GLASS_RAIL_Y_RATIO` ở mỗi lần layout.
- Vùng 36 dp ở mép, vuốt vào trong ≥ 45 dp sẽ gọi `activity.onBackPressed()` (API deprecated, bỏ qua `OnBackPressedDispatcher`) bằng cách chặn touch của view con. Danh sách cuộn ngang hoặc slider bắt đầu gần mép sẽ kích hoạt back ngoài ý muốn.

**Đề xuất:** chỉ lên lịch auto-hide khi trạng thái mở thay đổi hoặc có tương tác. Cache tỷ lệ Y. Dùng `getOnBackPressedDispatcher()`. Không chặn touch khi view con có thể cuộn theo chiều ngang (`canScrollHorizontally`).

### 🟡 F-10 (Medium) — `queryBestKnownUserLocation` trả vị trí cache vĩnh viễn
`CarLocatorManager.java:145`

Khi `currentUserLocation` đã có giá trị thì luôn trả nó, bất kể đã cũ bao lâu. Thời tiết sẽ lấy theo nơi bắt đầu chuyến đi. Phép so sánh "tốt nhất" ở các dòng sau cũng ưu tiên fix **mới hơn** bất kể độ chính xác (một fix cell 2 km thắng một fix GPS 5 m cũ hơn 3 giây).
**Đề xuất:** kiểm tra tuổi của cache (ví dụ > 2 phút thì truy vấn lại). Chấm điểm fix theo `accuracy + tuổi × hệ số`.

### 🟡 F-11 (Medium) — Quyền riêng tư: gửi toạ độ chính xác đến Open-Meteo
`GlassOpenMeteoClient.java:152`

Gửi `%.4f` (khoảng 11 m) cho bên thứ ba. Dự báo thời tiết chỉ cần `%.2f` (khoảng 1 km). Cần cập nhật privacy policy hoặc Data safety trên Play nếu chưa có.

### 🟡 F-12 (Low/Medium) — NotificationListener
`AutomotiveNavNotificationService.java:61, 88`
- `listeners.contains(packageName)` là so khớp chuỗi con: package `me.app.fermataX` khớp nhầm với `me.app.fermataX.auto.test`. Dùng `NotificationManagerCompat.getEnabledListenerPackages()`.
- `onNotificationRemoved` xoá trạng thái chỉ đường khi *bất kỳ* thông báo nào của app dẫn đường bị gỡ (Maps có nhiều thông báo).
- Không xử lý `onListenerDisconnected`, nên trạng thái cũ còn treo lại.

### 🟡 F-13 (Low) — i18n và chuỗi cứng
- `GlassDashboardController.java:255` dùng chuỗi tiếng Anh cứng `"Shortcut target unavailable: "`.
- `GlassNavigationRailView.java:287` dùng `" Navigation Rail"` cứng trong contentDescription.
- `values-vi` thiếu nhóm `control_aa_*`, `control_open_*` (khoảng 20 key) so với `values`.
- Bản dịch speedo cho es/pt/ru/zh-rTW nằm trong `speedo_strings.xml` **untracked**. Nếu chỉ commit `values/strings.xml`, các locale đó sẽ hiển thị tiếng Anh.

### 🟡 F-14 (Low) — Kiến trúc: logic theme Glass lọt vào thư viện `depends/utils`
`ActivityDelegate.java` được thêm `isGlassTheme()`, `getGlassTextPrimary()` (mã màu cứng `0xFF00E5FF`…), `createSwitch/createSlider`. Thư viện dùng chung giờ biết đến một theme cụ thể của app, và các màu bị nhân bản so với `GlassThemeColors`. Nên dùng một interface `ThemeProvider` trung tính, do module `fermata` cài đặt.

### 🟡 F-15 (Low) — `handleShortcutLaunch` so khớp bằng `endsWith`
`GlassDashboardController.java:214-219`: một addon có id kết thúc bằng `settings` hoặc `car_finder` sẽ bị chuyển nhầm sang Settings/CarFinder. Nên so khớp chính xác.

### ⚪ F-16 — Vệ sinh nhánh và quy trình
1. **Commit `e108dcea` "fix(glass): resolve concurrency…"** chứa các thay đổi sản phẩm không liên quan đến glass:
   - **Gỡ module Cast** (`:cast` vào `retiredProductionModules`, xoá meta-data `CastOptionsProvider` trong manifest);
   - xoá `FermataContentAddon`, `MediaEnginePrefsBuilder` (màn hình chọn engine), `SplitterView`;
   - xoá toàn bộ `promo/` (video, ảnh, script), xoá link GitHub trong Settings, sửa lại CHANGELOG.

   Không có gì bị gãy (build và test đều xanh), nhưng sẽ khó revert hoặc review và mất khả năng truy vết. Nên tách thành commit hoặc PR riêng có mô tả rõ.
2. CHANGELOG vẫn ghi "Kept the 2 best interfaces (Material You & Classic)" trong khi nhánh thêm theme thứ ba (Glass).
3. Có 18 file plan/spec trong `docs/superpowers/` thuộc các tính năng khác (YouTube quality, IPTV, TikTok, fullscreen…) được commit trong nhánh này.
4. File rác chưa ignore ở thư mục gốc:
   - `temp_fermata.xml`: dump SharedPreferences, **không khớp** pattern mới `fermata_*.xml`;
   - `ui.xml`: dump UI hierarchy;
   - `run_dhu.bat`: chứa serial thiết bị `15c36230` và đường dẫn SDK cá nhân.

   Cần thêm vào `.gitignore` hoặc xoá trước khi `git add -A`.
5. `appVersionCode` 319 → 326 chưa commit. Hãy kiểm tra xem 320–325 đã được dùng chưa.
6. Tác giả commit là `Codex <codex@local>`, không phải danh tính thật của người phát triển. Cần chú ý nếu repo yêu cầu DCO hoặc chữ ký.

## 4. Những điểm làm tốt

- `TripEngine` thiết kế cẩn thận: chỉ dùng thời gian `elapsedRealtime`, có giới hạn plausibility, cần 3 fix nhất quán mới nối gap, có cận trên/dưới khi ước lượng gap, phân biệt `estimated`/`uncertainty`, không lưu toạ độ qua lần khởi động lại process (`TripStore`).
- `CarLocatorManager` đã sửa đúng lỗi `SecurityException` BLUETOOTH_CONNECT và kiểm tra quyền trước `getLastKnownLocation`. `String.formatted` (lỗi NewApi) đã được thay.
- `GlassMediaPlayerController` cân bằng bind/unbind và dừng ticker đúng chỗ. `GlassNavigationRailView` dọn callback trong `onDetachedFromWindow`.
- `CarLocatorManager.userLocationListeners` dùng `CopyOnWriteArraySet`, nên an toàn khi listener tự gỡ trong callback.
- Độ phủ test lớn (3 729 test), có test chính sách manifest, ranh giới kiến trúc, đồ thị dependency release.

## 5. Kết luận

**Chưa sẵn sàng merge/phát hành ở trạng thái working tree hiện tại.**

Cần xử lý trước khi commit phần đang dở:
1. **F-1** (FGS location): rủi ro hỏng việc phát nhạc trên Android 14+ và rủi ro chính sách Play.
2. **F-2, F-3** (speedo): số liệu sai và nhìn thấy được ngay trên màn hình xe.
3. **F-5** (rò thread): tích tụ theo thời gian sử dụng.
4. Dọn file rác (F-16.4) và commit đủ bản dịch `speedo_strings.xml`.

Phần đã commit (`main..291f3acc`) đạt mức **READY_WITH_CONDITIONS**, như audit 2026-10-18. Bổ sung thêm: F-6, F-7, F-11 nên sửa trước khi phát hành, và nên tách commit `e108dcea`.

Cần kiểm tra trên thiết bị (unit test không thể thay thế):
- Phát nhạc qua nút Bluetooth khi app ở nền, trên Android 14/15 (F-1).
- Lái qua hầm hoặc dừng trong hầm xe (F-2).
- Mở dashboard khi đang chạy (F-3).
- Bật/tắt day/night nhiều lần rồi xem số thread bằng `adb shell ps -T` (F-5).
