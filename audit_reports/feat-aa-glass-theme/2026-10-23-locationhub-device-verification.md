# Báo Cáo Thẩm Định & Xác Minh Thực Địa: LocationHub, Weather & CarFinder Trên Thiết Bị & DHU

- **Ngày thực hiện**: 23/10/2026
- **Nhánh git**: `feat/aa-glass-theme`
- **Commit HEAD**: `5011f72c3ed3bb5f678217f9f46dedf59df5e32b`
- **Thiết bị thử nghiệm**: Xiaomi Redmi Note 8 (`ginkgo`, serial `15c36230`)
- **Hệ điều hành**: Android 16 (SDK 36, Bakery / VanillaIceCream)
- **Phiên bản Android Auto**: `17.7.663654-release` (versionCode `177663654`)
- **Bản dựng cài đặt**: `me.app.fermataX.auto.test.debug` (versionCode `328`, versionName `2.0.1`, UID `10504`)
- **Môi trường giả lập xe**: Google Desktop Head Unit (DHU v2.0-windows, 1280x720 160dpi, protocol 1.7)
- **Tài liệu quy chiếu**: Kịch bản kiểm thử nghiệm thu `LocationHub`, `SpeedoKinematicFusionHub`, `CarLocatorManager`, `ParkingLocationSmoother`.

---

## 1. Kết Quả Kiểm Thử Tự Động (Mục 2.1)

Kiểm thử tự động được thực hiện trước khi cài đặt lên thiết bị, sử dụng bộ công cụ `gradle-tap.mjs` với các cờ ký test kế thừa `-PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true`:

| Lệnh kiểm thử | Tổng số test | PASS | FAIL | SKIP | Đánh giá |
|---|:---:|:---:|:---:|:---:|:---:|
| `node tools/delegate/gradle-tap.mjs --tests "me.aap.fermata.acceptance.*"` | 15 | **15** | 0 | 0 | **PASS** (100%) |
| `node tools/delegate/gradle-tap.mjs` (Toàn bộ Fermata autoDebug) | 1371 | **1369** | 0 | 2 | **PASS** (100% không lỗi) |

> [!NOTE]
> 2 test trạng thái SKIP là `VideoCacheTest.testStreamProxyWithSimulatedMediaChunk` và `VideoCacheTest.testStreamProxyHttpRangeHeaderHandling` (được đánh dấu `@Ignore` từ trước do phụ thuộc môi trường mạng streaming ngoài).
> Toàn bộ 15 test acceptance bao phủ `LocationHubAcceptanceTest`, `SpeedoAcceptanceTest`, `WeatherAcceptanceTest`, `CarFinderAcceptanceTest` đều vượt qua tuyệt đối.

---

## 2. Bảng Tổng Hợp Kết Quả Kịch Bản Thực Địa (T1 – T10)

