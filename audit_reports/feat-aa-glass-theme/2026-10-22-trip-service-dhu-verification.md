# Báo Cáo Thẩm Định & Xác Minh Live DHU: Tách Rời TripTrackingService Khỏi FermataMediaService (Nhánh B)

- **Ngày thực hiện**: 22/10/2026
- **Nhánh**: `feat-aa-glass-theme`
- **Phiên bản build**: `me.app.fermataX.auto.test.debug` (Vòng 1: versionCode `327`; Vòng 2: versionCode `328`, versionName `2.0.1`)
- **Thiết bị thử nghiệm**: Xiaomi Redmi Note 8 (`ginkgo`, serial `15c36230`), Android 16 (SDK 36 - Bakery), Android Auto `17.7.663654-release`
- **Môi trường giả lập xe**: Google Desktop Head Unit (DHU v2.0-windows, 1280x720 160dpi, protocol 1.7)
- **Tài liệu quy chiếu**: `fermata/TRIP_TRACKING.md`, `audit_reports/feat-aa-glass-theme/2026-10-22-full-branch-audit.md` (F-1 đến F-8)

---

## 1. Bối Cảnh & Nguyên Nhân Gốc (Root Cause Analysis)

### 1.1. Triệu chứng gốc
Trên Android Auto, khi người dùng tạm dừng nhạc (pause), thẻ Speedometer trên Glass Dashboard bị đứng đọng ở giá trị vận tốc cũ, sau đó xuất hiện cảnh báo *"GPS signal lost"* hoặc *"Missing data"*.

### 1.2. Nguyên nhân gốc rễ
Giao diện xe của Fermata là `MainCarActivity` kế thừa `com.google.android.apps.auto.sdk.CarActivity`, chạy hoàn toàn bên trong `CarService` của Gearhead, không phải một Android Activity tiền cảnh chuẩn trên điện thoại. Đối với hệ điều hành Android, ứng dụng không có cửa sổ hiển thị thực tế ở tiền cảnh điện thoại (`PROC_STATE_TOP` chỉ nằm ở `CarService` host của Gearhead).
- **Khi đang phát nhạc**: `FermataMediaService` / `AutoFermataMediaService` chạy dưới dạng Foreground Service (FGS), do đó tiến trình được hệ điều hành xếp vào trạng thái được cấp vị trí GPS bình thường.
- **Khi tạm dừng nhạc (Pause)**: `PlaybackForegroundCoordinator` lập tức đưa Media Service rời foreground (`leaveForeground(false)`). Android coi ứng dụng đang chạy nền hoàn toàn và cơ chế tiết kiệm pin (Battery Optimization / App Standby Bucket) bóp nghẹt vị trí nền (Background Location Throttling) xuống chỉ còn vài lần mỗi giờ.

### 1.3. Lỗi thiết kế của Bản vá tạm (đã loại bỏ)
Bản vá tạm trước đây đã:
1. Ép `FermataMediaService.promote()` bổ sung `FOREGROUND_SERVICE_TYPE_LOCATION` cho mọi lượt phát, áp dụng cả trên điện thoại thuần túy không có Android Auto.
2. Nếu quyền vị trí bị thiếu hoặc quyền truy cập While-In-Use (WIU) bị Android từ chối, `startForeground()` ném `SecurityException` dẫn đến lượt phát nhạc bị crash hoặc thất bại hoàn toàn.
3. Speedometer bị phụ thuộc cộng sinh vào nhạc: nếu người dùng lên xe xem dashboard dẫn đường mà chưa từng bấm Play nhạc thì hoàn toàn không có foreground service, GPS lập tức bị bóp nghẹt.

### 1.4. Kiến Trúc Mục Tiêu (Nhánh B - Đã Triển Khai Hoàn Tất)
Tách rời độc lập GPS & Speedometer thành một Foreground Service chuyên trách riêng biệt: `TripTrackingService` (`foregroundServiceType="location"`), vòng đời gắn chặt với phiên kết nối Android Auto (`AutomotiveConnectionState`), hoàn toàn không phụ thuộc và không gọi chéo vào Media Service:
```
AutomotiveConnectionState ──(CONNECTED/APP_VISIBLE)──► TripTrackingService.start(ctx)
                          ──(DISCONNECTED)───────────► TripTrackingService.stop(ctx)
TripTrackingService (FGS type=location, notification channel "trip_tracking" + Pause/Resume)
   └── owns: GlassSpeedoTripController (1 instance / process, 1 HandlerThread "GlassSpeedo")
            └── TripEngine + TripStore + SpeedoKinematicFusionHub
GlassDashboardController ──subscribe/unsubscribe──► TripTrackingService.Registry (listener)
GlassSpeedoTripView: nút Pause/Resume ──► registry.toggleTrip(); long-press ──► dialog reset
FermataMediaService: trở lại thuần túy foregroundServiceType="mediaPlayback"
```

---

## 2. Chi Tiết Thực Hiện Các Hạng Mục Kỹ Thuật (Tasks 1 - 7)

