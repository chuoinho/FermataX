# Prompt triển khai: TripTrackingService (hướng B) + kiểm thử trên DHU

> Dán toàn bộ file này cho agent thực thi. Agent làm việc trong `E:\antigravity\fermata`, nhánh `feat/aa-glass-theme`.

---

## 0. Bối cảnh (đọc trước khi code)

**Triệu chứng gốc:** trên Android Auto, khi pause nhạc thì thẻ speedo đứng ở giá trị cũ, kèm "GPS signal lost" và "Missing data".

**Nguyên nhân gốc:** UI trên xe là `MainCarActivity extends com.google.android.apps.auto.sdk.CarActivity`, chạy bên trong `CarService`, không phải một Activity thật. Với Android, app không có cửa sổ nào ở tiền cảnh. Khi đang phát nhạc, `FermataMediaService` chạy foreground nên GPS được giao bình thường. Khi pause, service rời foreground, app bị coi là ở nền và Android bóp vị trí xuống vài lần mỗi giờ.

**Bản vá tạm (đang có trong working tree, chưa commit) sai về thiết kế:**
- `FermataMediaService.promote()` thêm `FOREGROUND_SERVICE_TYPE_LOCATION` cho mọi lần phát, kể cả trên điện thoại không có AA.
- Nếu Android từ chối loại `location`, `startForeground` ném `SecurityException` và **lượt phát nhạc thất bại**.
- `keepForeground()` không làm gì nếu service đã foreground chỉ với loại `mediaPlayback`.
- Speedo vẫn phụ thuộc vào nhạc: chưa từng bấm play thì không có foreground service.

**Hướng B (việc cần làm):** tách GPS và speedo ra một foreground service riêng, `TripTrackingService` (loại `location`), sống theo phiên AA, độc lập với nhạc. Thiết kế mục tiêu đã có trong `fermata/TRIP_TRACKING.md`: dashboard đăng ký nhận dữ liệu từ `TripTrackingService`, có thông báo kèm nút pause, "one provider owns the measurement stream".

Đọc trước:
- `fermata/TRIP_TRACKING.md`
- `audit_reports/feat-aa-glass-theme/2026-10-22-full-branch-audit.md` (các mục F-1 đến F-8)
- `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/*` (`GlassSpeedoTripController`, `TripEngine`, `TripStore`, `SpeedoKinematicFusionHub`, `GlassSpeedoTripView`)
- `fermata/src/main/java/me/aap/fermata/ui/glass/dashboard/GlassDashboardController.java`
- `fermata/src/main/java/me/aap/fermata/media/service/FermataMediaService.java`, `PlaybackForegroundCoordinator.java`
- `fermata/src/main/java/me/aap/fermata/auto/AutomotiveConnectionState.java` (`addListener`, `State.DISCONNECTED/CONNECTED/APP_VISIBLE`, `isProjectionConnected()`)
- `fermata/src/auto/java/me/app/fermatax/auto/CarService.java`, `CarSessionCoordinator.java`

---

## 1. Ràng buộc toàn cục

- **KHÔNG** commit, push, merge, rebase, stash hay reset. Chỉ sửa working tree.
- **KHÔNG** gỡ cài đặt app trên điện thoại `15c36230`. Luôn dùng `adb -s 15c36230 install -r -d ...`.
- **KHÔNG** sửa `MainActivityDelegate.java`, vì file này có trần dòng trong `ArchitectureBoundaryTest`.
- **KHÔNG** sửa `ControlPanelView.java`, `MediaSessionCallback.java` (cũng có trần dòng).
- Không đụng `settings.gradle`, `local.properties` hay cấu hình ký.
- Mọi lệnh Gradle cần cờ ký test:
  `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat <task> -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
- Package đã cài trên máy: `me.app.fermataX.auto.test`. Kiểm tra lại bằng `adb -s 15c36230 shell pm list packages | findstr fermata`.
- Làm theo TDD: viết test thất bại trước, rồi sửa cho pass.
- Giữ phong cách code xung quanh (tab/space, `me.aap.utils.log.Log` hoặc `android.util.Log` như file đang dùng).

---

## 2. Thiết kế đích

```
AutomotiveConnectionState ──(CONNECTED/APP_VISIBLE)──► TripTrackingService.start(ctx)
                          ──(DISCONNECTED)───────────► TripTrackingService.stop(ctx)