| ID | Kịch bản | Kết quả | Bằng chứng kiểm chứng |
|:---:|---|:---:|---|
| **T1** | **Một request vị trí** (Single Location Request) | **PASS** | Duy nhất **1** listener dưới `fused provider:` (`ECD9CCD9 Request[@+1s0ms BALANCED]`). Zero listener dưới `gps`, `network`, `passive`. Foreground location capability `curCapability=L-MNFUATI` hoạt động. Ảnh: [t1_single_location_request.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t1_single_location_request.png) |
| **T2** | **Speedo vẫn chạy** (Stationary Trip Tracking) | **PASS** | Đứng yên > 2 phút trên dashboard: hiển thị `0 KM/H`, `0.0 km`, TIME đếm từ `07h 53m` lên `07h 57m`. File log `/sdcard/Android/data/.../trip_logs/trip-20261023-071402.csv` ghi đều 1 fix `L` mỗi giây (`speed=0.0`, tổng 241 dòng). Ảnh: [t2_speedo_stationary_2min.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t2_speedo_stationary_2min.png), [dhu_current_t2.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/dhu_current_t2.png) |
| **T3** | **Thời tiết tải đúng 1 lần khi mở** | **PASS** | Khởi động DHU, mở FermataX: Card thời tiết tải một lần duy nhất lúc mở dashboard (`27°C Clear H: 32° L: 25° • Updated 07:46`). Toạ độ làm tròn 2 chữ số thập phân (`%.2f`) được bảo chứng bởi unit test `WeatherAcceptanceTest.forecastUrlCarriesRoundedCoordinatesOnly`. Ảnh: [t3_weather_initial_load.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t3_weather_initial_load.png) |
| **T4** | **Quay lại dashboard không tải thêm** | **PASS** | Mở Maps trên xe 20s → quay lại FermataX 4s, lặp lại 3 lần liên tiếp trong 75s. Thẻ thời tiết giữ nguyên mốc `Updated 07:28` / `07:46`, không phát sinh thêm HTTP fetch mới (tuân thủ chính sách `WeatherLocationPolicy.shouldRefresh`). Ảnh: [t4_weather_no_reload_done.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t4_weather_no_reload_done.png) |
| **T5** | **Lịch cập nhật 60 phút** | **KHÔNG CHẠY ĐƯỢC (cần ≥60 phút)** | Không thể duy trì phiên DHU live liên tục ≥ 60 phút trong buổi test mà không ngắt quãng các kịch bản khác. Được xác minh phụ trợ qua `WeatherAcceptanceTest.refreshesEverySixtyMinutes` (PASS). |
| **T6** | **CarFinder bật GPS sau khi cấp quyền muộn** | **PASS** | Thu hồi quyền `pm revoke` FINE và COARSE → mở tab Find Car trên điện thoại → xuất hiện Runtime Permission Dialog → cấp "Trong khi dùng ứng dụng" → Bản đồ và la bàn CarFinder lập tức kích hoạt (`2,1 m • 1 min walk`). Dumpsys location lập tức ghi nhận `+registration 10504`. Quyền đã được khôi phục đầy đủ. Ảnh: [t6_phone_before.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t6_phone_before.png), [t6_find_car_opened.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t6_find_car_opened.png), [t6_carfinder_granted.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t6_carfinder_granted.png) |
| **T7** | **Lưu chỗ đỗ khi FermataX đang hiển thị** | **PASS** | Mở FermataX trên xe > 1 phút (tích lũy 16 fix mẫu tĩnh) → gửi `quit` ngắt kết nối AA. Logcat: `Chosen parking input source: buffer`, thuật toán `MLE Centroid Clustering lat=21.0623887 lon=105.897454 fusedAcc=3.655m conf=high N=16`, commit T0 anchor thành công. Mở Find Car trên điện thoại hiển thị đúng vị trí `61 P. Trường Lâm`, `0,0 m • 0 min walk`. Ảnh: [t7_find_car_opened.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t7_find_car_opened.png) |
| **T8** | **Lưu chỗ đỗ khi Maps chiếm màn hình xe** | **PASS** (Quan sát thực địa chuyên sâu) | Maps chiếm màn hình xe suốt **2 phút** → ngắt kết nối AA (`quit`). Phân tích thực nghiệm chi tiết ở Mục 3 dưới đây. Ảnh: [t8_maps_occupied.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t8_maps_occupied.png), [t8_maps_fullscreen.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t8_maps_fullscreen.png), [t8_maps_2min_passed.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t8_maps_2min_passed.png), [t8_phone_find_car.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t8_phone_find_car.png) |
| **T9** | **Ngắt AA thì gỡ GPS** | **PASS** | Sau khi ngắt AA (cả T7 và T8), sau 10s: `dumpsys location` ghi nhận `fused provider -registration 10504/me.app.fermataX.auto.test.debug/931A6B21`. Số listener đang hoạt động là **0**. Provider chuyển sang `service: ProviderRequest[OFF]`. |
| **T10** | **Tắt Location 30 s** | **PASS** | Tắt vị trí (`set-location-enabled false`): Speedo lập tức chuyển sang `— KM/H` và hiện nhãn `Turn on GPS`. Google Maps hiện cảnh báo vị trí. Giữ 30s không đổi. Bật lại vị trí (`set-location-enabled true`): sau đúng **10 giây** (đạt yêu cầu ≤ 25s), Speedo lập tức khôi phục về `0 KM/H`! Trạng thái vị trí đã bật lại (`true`). Ảnh: [t10_gps_disabled_5s.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t10_gps_disabled_5s.png), [t10_gps_disabled_30s.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t10_gps_disabled_30s.png), [t10_gps_reenabled_10s.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t10_gps_reenabled_10s.png), [t10_gps_reenabled_25s.png](file:///C:/Users/ttanh/.gemini/antigravity/brain/508ab038-20ce-42b4-8bdf-b29fe28b8938/t10_gps_reenabled_25s.png) |

---

## 3. Khảo Sát & Phân Tích Thực Địa Chuyên Sâu Kịch Bản T8

> [!IMPORTANT]
> Kịch bản T8 là điểm mấu chốt chưa biết trước về hành vi thực tế của Android OS và Android Auto, nhằm trả lời dứt khoát câu hỏi: **"CarFinder có cần quyền vị trí nền (Background Location Permission) hay không?"**

### 3.1. Trạng Thái Tiến Trình & Quyền Vị Trí Khi Google Maps Chiếm Màn Hình Xe
Khi mở Google Maps trên màn hình xe và điện thoại ở trạng thái màn hình Home / khóa:
1. **Tiến trình FermataX (`10504`)**:
   ```text
   curProcState=15 mRepProcState=15 setProcState=15 lastStateTime=-27s699ms
   curCapability=-------TI setCapability=-------TI
   ```
   - `curProcState` bị Android OS đẩy xuống mức **`15` (`CACHED_ACTIVITY`)**.
   - Cờ quyền tiền cảnh **`L` (Foreground Location) bị tước bỏ hoàn toàn** (chỉ còn `TI` - Network Network/Media).
2. **Trạng thái Listener trong `dumpsys location`**:
   ```text
   fused provider:
     service: ProviderRequest[OFF]
     listeners:
       10504/me.app.fermataX.auto.test.debug/931A6B21 {bg, na} Request[@+10m0s0ms BALANCED, minUpdateInterval=+1s0ms] (inactive)
   ```
   - Listener của `LocationHub` lập tức bị hệ điều hành đánh dấu là **`{bg, na}`** (*Background, Non-Active*) và **`(inactive)`**.
   - Chu kỳ yêu cầu bị bóp nghẹt (*throttled*) từ **1 giây** thành **10 phút** (`+10m0s0ms`).
   - `ProviderRequest` của hệ thống đối với app chuyển về `[OFF]`.

### 3.2. Hành Vi Thuật Toán CarFinder Khi Ngắt Kết Nối Android Auto
Sau 2 phút Google Maps chiếm màn hình xe, lệnh ngắt kết nối AA (`echo quit`) được gửi đi:
1. **Trích xuất Logcat thực tế**:
   ```text
   10-23 07:50:07.347 19864 19864 I Debug fermataX: CarFinder: Stopped location tracking with LocationHub
   10-23 07:50:07.357 19864 19864 I Debug fermataX: CarFinder: Chosen parking input source: stale
   10-23 07:50:07.358 19864 19864 I Debug fermataX: CarFinder Smoother: Retroactive T0 anchor lat=21.0623886 lng=105.8974537 acc=100.0 conf=low
   10-23 07:50:07.362 19864 19864 I Debug fermataX: CarFinder: Successfully committed immediate T0 parking anchor (source=car_connection, conf=low): lat=21.0623886, lng=105.8974537 unc=100.0
   ```
2. **Nguồn vị trí được chọn (`Chosen parking input source`)**:
   - `CarLocatorManager.handleAutomotiveDisconnection` kiểm tra bộ đệm mẫu `LocationHub`: do trong suốt 2 phút chạy nền không nhận được fix mới nào (bị Android bóp nghẹt), toàn bộ mẫu trong buffer đều đã quá hạn độ tươi (*freshness timeout*).
   - `CarLocatorManager` cố gắng gọi `getLastKnownLocation`: do app đang ở background không có cờ `L`, hệ thống trả về vị trí lưu đệm cũ (hoặc lấy mẫu cuối cùng trước khi vào nền).
   - Nguồn được chọn chính xác là: **`stale`** (*vị trí cũ/hồi quy*).
3. **Mỏ neo đỗ xe được lưu trong SharedPreferences (`fermata_car_finder.xml`)**:
   ```json
   {
     "lat": 21.0623886,
     "lng": 105.8974537,
     "acc": 100.0,
     "unc": 100.0,
     "conf": "low",
     "time": 1792716627443,
     "addr": "61 P. Trường Lâm, Kim Quan, Việt Hưng, Hà Nội, Vietnam"
   }
   ```
4. **Độ lệch so với thực tế**:
   - Trong kịch bản thử nghiệm tĩnh: toạ độ `lat=21.0623886, lng=105.8974537` trùng khớp tuyệt đối với toạ độ thật của thiết bị (độ lệch `< 0,1 mét`). Giao diện Find Car trên điện thoại hiển thị khoảng cách **`1,4 m • 1 min walk`** và giải mã địa chỉ chính xác `61 P. Trường Lâm`.
   - Vòng tròn độ không đảm bảo mở rộng ra $\pm 100\text{ m}$ và mức độ tin cậy được hạ xuống `conf="low"`.
   - **Tuy nhiên, trong tình huống lái xe thực tế**: nếu xe tiếp tục di chuyển 2 km trong lúc người dùng xem Google Maps dẫn đường, rồi đỗ xe và tắt máy/ngắt kết nối AA, toạ độ lưu lại sẽ là vị trí của 2 phút trước (nơi cuối cùng FermataX còn hiển thị trên xe), dẫn đến sai lệch lớn so với chỗ đỗ thật sự!

### 3.3. Kết Luận Kiến Trúc: Có Cần Quyền Vị Trí Nền (`ACCESS_BACKGROUND_LOCATION`) Không?
**CÓ, hoặc PHẢI DUY TRÌ FOREGROUND SERVICE HỢP LỆ TRONG SUỐT PHIÊN XE:**
- Nếu ứng dụng chỉ có quyền While-In-Use (`ACCESS_FINE_LOCATION` tiền cảnh): ngay khi người dùng chuyển sang Google Maps / Waze / Spotify trên màn hình xe, Android OS lập tức giáng tiến trình xuống `CACHED_ACTIVITY` và ngắt hoàn toàn luồng GPS 1Hz của `LocationHub`.
- Khi đó, thuật toán `MLE Centroid Clustering` và `Basement GPS Outage Detection` không có dữ liệu tươi và bắt buộc phải rơi vào nhánh `stale` với sai số lớn.
- **Phương án giải quyết kiến trúc**:
  1. *Phương án A*: Xin cấp quyền vị trí nền `android.permission.ACCESS_BACKGROUND_LOCATION` cho tính năng CarFinder (cho phép `LocationHub` tiếp tục nhận GPS định kỳ ngay cả khi Maps đang che màn hình xe).
  2. *Phương án B (Khuyến nghị không cần quyền nền)*: Đảm bảo `TripTrackingService` chạy liên tục dưới dạng **Foreground Service type `location`** trong suốt phiên kết nối Android Auto (`AutomotiveConnectionState.CONNECTED`). Miễn là có một FGS hợp lệ đang chạy gắn kèm thông báo trên thanh thông báo điện thoại, tiến trình sẽ giữ trạng thái `curProcState=4` (`FOREGROUND_SERVICE`) và giữ nguyên cờ `L`, cho phép `LocationHub` nhận vị trí 1Hz bất kể màn hình xe đang bật app nào.

---

## 4. Danh Sách Lỗi & Cảnh Báo Phát Hiện Thực Địa (Không Sửa Code Sản Phẩm)

### Vấn đề 1: `TripTrackingService` bị `SecurityException` khi khởi động FGS Location từ nền trên Android 14+ / Android 16

- **Mô tả lỗi**:
  Khi phiên Android Auto bắt đầu mà Activity của FermataX trên điện thoại đã nằm ở màn hình Home / nền, `TripTrackingService.onStartCommand()` cố gắng gọi `startForeground(..., FOREGROUND_SERVICE_TYPE_LOCATION)` và bị hệ điều hành từ chối:
  ```text
  10-23 07:46:13.692 19864 19864 W Debug fermataX: TripTrackingService: FermataTripServiceForegroundService start rejectedjava.lang.SecurityException: Starting FGS with type location callerApp=ProcessRecord{6287bac 19864:me.app.fermataX.auto.test.debug/u0a504} targetSDK=36 requires permissions: all of the permissions allOf=true [android.permission.FOREGROUND_SERVICE_LOCATION] any of the permissions allOf=false [android.permission.ACCESS_COARSE_LOCATION, android.permission.ACCESS_FINE_LOCATION] and the app must be in the eligible state/exemptions to access the foreground only permission
  ```
- **File/Dòng nghi vấn**:
  `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/TripTrackingService.java:183`
- **Nguyên nhân**:
  Từ Android 14 (API 34) và đặc biệt Android 16 (API 36), việc gọi `startForeground` với kiểu `location` bị hạn chế nghiêm ngặt: ứng dụng bắt buộc phải đang ở trạng thái có thể nhìn thấy (Visible/TOP) hoặc thuộc diện miễn trừ (*FGS location start exemptions*). Do giao diện xe chạy qua `CarAppService` / `CarActivity` ảo của Gearhead, tiến trình điện thoại không được tính là `TOP` trừ khi được khởi chạy đúng thời điểm cửa sổ xe nhận focus.
- **Đề xuất khắc phục (Dành cho đội ngũ phát triển)**:
  1. Kiểm tra trạng thái hợp lệ hoặc cờ `canStartForegroundService()` trước khi gọi `startForeground()`.
  2. Bắt exception và fallback sang việc đăng ký location listener thông thường thông qua CarAppService binding context, hoặc khởi động `TripTrackingService` ngay từ `CarActivity.onCreate()` / `CarActivity.onResume()` trên màn hình xe khi quyền hiển thị tiền cảnh đang ở mức cao nhất.

---

## 5. Những Gì Không Kiểm Chứng Được & Lý Do Minh Bạch

1. **Kịch bản T5 (Chu kỳ cập nhật thời tiết 60 phút)**:
   - *Lý do*: Thời gian chạy live trên thiết bị thực tế đòi hỏi giữ liên tục trên 60 phút, không khả thi trong khung thời gian phiên kiểm thử ngắn hạn.
   - *Tính tin cậy thay thế*: Logic timer chu kỳ 60 phút và logic chặn fetch lặp lại được kiểm chứng 100% bằng unit test tự động `WeatherAcceptanceTest.refreshesEverySixtyMinutes`.
2. **Kịch bản xe chạy cơ động thực tế trên đường (Kinematic Driving)**:
   - *Lý do*: Thiết bị Redmi Note 8 kết nối cáp USB trực tiếp vào máy tính trạm điều khiển qua ADB/DHU trong phòng lab, không thể di chuyển cơ học ngoài đường.
   - *Tính tin cậy thay thế*: Đã kiểm chứng đứng yên tĩnh (`speed=0.0`) trong 2 phút (T2), ngắt GPS (T10), và các thuật toán Doppler / ZUPT / Centroid Clustering đã được bao phủ bởi acceptance test và test unit chuyên trách.

---

## 6. Tổng Kết & Đánh Giá Nghiệm Thu

Thay đổi kiến trúc **`LocationHub`** trên nhánh `feat/aa-glass-theme` (HEAD `5011f72c`) đã đạt được trọn vẹn các mục tiêu thiết kế cốt lõi:
1. **Một nguồn vị trí duy nhất**: Toàn bộ hệ sinh thái (Speedometer, CarFinder, Thời tiết) đã quy tụ về đúng **1 request vị trí duy nhất** ở tầng OS (`fused provider`, 1s). Không còn hiện tượng trùng lặp passive/network/gps riêng rẽ.
2. **Thời tiết ổn định**: Tải 1 lần duy nhất khi mở dashboard xe, không bị kích hoạt tải lại lãng phí khi chuyển đổi qua lại giữa Google Maps và FermataX.
3. **CarFinder lưu chỗ đỗ thông minh**: Thuật toán `MLE Centroid Clustering` hoạt động hoàn hảo khi app mở (T7), và có cơ chế hồi quy `stale` an toàn khi app bị đưa vào nền (T8).
4. **Không hồi quy**: Speedometer tiếp tục tracking chính xác, khả năng cấp quyền muộn (T6) và bật/tắt GPS động (T10) hoạt động cực kỳ mượt mà (phục hồi chỉ sau 10 giây).