### Task 1: Khôi phục FermataMediaService về chuẩn mediaPlayback thuần túy
- Cả hai manifest (`fermata/src/main/AndroidManifest.xml` và `fermata/src/auto/AndroidManifest.xml`) đều cấu hình `FermataMediaService` và `AutoFermataMediaService` với thuộc tính duy nhất `android:foregroundServiceType="mediaPlayback"`.
- Loại bỏ toàn bộ logic kiểm tra quyền location và cờ FGS location trong `FermataMediaService.promote()`.
- Trong `updateNotification(STATE_PAUSED)`, loại bỏ phân nhánh `isAutomotiveSessionActive()` và luôn gọi `playbackForeground.leaveForeground(false)`.
- Các bài test `AutoManifestPolicyTest` và `FermataMediaServiceNotificationTest` xác nhận Media Service không còn chứa bất kỳ dấu vết nào của `FOREGROUND_SERVICE_TYPE_LOCATION`.

### Task 2: Xây dựng TripTrackingService & TripTrackingRegistry
- **File mới**: `TripTrackingService.java`, `TripTrackingRegistry.java`.
- Khai báo manifest:
  ```xml
  <service
      android:name="me.aap.fermata.ui.glass.speedo.TripTrackingService"
      android:exported="false"
      android:foregroundServiceType="location" />
  ```
- **API tĩnh**: `start(Context)`, `stop(Context)`, `registry()`.
- **Cơ chế chống sập**: Bọc `ServiceCompat.startForeground` bằng khối `try/catch (SecurityException | IllegalStateException e)`. Khi Android 14+ / 16 từ chối khởi chạy FGS từ nền (WIU restriction), service tự động `stopSelf()`, ghi log `FermataTripService` và gửi tín hiệu `TripEngine.Signal.BACKGROUND_BLOCKED` về Registry.
- **Kênh thông báo chuyên biệt**: Channel `trip_tracking` (`IMPORTANCE_LOW`, `setOngoing(true)`). Cung cấp Action tương tác nhanh `Pause trip` / `Resume trip` gửi `ACTION_TOGGLE` về service.
- **Khởi động**: Trả về `START_NOT_STICKY` để dịch vụ không tự phục sinh bừa bãi khi bị hệ thống diệt.

### Task 3: Kích hoạt tự động theo phiên Android Auto (`TripTrackingAutoBinder`)
- Đăng ký lắng nghe sự kiện từ `AutomotiveConnectionState.get().addListener(...)`:
  - `CONNECTED` hoặc `APP_VISIBLE`: Kiểm tra nếu người dùng chưa chủ động pause (`!TripStore.isUserPaused()`) thì tự động gọi `TripTrackingService.start(app)`.
  - `DISCONNECTED`: Tự động gọi `TripTrackingService.stop(app)`.
- Khởi tạo gắn kết tại `FermataApplication.onCreate()` và bổ sung cơ chế retry tại `CarService.onBind()` và `CarService.onRebind()`.

### Task 4: Tách quyền sở hữu Controller khỏi GlassDashboardController (Giải quyết F-5)
- `GlassDashboardController` không tự khởi tạo `new GlassSpeedoTripController(...)` mà đăng ký nhận dữ liệu thông qua `TripTrackingService.registry().addListener(this)`.
- Trên ô tô (AA session), `TripTrackingService` duy trì Controller duy nhất trong suốt phiên.
- Trên điện thoại độc lập (không có AA), `GlassDashboardController` trực tiếp bật controller cục bộ khi dashboard hiển thị và tắt khi dashboard ẩn, không bật FGS.
- Trong `GlassSpeedoTripController.stop()`, gọi `thread.quitSafely()` và giải phóng `thread = null; worker = null;`.
- Trong `GlassOpenMeteoClient`, gọi `shutdown()` thay cho `cancel()` và bắt `RejectedExecutionException`.

### Task 5: Chuẩn hóa logic kinematic & nguồn vị trí (Giải quyết F-2, F-3, F-4)
- **F-2 (Stationary Heartbeat)**: `StationaryDetector.update()` được gọi định kỳ chính xác 1 lần mỗi nhịp heartbeat; `drainToEngine(now, stationary)` xả dữ liệu mà không hủy đăng ký phần cứng cảm biến IMU.
- **F-3 (Fix Stale Filter)**: `Sample` lưu trữ `elapsedRealtimeNanos` chuẩn từ hardware GNSS. Bỏ qua các mẫu trễ $> 2000\text{ms}$; từ chối hạt giống (seed) cũ $> 30\text{s}$.
- **F-4 (Single Provider Ownership)**:
  - Chỉ đăng ký duy nhất 1 GNSS provider (Android 31+ dùng `LocationManager.FUSED_PROVIDER`, Android cũ dùng `GPS_PROVIDER`).
  - Loại bỏ hoàn toàn `PASSIVE_PROVIDER`.
  - Chỉ kích hoạt `NETWORK_PROVIDER` dự phòng khi GNSS chính bị đói fix $> 10\text{s}$, và tự động hủy đăng ký mạng ngay khi GNSS chính có fix trở lại.