TripTrackingService (FGS type=location, notification "Đang ghi trip" + Pause/Resume)
   └── owns: GlassSpeedoTripController (1 instance / process, 1 HandlerThread)
            └── TripEngine + TripStore + FusionHub

GlassDashboardController ──subscribe/unsubscribe──► TripTrackingService.Registry (listener)
GlassSpeedoTripView: nút Pause/Resume ──► registry.toggleTrip(); long-press ──► dialog xác nhận reset

FermataMediaService: trở lại chỉ mediaPlayback; pause → leaveForeground(false) như cũ
```

Nguyên tắc:
1. **Lỗi GPS không được làm hỏng nhạc.** Hai service độc lập, không gọi chéo.
2. **Không có AA thì không chạy GPS nền.** Trên điện thoại, speedo chỉ chạy khi dashboard đang hiển thị: controller vẫn dùng được trực tiếp nhưng không bật FGS.
3. **Một controller cho mỗi process**, không tạo lại khi dashboard reload. Sửa F-5.
4. **Mỗi phép đo chỉ có một nguồn vị trí** (theo `TRIP_TRACKING.md`). Sửa F-4 cho phần speedo.

---

## 3. Các bước

### Task 1: Trả `FermataMediaService` về như trước bản vá tạm

**File:**
- `fermata/src/main/AndroidManifest.xml`
- `fermata/src/auto/AndroidManifest.xml`
- `FermataMediaService.java`
- `fermata/src/test/.../FermataMediaServiceNotificationTest.java`
- `fermata/src/testAuto/.../AutoManifestPolicyTest.java`

- [ ] Ở cả hai manifest, `FermataMediaService` và `AutoFermataMediaService` dùng `foregroundServiceType="mediaPlayback"`.
- [ ] Trong `promote()`, gọi `startForeground(NOTIF_ID, notif)` như cũ, bỏ `hasLocationPermission()`.
- [ ] Trong `updateNotification(STATE_PAUSED)`, bỏ nhánh `isAutomotiveSessionActive()` và luôn gọi `playbackForeground.leaveForeground(false)`.
- [ ] Xoá `isAutomotiveSessionActive()` và test `automotiveProjectionKeepsForegroundWhenPaused`.
- [ ] Trong `AutoManifestPolicyTest`, assert media service **không** chứa `location`.
- [ ] Giữ `<uses-permission FOREGROUND_SERVICE_LOCATION>` trong main manifest, vì Task 2 cần.

### Task 2: Tạo `TripTrackingService`

**File mới:**
- `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/TripTrackingService.java`
- test `TripTrackingServiceTest.java` (Robolectric)

**Manifest (main):**
```xml
<service
    android:name="me.aap.fermata.ui.glass.speedo.TripTrackingService"
    android:exported="false"
    android:foregroundServiceType="location" />