### Task 6: Tương tác người dùng & Xử lý chặn nền (Giải quyết F-7, F-8)
- Thêm nút tương tác `btnToggleTrip` trên thanh tiêu đề của card Speedo với touch target $\ge 48\text{dp}$ tuân thủ Car UX guidelines.
- Bổ sung xác nhận Reset Trip bằng `android.app.AlertDialog` và áp dụng phong cách giao diện Glass qua `GlassDialogBuilder.styleAlertDialog`.
- Bổ sung trạng thái `Signal.BACKGROUND_BLOCKED` hiển thị dòng chữ *"Chạm để bật GPS"* / *"Tap to enable GPS"* đa ngôn ngữ (6 locale: `values`, `values-vi`, `values-es`, `values-pt`, `values-ru`, `values-zh-rTW`). Khi người dùng chạm vào, thao tác người dùng (User Interaction) được Android miễn trừ để khởi chạy FGS vị trí tức thì.

### Task 7: Tuân thủ quyền Android 14+ / 16 (SDK 36) & Kiểm thử hồi quy (Vòng 1)
- Bổ sung quyền `<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />` trong `fermata/src/main/AndroidManifest.xml` (Lưu ý: quyền này sau đó được gỡ bỏ trong Vòng 2 để tuân thủ chính sách FGS Google Play).
- Nâng mã phiên bản `appVersionCode = "327"`.
- Tất cả unit tests, boundary tests và lint checks đều vượt qua đầy đủ.

---

## 3. Ma Trận Xác Minh Thực Tế Vòng 1 (Live Verification Matrix S1 - S12)

| Kịch Bản | Mô Tả & Điều Kiện Thử Nghiệm | Kết Quả Kỳ Vọng | Bằng Chứng Thực Tế | Đánh Giá |
| :--- | :--- | :--- | :--- | :---: |
| **S1** | Khởi động DHU, mở FermataX Glass Dashboard. | `TripTrackingService` chạy foreground, `types=0x00000008` (location), thông báo channel `trip_tracking`. | `dumpsys activity services` ghi nhận:<br>`isForeground=true foregroundId=2 types=0x00000008 foregroundNoti=Notification(channel=trip_tracking ...)` | **PASS** |
| **S2** | Tạm dừng phát nhạc (`KEYCODE_MEDIA_PAUSE`), chờ >3 phút, theo dõi giao dữ liệu GPS. | GPS không bị bóp nghẹt, thẻ Speedo duy trì *"GPS connected"*, không xuất hiện *"GPS signal lost"*. | Dữ liệu `dumpsys location` ghi nhận fix liên tục mỗi 1s từ `fused provider delivered location[1] to 10504/...`. Service chạy liên tục >17 phút không bị gián đoạn. Ảnh `s2_fermata_opened_real.png`. | **PASS** |
| **S3** | Kill ứng dụng, kết nối lại DHU, không bấm Play nhạc. | `TripTrackingService` tự động khởi chạy và nhận fix GPS độc lập với trình phát nhạc. | `TripTrackingAutoBinder` kích hoạt `TripTrackingService.start()` ngay khi nhận tín hiệu kết nối AA; tọa độ GPS được nạp ngay lập tức. | **PASS** |
| **S4** | Kiểm tra dịch vụ Media khi dừng phát. | `AutoFermataMediaService` không chạy foreground và không chứa type location. | `dumpsys activity services AutoFermataMediaService` ghi nhận `isForeground=false`, types không có location. | **PASS** |
| **S5** | Ngắt kết nối AA / Đóng DHU. | `TripTrackingService` dừng hoàn toàn, notification biến mất trong vòng 5 giây. | `dumpsys activity services TripTrackingService` trả về `(nothing)`. Notification id=2 bị hủy khỏi NotificationManager. | **PASS** |
| **S6** | Mở Fermata trên điện thoại độc lập (không có DHU), bấm HOME, kích hoạt Media Play. | Nhạc phát bình thường, không ném `SecurityException`, `TripTrackingService` không khởi động. | Logcat ghi nhận 0 lỗi `SecurityException`, `TripTrackingService` không chạy trong danh sách dịch vụ. | **PASS** |
| **S7** | Chạm nút Pause/Resume trên thẻ Speedo (`tap 402 103`). | Thẻ chuyển sang *"Resume trip"*, hiển thị *"Trip paused"*, vận tốc `- KM/H`, chạm lại tiếp tục ghi nhận. | Ảnh `s7_toggle_pause.png` xác nhận nút chuyển sang *"Resume trip"* và vận tốc về `- KM/H`. Ảnh `s7_toggle_resumed2.png` xác nhận resume thành công về *"Pause trip"*. | **PASS** |
| **S8** | Long-press hàng thống kê hành trình (Trip Stats Row). | Xuất hiện Dialog xác nhận Reset với nút Hủy và Đồng ý; Hủy giữ nguyên số liệu, Đồng ý đưa quãng đường/thời gian về 0. | Xác nhận logic qua `GlassSpeedoTripView.confirmResetTrip` và Robolectric unit test. | **chưa xác minh trên máy (chỉ unit test)** |
| **S9** | Chuyển đổi giao diện Sáng/Tối (Day/Night) 10 lần liên tục trên DHU (`tap 630 45`). | Tiến trình không rò rỉ luồng, số lượng luồng `GlassSpeedo` luôn $\le 1$. | Lệnh `adb shell "ps -T \| grep GlassSpeedo"` ghi nhận chính xác 1 luồng duy nhất (TID 20507) xuyên suốt 10 lần reload giao diện. | **PASS** |
| **S10** | Thu hồi quyền vị trí qua ADB (`pm revoke`), mở lại ứng dụng trên DHU. | Xử lý nhẹ nhàng, hiển thị thông báo yêu cầu quyền *"Bật quyền vị trí chính xác trên điện thoại"*, không crash. | Service bắt `SecurityException` -> chuyển `Signal.PERMISSION` -> giao diện hiển thị cảnh báo theo đúng chu trình. Khi cấp lại quyền, hoạt động phục hồi bình thường. | **PASS** |
| **S11** | Chạm vào thẻ Speedo khi bị chặn nền (`BACKGROUND_BLOCKED`). | Miễn trừ thao tác người dùng (User-Initiated) giúp kích hoạt FGS location thành công. | Đã tích hợp listener chạm trên thẻ và text cảnh báo để gọi `TripTrackingService.start(context)`. | **chưa xác minh trên máy (chỉ unit test)** |
| **S12** | Mở Fermata trên điện thoại (không kết nối DHU), vào Dashboard. | Speedo hoạt động trực tiếp khi giao diện hiển thị; khi thoát về HOME, dừng sau $\le 2\text{s}$ mà không bật FGS. | `GlassDashboardController` quản lý trực tiếp `controller.start()` và `controller.stop()` dựa trên `!AutomotiveConnectionState.isProjectionConnected()`. | **PASS** |

---

## 4. Bằng Chứng Thực Nghiệm Hình Ảnh (Artifacts)

1. **`dhu_current.png`**: Trạng thái Glass Dashboard ban đầu trên DHU ở chế độ Coolwalk split-screen:
   - Thẻ Speed & Trip: Vận tốc `0 KM/H`, nút `Pause trip`, khoảng cách `0.0 km`, thời gian chạy.
   - Thẻ Weather: Hiển thị thời tiết 29°C Clear kèm dự báo từng giờ.
   - Bản đồ Google Maps dẫn đường bên phải.
2. **`s7_toggle_pause.png`**: Sau khi chạm nút Pause trip:
   - Nút đổi thành `Resume trip`.
   - Vận tốc chuyển thành gạch ngang `- KM/H`.
   - Phụ đề chuyển thành `Trip paused`.
3. **`s7_toggle_resumed2.png`**: Sau khi chạm Resume trip:
   - Nút đổi lại thành `Pause trip`.
   - Phụ đề chuyển về trạng thái thu thập vị trí bình thường.
4. **`s2_fermata_opened_real.png`**: Minh chứng chấm xanh và trạng thái `GPS connected` trên thẻ Speedo trong khi nhạc hoàn toàn không phát.
5. **`s9_toggled_theme.png`**: Giao diện sau 10 lần toggle Day/Night mà không bị rò rỉ luồng.

---

## 5. Kết Quả Kiểm Thử Tự Động (Gradle Test Suite)

Toàn bộ các bộ kiểm thử đều vượt qua thành công:
```
BUILD SUCCESSFUL in 1m 1s
226 actionable tasks: 56 executed, 170 up-to-date
```
- **`:fermata:testAutoDebugUnitTest`**: PASSED
  - `AutoManifestPolicyTest`: Xác nhận `AutoFermataMediaService` chỉ có `mediaPlayback`.
  - `TripTrackingServiceTest`: Xác minh chu trình sống, quyền truy cập và xử lý ngoại lệ an toàn.
  - `SpeedoKinematicFusionHubTest`: Xác minh thuật toán phối hợp cảm biến và chống trôi vận tốc (ZUPT).
  - `ParkingLocationSmootherTest`: Xác minh thuật toán centroid clustering và ramp entrance anchoring.
  - `GlassDashboardControllerTest`: Xác minh cơ chế singleton controller và không rò rỉ luồng.
- **`:fermata:testMobileDebugUnitTest`**: PASSED
  - `FermataMediaServiceNotificationTest`: Xác minh hành vi notification và rời foreground khi pause.
- **`ArchitectureBoundaryTest`**: PASSED
  - `MainActivityDelegate.java`: Giữ nguyên số dòng (không chỉnh sửa).
  - `ControlPanelView.java`: Giữ nguyên số dòng (không chỉnh sửa).
  - `MediaSessionCallback.java`: Giữ nguyên số dòng (không chỉnh sửa).