```

Yêu cầu:
- [ ] **API tĩnh:**
  - `start(Context)` và `stop(Context)`.
  - `registry()` trả singleton `TripTrackingRegistry`, gồm:
    - `addListener` / `removeListener` (giao trên main thread, gửi snapshot ngay khi đăng ký);
    - `getController()`, `toggleTrip()`, `resetTrip()`.
- [ ] **`onCreate()` / `onStartCommand()`:**
  1. Kiểm tra quyền `ACCESS_FINE_LOCATION` hoặc `ACCESS_COARSE_LOCATION`. Không có quyền thì `stopSelf()`, và registry báo `Signal.PERMISSION`.
  2. Gọi `ServiceCompat.startForeground(this, NOTIF_ID, notif, FOREGROUND_SERVICE_TYPE_LOCATION)` (với API ≥ 29; dưới 29 thì không truyền loại).
  3. Bọc lời gọi trên bằng `try/catch (SecurityException | IllegalStateException e)` (bao gồm `ForegroundServiceStartNotAllowedException`). Khi lỗi: log `FermataTripService`, `stopSelf()`, registry báo trạng thái mới `Signal.BACKGROUND_BLOCKED` (thêm vào `TripEngine.Signal`, có string i18n). **Không ném lỗi ra ngoài.**
  4. Gọi `controller.start()` (controller lấy từ registry; chỉ tạo một lần).
- [ ] **`onDestroy()`:** gọi `controller.stop()`, nhưng giữ controller trong registry để dashboard trên điện thoại vẫn dùng được.
- [ ] **Notification:**
  - Kênh riêng `trip_tracking`, mức `IMPORTANCE_LOW`, `setOngoing(true)`.
  - Tiêu đề `speedo_recording_title`, nội dung là quãng đường và thời gian hiện tại; cập nhật tối đa mỗi 10 giây.
  - Action Pause/Resume dùng `speedo_pause_trip` / `speedo_resume_trip`, gửi PendingIntent tới chính service với action `ACTION_TOGGLE`.
  - Content intent mở app.
  - Dùng icon có sẵn (`R.drawable.notification`).
- [ ] **Không kích hoạt lại sau khi bị kill:** `START_NOT_STICKY`, vì service sống theo phiên AA và không tự hồi sinh.

### Task 3: Bật/tắt theo phiên AA

**File:**
- `TripTrackingService.java` (hoặc một class nhỏ mới `TripTrackingAutoBinder.java`)
- Nơi khởi tạo app (`FermataApplication.onCreate` hoặc nơi đang khởi tạo `AutomotiveConnectionState`). Chọn chỗ ít đụng nhất, không phải `MainActivityDelegate`.

- [ ] Đăng ký `AutomotiveConnectionState.get().addListener(state -> ...)`:
  - `CONNECTED` hoặc `APP_VISIBLE`: nếu người dùng chưa pause (`TripStore.isUserPaused()`) thì gọi `TripTrackingService.start(app)`.
  - `DISCONNECTED`: gọi `TripTrackingService.stop(app)`.
- [ ] Gọi `start` khi đã có tín hiệu `APP_VISIBLE` (UI trên xe đang hiển thị). Đây là thời điểm Android dễ chấp nhận FGS nhất. Nếu bị từ chối (Task 2, bước 3), thử lại một lần khi `CarService.onBind` / `onRebind` hoặc khi người dùng chạm vào thẻ speedo. Một cú chạm là thao tác người dùng nên được miễn trừ.
- [ ] Viết test: giả lập chuỗi trạng thái DISCONNECTED → APP_VISIBLE → DISCONNECTED rồi kiểm tra `start` và `stop` được gọi đúng một lần mỗi chiều (dùng Robolectric `ShadowApplication.getNextStartedService()` hoặc tiêm một interface `ServiceStarter`).

### Task 4: Dashboard chỉ đăng ký nhận dữ liệu, không sở hữu controller (F-5)

**File:** `GlassDashboardController.java`, `DashboardFragment.java` (chỉ khi thật sự cần)

- [ ] Bỏ `new GlassSpeedoTripController(...)` trong dashboard. Lấy controller qua `TripTrackingService.registry()`.
- [ ] Trong `start()`, gọi `registry.addListener(listener)`:
  - Nếu **không** đang chiếu AA (điện thoại): gọi thêm `controller.start()` để speedo chạy khi dashboard đang hiển thị.
  - Nếu đang chiếu AA: service đã giữ controller, không cần làm gì thêm.
- [ ] Trong `stop()`, gọi `registry.removeListener(listener)`. Chỉ gọi `controller.stop()` khi **không** đang chiếu AA và service không chạy.
- [ ] Trong `GlassSpeedoTripController.stop()`, sau `checkpoint()` gọi `thread.quitSafely()`, rồi gán `thread = null; worker = null`. `ensureWorker()` sẽ tạo lại thread khi cần.
- [ ] Trong `GlassDashboardController.stop()`, gọi `weatherClient.shutdown()` thay cho `cancel()`. Trong `GlassOpenMeteoClient.fetchForecast`, bắt `RejectedExecutionException`.
- [ ] Viết test: gọi `reload()` 10 lần, đếm thread tên `GlassSpeedo` bằng `Thread.getAllStackTraces()`, kết quả phải ≤ 1.

### Task 5: Sửa logic đo của speedo (F-2, F-3, F-4)

**File:** `GlassSpeedoTripController.java`, `SpeedoKinematicFusionHub.java` và các test tương ứng

- [ ] **F-2: chỉ kiểm tra cảm biến một lần mỗi chu kỳ.**
  - Gọi `stationaryDetector.update()` **một lần** trong `heartbeat`.
  - Đổi chữ ký thành `drainToEngine(long now, boolean stationary)`. `handleLocation` và `drainRunnable` truyền `false`.
  - Bỏ `unregisterImu()` trong `drainToEngine`.
  - Test: IMU yên tĩnh qua 3 lần heartbeat liên tiếp thì `TripEngine.motion(..., stationary=true, ...)` được gọi, và `gapMeters` không tăng.
- [ ] **F-3: dùng thời điểm thật của từng vị trí.**
  - `Sample` lưu `fixTime = loc.getElapsedRealtimeNanos() / 1_000_000` (nếu bằng 0 thì dùng `receiveTime`).
  - `Fix.timeMs` bằng `fixTime` lớn nhất trong epoch. Bỏ mẫu cũ hơn 2 000 ms so với mẫu mới nhất.
  - Seed trong `startTrip()` chỉ được nạp nếu tuổi ≤ 2 000 ms.
  - Gọi `engine.start(nowTime)`, không dùng `min(now, seedTime)`.
  - Test: seed 30 giây tuổi không được làm anchor; fix thật đầu tiên được nhận ngay.
- [ ] **F-4: một nguồn vị trí cho mỗi phép đo.**
  - Trong `registerLocation()`: API ≥ 31 và có `FUSED_PROVIDER` thì chỉ dùng FUSED; ngược lại dùng GPS.
  - NETWORK chỉ đăng ký khi không có fix GPS/FUSED trong hơn 10 giây; gỡ lại khi GPS về.
  - **Bỏ PASSIVE.**
  - Test: không có fix nào bị nạp hai lần vào fusion hub.
- [ ] Không đụng CarFinder (`CarLocatorManager`, `ParkingLocationSmoother`). Phần đó làm ở một task khác.

### Task 6: Giao diện thẻ speedo (F-7, F-8)

**File:** `GlassSpeedoTripView.java`, `values*/strings.xml`, `values-*/speedo_strings.xml`

- [ ] Thêm nút Pause/Resume nhỏ trên thẻ, gọi `onToggleTripListener`:
  - nhãn theo `speedo_pause_trip` / `speedo_resume_trip`;
  - vùng chạm ≥ 48 dp trên xe;
  - có `contentDescription`.
- [ ] Đổi `confirmResetTrip()` để hiện dialog `GlassDialogBuilder` dùng `speedo_reset_trip_confirm`. Chỉ reset khi người dùng bấm OK.
- [ ] Hiển thị `Signal.BACKGROUND_BLOCKED` bằng string mới `speedo_tap_to_enable` ("Chạm để bật GPS"). Chạm vào thì gọi `TripTrackingService.start(ctx)`.
- [ ] Thêm string mới cho **cả 6 locale** (values, vi, es, pt, ru, zh-rTW).
- [ ] Sau khi xong, chạy lint và kiểm tra không còn `speedo_*` nào bị báo UnusedResources.

### Task 7: Kiểm thử tự động

- [ ] `:fermata:testAutoDebugUnitTest` và `:fermata:testMobileDebugUnitTest` pass 100%.
- [ ] `lintAutoDebug` có 0 error.
- [ ] `:fermata:testAutoDebugUnitTest --tests me.aap.fermata.architecture.ArchitectureBoundaryTest` pass.
- [ ] Bump `appVersionCode` trong `gradle/libs.versions.toml` lên 327.

---

## 4. Kiểm thử trên DHU với thiết bị `15c36230`

### 4.1 Chuẩn bị

```bat
adb devices
adb -s 15c36230 shell getprop ro.build.version.sdk
adb -s 15c36230 shell getprop ro.product.model
```

Ghi lại SDK. **Nếu SDK ≥ 34 thì bắt buộc chạy kịch bản S6.**

Build và cài:
```bat
cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:assembleAutoDebug -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"
adb -s 15c36230 install -r -d fermata\build\outputs\apk\auto\debug\fermata-auto-debug.apk
```

Lưu ý: nếu tên APK khác, tìm trong `fermata\build\outputs\apk\auto\debug\`.

Khởi động DHU:
1. Trên điện thoại, mở Android Auto → Developer settings → **Start head unit server**.
2. Chạy `run_dhu.bat`. Script này forward cổng 5277 rồi mở `desktop-head-unit.exe -c config\default_720p.ini`.
3. Trên DHU, mở FermataX và chọn theme Glass để thấy dashboard.

Mở logcat ở một cửa sổ riêng, lưu thành file làm bằng chứng:
```bat
adb -s 15c36230 logcat -c
adb -s 15c36230 logcat -v time FermataTrip:V FermataTripService:V ActivityManager:W AndroidRuntime:E *:S > dhu_trip_log.txt
```

Các lệnh dùng xuyên suốt (đặt `PKG=me.app.fermataX.auto.test`):
```bat
:: Service ghi trip có đang foreground, loại location (0x8) không
adb -s 15c36230 shell dumpsys activity services %PKG%/me.aap.fermata.ui.glass.speedo.TripTrackingService
:: Media service KHÔNG được mang loại location
adb -s 15c36230 shell dumpsys activity services %PKG%/me.aap.fermata.media.service.FermataMediaService
:: Yêu cầu vị trí đang mở của app
adb -s 15c36230 shell dumpsys location | findstr /i "%PKG%"
:: Đếm thread GlassSpeedo
for /f %p in ('adb -s 15c36230 shell pidof %PKG%') do adb -s 15c36230 shell ps -T -p %p | find /c "GlassSpeedo"
:: Điều khiển nhạc
adb -s 15c36230 shell input keyevent KEYCODE_MEDIA_PLAY
adb -s 15c36230 shell input keyevent KEYCODE_MEDIA_PAUSE
```

### 4.2 Kịch bản

Mỗi kịch bản: ghi lại lệnh, kết quả, ảnh chụp DHU (`adb -s 15c36230 exec-out screencap -p > sX.png` hoặc chụp cửa sổ DHU) và đoạn log liên quan.

| ID | Kịch bản | Bước | Kỳ vọng |
|---|---|---|---|
| S1 | Service bật theo phiên AA | Kết nối DHU, mở FermataX | `dumpsys` cho thấy TripTrackingService `isForeground=true`, `foregroundServiceType=0x00000008`; trên điện thoại có thông báo "Recording trip" |
| S2 | **Lỗi gốc**: pause nhạc thì speedo vẫn chạy | Phát nhạc → `KEYCODE_MEDIA_PAUSE` → chờ 3 phút (để điện thoại ở gần cửa sổ hoặc ngoài trời có GPS; mang ra xe nếu được) | Log `FermataTrip` vẫn có fix khoảng mỗi 1 giây; thẻ không chuyển sang "GPS signal lost"; `dumpsys location` vẫn có request của app; media service đã rời foreground |
| S3 | Chưa phát nhạc lần nào | Kill app (`adb shell am force-stop %PKG%`), kết nối lại DHU, **không** bấm play | Giống S2: speedo nhận fix, TripTrackingService foreground |
| S4 | Media service không mang `location` | Phát nhạc, chạy `dumpsys` cho FermataMediaService | `foregroundServiceType=0x00000002` (chỉ mediaPlayback) |
| S5 | Ngắt AA | Đóng DHU hoặc tắt head unit server | TripTrackingService dừng, thông báo biến mất, `dumpsys location` không còn request của app (sau ≤ 5 giây) |
| S6 | **Nhạc không bị ảnh hưởng khi app ở nền** (SDK ≥ 34) | Không chạy DHU. Mở app trên điện thoại → HOME → `KEYCODE_MEDIA_PLAY` (hoặc nút tai nghe Bluetooth) | Nhạc phát. Không có `SecurityException` hay `ForegroundServiceStartNotAllowedException` trong log. Không có TripTrackingService chạy |
| S7 | Pause/Resume trip | Bấm nút Pause trên thẻ, rồi bấm action trong thông báo | Thẻ hiện "Trip paused"; GPS bị gỡ (`dumpsys location`); trạng thái pause còn giữ sau khi ngắt rồi kết nối lại DHU; Resume ghi tiếp |
| S8 | Reset cần xác nhận | Chạm giữ dòng thống kê trip | Hiện dialog; Cancel thì không mất dữ liệu; OK thì về 0 |
| S9 | Không rò thread (F-5) | Đổi Day/Night 10 lần qua header, đếm thread | Số thread `GlassSpeedo` ≤ 1 |
| S10 | Thu hồi quyền vị trí | `adb shell pm revoke %PKG% android.permission.ACCESS_FINE_LOCATION` và `...ACCESS_COARSE_LOCATION` (app sẽ bị kill) → kết nối lại DHU | Không crash; thẻ hiện "Allow precise location on your phone"; không có FGS location. Sau đó `pm grant` lại cả hai quyền |
| S11 | Bị từ chối FGS | Nếu S1 thất bại vì Android từ chối: kiểm tra thẻ hiện "Chạm để bật GPS", chạm vào | Service bật thành công sau khi chạm; ghi rõ trường hợp này trong báo cáo |
| S12 | Dashboard trên điện thoại (không AA) | Ngắt DHU, mở dashboard Glass trên điện thoại | Speedo chạy khi đang hiển thị; HOME thì GPS gỡ trong ≤ 2 giây; không có FGS location |

Kiểm tra chuyển động: chỉ đo được bằng cách lái hoặc đi bộ thật với điện thoại. Không dùng mock location, trừ khi trên máy đã cài sẵn app mock và người dùng đồng ý. Khi đó bật `allow_mock` trong `TripStore` qua bản debug và ghi chú lại. Nếu không đo được chuyển động, ghi rõ trong báo cáo rằng S2 chỉ xác minh được luồng fix (log và dumpsys), chưa xác minh được số liệu tốc độ.

### 4.3 Báo cáo

Tạo `audit_reports/feat-aa-glass-theme/2026-10-22-trip-service-dhu-verification.md` gồm:
- Model, SDK và Android Auto version của thiết bị.
- Bảng S1–S12 với cột PASS / FAIL / KHÔNG CHẠY ĐƯỢC, kèm bằng chứng (đoạn log, output `dumpsys`, tên ảnh).
- Kết quả Gradle (số test, lint error).
- Danh sách file đã sửa hoặc thêm.
- Mọi sai lệch so với prompt này và lý do.
- Rủi ro còn lại, đặc biệt là Play Console: cần khai báo FGS `location` cho mục đích ghi trip/đồng hồ tốc độ do người dùng khởi động, có thông báo hiển thị.

Báo cáo trung thực: kịch bản nào không chạy được (ví dụ không có tín hiệu GPS trong nhà) thì ghi là "KHÔNG CHẠY ĐƯỢC" kèm lý do, không ghi PASS.

---

## 5. Định nghĩa hoàn thành

- [ ] `FermataMediaService` chỉ dùng `mediaPlayback`, không còn logic vị trí.
- [ ] `TripTrackingService` chạy foreground loại `location` trong suốt phiên AA, độc lập với trạng thái nhạc.
- [ ] Mỗi process chỉ có một `GlassSpeedoTripController` và một thread `GlassSpeedo`.
- [ ] F-2, F-3, F-4 (phần speedo), F-5, F-7, F-8 đã sửa và có test.
- [ ] Test tự động xanh, lint 0 error.
- [ ] S2, S4, S5, S6 PASS trên thiết bị. Kịch bản nào khác thất bại thì phải có giải thích.
- [ ] Chưa commit gì.