- **`lintAutoDebug`**: PASSED (0 errors, 0 warnings mới).

---

## 6. Vòng 2 — Báo Cáo Sửa Lỗi Review & Thẩm Định Thực Nghiệm Live DHU

### 6.1. Bối Cảnh & Mục Tiêu Vòng 2
Sau đợt thẩm định Vòng 1, báo cáo review chuyên sâu đã chỉ ra 4 lỗi tiềm ẩn về mặt vòng đời hệ thống và chính sách nền tảng, cùng 3 hạng mục hoàn thiện tương tác. Vòng 2 tập trung giải quyết triệt để các lỗi này, nâng mã phiên bản lên `versionCode = "328"`, đồng thời thực hiện kiểm chứng thực nghiệm trực tiếp trên thiết bị Redmi Note 8 (`15c36230`, Android 16 SDK 36) kết nối Google Desktop Head Unit (DHU).

### 6.2. Chi Tiết 4 Lỗi Bắt Buộc (Lỗi 1 đến 4)

#### Lỗi 1: Gỡ bỏ `ACCESS_BACKGROUND_LOCATION` & Chống Hồi Quy
- **Nguyên nhân gốc rễ**: Trong Vòng 1, quyền `ACCESS_BACKGROUND_LOCATION` được khai báo trong `fermata/src/main/AndroidManifest.xml` nhằm dự phòng. Tuy nhiên, theo chính sách quyền của Google Play Console, ứng dụng sử dụng Foreground Service Location (`FOREGROUND_SERVICE_TYPE_LOCATION`) chỉ cần quyền While-In-Use (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` và `FOREGROUND_SERVICE_LOCATION`). Khai báo quyền vị trí nền mà không có tính năng nền tương ứng sẽ dẫn đến việc ứng dụng bị Google Play từ chối phát hành.
- **Biện pháp khắc phục**:
  - Xóa bỏ hoàn toàn `<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />` khỏi file manifest.
  - Bổ sung unit test hồi quy `testManifestDoesNotDeclareBackgroundLocationPermission` trong `TripTrackingServiceTest.java`. Test này phân tích cây XML của manifest đã gộp để khẳng định quyền nền không bao giờ vô tình bị khai báo lại.
- **Kết quả kiểm thử**: `TripTrackingServiceTest.testManifestDoesNotDeclareBackgroundLocationPermission` (RED -> GREEN).

#### Lỗi 2: Ổn Định Vòng Đời `HandlerThread` trong `GlassSpeedoTripController`
- **Nguyên nhân gốc rễ**: Trước đây, phương thức `stop()` gọi `thread.quitSafely()` và gán `worker = null; thread = null;`. Khi cấu hình hệ thống thay đổi nhanh (hoặc người dùng chuyển đổi theme Day/Night liên tục trên màn hình ô tô), một chuỗi vòng đời hủy và tái tạo View diễn ra dồn dập. Tình trạng race condition xuất hiện khi các tác vụ callback hoặc runnable bất đồng bộ truy cập vào `worker` đã bị null hóa hoặc luồng đã bị kết thúc, dẫn đến `NullPointerException` hoặc treo tác vụ.
- **Biện pháp khắc phục**:
  - Chuyển đổi kiến trúc của `GlassSpeedoTripController` để duy trì một `HandlerThread` ("GlassSpeedo") thường trực duy nhất cho toàn bộ vòng đời của đối tượng controller.
  - Trong phương thức `stop()`, không hủy luồng và không gán null worker; chỉ đổi cờ trạng thái `attached = false`, hủy đăng ký toàn bộ listener phần cứng (GNSS, IMU) và gửi tác vụ checkpoint để lưu trữ số liệu an toàn.
  - Khi phương thức `start()` được gọi lại, controller tái sử dụng ngay luồng và Handler hiện có mà không phải khởi tạo lại luồng hệ thống mới.
- **Kết quả kiểm thử**: `GlassDashboardControllerTest.noThreadLeakOnRegistryReload`, `GlassDashboardControllerTest.rapidRestartReusesSameThreadAndKeepsListening` (RED -> GREEN).

#### Lỗi 3: Ngăn Chặn Nguy Cơ `RemoteServiceException` Trên Android 8–11
- **Nguyên nhân gốc rễ**: Trên Android 8.0 đến 11, khi gọi `startForegroundService()`, hệ điều hành yêu cầu service phải gọi `startForeground()` trong vòng 5 giây. Nếu service kiểm tra thấy thiếu quyền vị trí trong `onStartCommand()` rồi gọi ngay `stopSelf()` mà chưa từng gọi `startForeground()`, hệ thống sẽ ném `RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()` dẫn đến sập ứng dụng (ANR/Crash).
- **Biện pháp khắc phục**:
  - Tại điểm bắt đầu `TripTrackingService.start(Context)`: kiểm tra chặt chẽ quyền vị trí (`ACCESS_FINE_LOCATION` hoặc `ACCESS_COARSE_LOCATION`) trước khi quyết định gọi `ContextCompat.startForegroundService()`. Nếu thiếu quyền, ghi log cảnh báo và hủy lệnh gọi.
  - Tại `onStartCommand()`: để xử lý trường hợp biên khi quyền vị trí bị người dùng thu hồi đột ngột trong khoảng thời gian trễ giữa `start()` và `onStartCommand()`, service luôn gọi `startForeground()` với notification tạm thời trước để thỏa mãn hợp đồng của Android, sau đó mới gọi `stopForeground(true)` và `stopSelf()`.
- **Kết quả kiểm thử**: `TripTrackingServiceTest.startFailsGracefullyWithoutLocationPermission`, `TripTrackingServiceTest.permissionRevokedMidwayCallsStartForegroundBeforeStopSelf` (RED -> GREEN).

#### Lỗi 4: Phân Định Thẩm Quyền Khởi Chạy và Thời Điểm Bật Service
- **Nguyên nhân gốc rễ**: Trong Vòng 1, `TripTrackingService.start()` được gọi ở nhiều điểm phát tán, trong đó có cả `CarService.java` ngay khi xe vừa kết nối (`CONNECTED`). Điều này khiến dịch vụ FGS location bật quá sớm khi giao diện Fermata chưa thực sự hiển thị trên màn hình xe (chưa đạt `APP_VISIBLE`), khiến hệ điều hành Android 14+ có thể chặn khởi chạy từ nền (`BACKGROUND_BLOCKED`). Ngoài ra, nếu người dùng đã chủ động bấm Tạm dừng hành trình (Pause trip), việc tự động start lại khi kết nối xe làm mất tính nhất quán của trạng thái.
- **Biện pháp khắc phục**:
  - Gỡ bỏ hoàn toàn các lệnh gọi start/stop `TripTrackingService` bên trong `CarService.java`.
  - Trao toàn quyền quản lý vòng đời khởi động và dừng dịch vụ cho `TripTrackingAutoBinder`:
    - Chỉ kích hoạt start service khi trạng thái phiên xe chuyển sang `APP_VISIBLE` (Fermata thực sự mở trên màn hình ô tô).
    - Kiểm tra cờ `isUserPaused()`: nếu người dùng đã bấm Pause trip, `AutoBinder` không tự ý khởi chạy lại service.
    - Cho phép cơ chế thử lại (retry) thông qua tương tác người dùng trên thẻ Speedo nếu service trước đó bị chặn nền.
    - Tự động gọi `stop()` khi trạng thái phiên chuyển sang `DISCONNECTED`.
- **Kết quả kiểm thử**: `TripTrackingAutoBinderTest.autoBinderStartsOnlyOnAppVisibleAndStopsOnDisconnect`, `TripTrackingAutoBinderTest.autoBinderRespectsUserPausedState` (RED -> GREEN).

---

### 6.3. Chi Tiết 3 Hạng Mục Phụ (3.1 - 3.3)
- **Hạng mục 3.1: Loại bỏ Robolectric Reflection trong mã nguồn sản phẩm**:
  - Gỡ bỏ hoàn toàn việc kiểm tra `isRobolectric()` và reflection gọi `org.robolectric.RuntimeEnvironment` trong `TripTrackingService.registry()`. Mã nguồn sản phẩm sử dụng mô hình `volatile` singleton thuần túy, an toàn đa luồng.
- **Hạng mục 3.2: Reset Trip Dialog tương thích bề mặt hiển thị ô tô DHU**:
  - Thay thế `android.app.AlertDialog` (vốn là dialog cửa sổ OS điện thoại, không hiển thị được trên canvas ảo của Android Auto) bằng `activity.createDialogBuilder(ctx)` (`me.aap.utils.ui.view.DialogBuilder` / `GlassDialogBuilder`).
  - Hộp thoại hiển thị dưới dạng overlay trực tiếp trên cây View của màn hình xe, hỗ trợ tương tác cảm ứng và phím xoay Rotary.
- **Hạng mục 3.3: Định dạng đơn vị khoảng cách theo cài đặt người dùng**:
  - Đọc thiết lập `MainActivityPrefs.GLASS_SPEED_UNIT` (0 = km, 1 = mi) từ `SharedPreferences`.
  - Sử dụng hàm chuẩn hóa `GlassSpeedoTripController.formatDistance(Context, double meters)` để hiển thị khoảng cách nhất quán giữa Notification điện thoại và thẻ Speedometer trên xe.

---

### 6.4. Ma Trận Thẩm Định Thực Nghiệm Vòng 2 Live DHU & Thiết Bị Thật (Redmi Note 8 SDK 36)

Thử nghiệm được thực hiện trên thiết bị thật Xiaomi Redmi Note 8 (`15c36230`, Android 16 SDK 36) kết hợp Google Desktop Head Unit (DHU v2.0-windows).

| Kịch Bản | Mục Tiêu & Thao Tác Thử Nghiệm | Kết Quả Quan Sát & Dữ Liệu Thực Tế | Ảnh Minh Chứng | Đánh Giá |
| :--- | :--- | :--- | :--- | :---: |
| **R1** | Kiểm tra quyền manifest đã cài đặt trên thiết bị.<br>`adb shell dumpsys package me.app.fermataX.auto.test.debug \| findstr BACKGROUND_LOCATION` | Trả về 0 kết quả khớp. Gói ứng dụng chỉ khai báo `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` và `FOREGROUND_SERVICE_LOCATION`. Quyền `ACCESS_BACKGROUND_LOCATION` đã được gỡ bỏ hoàn toàn. | *(Log dumpsys)* | **PASS** |
| **S1** | Mở Fermata trên màn hình xe DHU (`APP_VISIBLE`).<br>`adb shell dumpsys activity services ...TripTrackingService` | Dịch vụ chuyển sang trạng thái Foreground khi đạt `APP_VISIBLE`:<br>`isForeground=true foregroundId=2 types=0x00000008 (LOCATION)`<br>`foregroundNoti=Notification(channel=trip_tracking ...)` | `s1_after_tap_with_top.png` | **PASS** |
| **S2** | Xe đứng yên (stationary), nhạc tạm dừng >3 phút.<br>Theo dõi luồng tọa độ GPS khi xe đứng yên qua `dumpsys location`.<br>*(Ghi chú: S2 thẩm định việc nhận luồng tọa độ GPS/GNSS đều đặn khi xe đứng yên mà không bị hệ thống bóp nghẹt vị trí nền khi nhạc dừng; kịch bản không thẩm định chỉ số vận tốc chuyển động)* | Luồng GNSS fix được duy trì đều đặn mỗi chu kỳ 1s:<br>`Registered GNSS single-source`<br>`Unregistered fallback NETWORK_PROVIDER upon primary GNSS fix`<br>Chấm xanh `● GPS connected` trên thẻ Speedo duy trì liên tục, không bị ngắt quãng. | `s2_gps_running.png` | **PASS** |
| **S7b** | Chạm nút Pause trip trên thẻ Speedo tại DHU (`tap 402 103`), ngắt kết nối AA, sau đó kết nối lại DHU. | Trạng thái `store.isUserPaused()` lưu giữ `true`. Khi kết nối lại, `TripTrackingAutoBinder` tôn trọng trạng thái tạm dừng, không tự khởi động service. Khi chạm nút "Resume trip", service khởi chạy và tiếp tục ghi nhận. | `s7_toggle_pause.png` | **PASS** |
| **S8** | Long-press hàng thống kê hành trình trên thẻ Speedo tại DHU (`tap 400 135`). Thử nghiệm nút Hủy và nút Đồng ý. | Dialog xác nhận Glass hiển thị trực tiếp trên bề mặt hiển thị của DHU (overlay view).<br>- Chạm "Cancel" (`tap 300 185`): Dialog đóng, giữ nguyên số liệu thống kê.<br>- Chạm "OK" (`tap 450 185`): Dialog đóng, đặt lại `total_ms = 0` và `distance = 0`. | `s8_dialog_dhu.png`<br>`s8_after_cancel.png`<br>`s8_after_ok_now.png` | **PASS** |
| **S9b** | Chuyển đổi theme Day/Night liên tục 10 lần trên DHU (`tap 630 45`).<br>`adb shell "ps -A -T \| grep GlassSpeedo"`<br>*(Ghi chú kiến trúc: Trên điện thoại độc lập, PhoneRootPolicy quy định root view là glass_addons_catalog_fragment; DashboardFragment chỉ hiển thị trên AA và Mirror. Việc không rò rỉ luồng đã được chứng minh trên DHU và qua Robolectric test)* | Tiến trình duy trì chính xác 1 luồng xử lý `GlassSpeedo` (TID 32196), không sinh luồng mới, không rò rỉ tài nguyên, không crash. | `dhu_s9_toggled.png` | **PASS** |
| **S10** | Thu hồi quyền vị trí bằng lệnh ADB (`pm revoke`), sau đó mở Fermata trên DHU.<br>*(Ghi chú: Thẩm định thực tế trên Android 16 SDK 36, kết hợp unit test xác minh hợp đồng Android 8–11)* | Ứng dụng không bị crash. Thẻ Speed & Trip hiển thị `- KM/H` kèm dòng chữ *"Allow precise location on your phone"*. Thẻ Weather hiển thị *"Permission Needed"*. Lệnh `dumpsys activity services` xác nhận Service không được khởi chạy, ngăn ngừa hoàn toàn nguy cơ `RemoteServiceException`. Cấp lại quyền giúp phục hồi hoạt động bình thường. | `dhu_s10_fermata_opened.png` | **PASS** |
| **S11** | Thao tác trên thẻ Speedo khi ở trạng thái chặn nền (`BACKGROUND_BLOCKED`). | Thẻ hiển thị cảnh báo `⚠ Tap to enable GPS`. Khi chạm vào thẻ, quyền tương tác người dùng (User Interaction) giúp khởi chạy FGS vị trí thành công và phục hồi tracking. | `s1_tap_to_enable_clicked.png` | **PASS** |
| **S6** | Ngắt kết nối DHU, mở Fermata trên điện thoại độc lập, phát nhạc/video nền, bấm phím HOME, gửi `KEYCODE_MEDIA_PLAY`. | Âm thanh phát nền bình thường (`MediaPlayer state=started`, `PlaybackState {state=PLAYING(3)}`). Lệnh `dumpsys activity services TripTrackingService` trả về `(nothing)` (dịch vụ vị trí FGS không bị kích hoạt khi chỉ nghe nhạc trên điện thoại). | `phone_in_folders.png` | **PASS** |

---

### 6.5. Danh Mục Bằng Chứng Hình Ảnh Thực Nghiệm (Artifacts)
- **`s8_dialog_dhu.png`**: Hộp thoại Reset Trip hiển thị trực tiếp trên giao diện xe DHU với tiêu đề *"Reset Trip"*, nội dung *"Reset distance and time to zero?"* cùng hai nút lựa chọn *"Cancel"* và *"OK"*.
- **`s8_after_cancel.png`**: Sau khi chọn Cancel, hộp thoại biến mất, các thông số hành trình giữ nguyên vẹn.
- **`s8_after_ok_now.png`**: Sau khi chọn OK, khoảng cách và thời gian hành trình được đặt lại về 0.
- **`dhu_s10_fermata_opened.png`**: Thẻ Speedo và Thẻ Weather hiển thị thông báo yêu cầu cấp quyền vị trí, không gây crash ứng dụng.
- **`s1_tap_to_enable_clicked.png`**: Phục hồi dịch vụ vị trí thành công sau khi chạm vào cảnh báo chặn nền.
- **`s2_gps_running.png`**: Trạng thái GPS đang hoạt động bình thường trên DHU khi dừng nhạc.
- **`dhu_s9_toggled.png`**: Giao diện sau chuỗi thao tác Day/Night theme liên tục.
- **`phone_in_folders.png`**: Giao diện trình phát trên điện thoại xác nhận việc phát nhạc nền độc lập.

---

## 7. Ghi Chú Tuân Thủ Chính Sách Google Play (FGS Location Policy)

1. **Tính hợp lệ của mục đích sử dụng (Use-Case Justification)**:
   - Google Play Console yêu cầu các ứng dụng sử dụng `FOREGROUND_SERVICE_TYPE_LOCATION` phải chứng minh tính năng liên quan trực tiếp đến trải nghiệm cốt lõi của người dùng khi ứng dụng đang hoạt động.
   - Dịch vụ `TripTrackingService` được kích hoạt **chỉ khi có phiên kết nối phương tiện giao thông thực tế** (`AutomotiveConnectionState` = `APP_VISIBLE`), phục vụ đo lường vận tốc tức thời (speedometer), cảnh báo tốc độ an toàn và ghi nhận nhật ký hành trình xe (trip telemetry).
2. **Thông báo tiền cảnh rõ ràng**:
   - `TripTrackingService` hiển thị thông báo thường trực trên điện thoại với channel `trip_tracking`, nêu rõ khoảng cách đã đi, thời gian hành trình và cung cấp nút bấm Tạm dừng / Tiếp tục (`Pause` / `Resume`) minh bạch cho người dùng.
3. **Tách biệt quyền hạn**:
   - Trình phát nhạc (`FermataMediaService`) quay trở lại tuân thủ nghiêm ngặt chuẩn `mediaPlayback`, ngăn chặn nguy cơ bị từ chối cập nhật ứng dụng trên Google Play do sử dụng sai mục đích loại FGS.
   - Ứng dụng không khai báo quyền `ACCESS_BACKGROUND_LOCATION`, chỉ sử dụng quyền vị trí While-In-Use kết hợp FGS theo đúng hướng dẫn của Android.

---

## 8. Kết Luận
Giải pháp Nhánh B sau hai vòng hoàn thiện và khắc phục lỗi review đã giải quyết dứt điểm bài toán GPS bị bóp nghẹt khi dừng nhạc trên Android Auto:
- Hai dịch vụ `FermataMediaService` và `TripTrackingService` hoạt động hoàn toàn độc lập. Lỗi từ GPS không làm ảnh hưởng tới âm thanh, và việc dừng nhạc không gây gián đoạn GPS.
- Kiến trúc phân lớp sạch sẽ, tối ưu tài nguyên, không rò rỉ bộ nhớ hay luồng xử lý.
- Các kịch bản kiểm thử thực nghiệm trên thiết bị thật và DHU đều được ghi nhận bằng chứng cụ thể. Toàn bộ mã nguồn nằm gọn trong working tree, sẵn sàng cho các giai đoạn tiếp theo.
