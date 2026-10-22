# Prompt sửa lỗi: TripTrackingService (vòng 2)

> Dán toàn bộ file này cho agent đã làm `2026-10-22-trip-tracking-service.md`. Làm việc trong `E:\antigravity\fermata`, nhánh `feat/aa-glass-theme`, trên working tree hiện tại (chưa commit).

## 0. Bối cảnh

Bản triển khai hướng B đã được review lại. Kết quả: test pass (2 572 test module `fermata`, 0 fail), lint 0 error, và thiết kế đúng hướng. Nhưng còn **4 lỗi bắt buộc sửa**, cộng 3 việc nhỏ. Ngoài ra, báo cáo DHU ghi PASS cho một số kịch bản chưa thực sự chạy trên máy (xem mục 3).

## 1. Ràng buộc (giữ nguyên như vòng 1)

- **KHÔNG** commit, push, merge, rebase, stash hay reset.
- **KHÔNG** gỡ cài đặt app trên `15c36230`. Luôn dùng `adb -s 15c36230 install -r -d ...`.
- **KHÔNG** sửa `MainActivityDelegate.java`, `ControlPanelView.java`, `MediaSessionCallback.java`.
- **KHÔNG** sửa `FermataMediaService` hay manifest của media service. Phần đó đã đúng.
- Lệnh Gradle: `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat <task> -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
- Package đang cài trên máy: `me.app.fermataX.auto.test.debug`.
- Làm theo TDD: viết test tái hiện lỗi, xác nhận test **fail**, rồi mới sửa.

## 2. Các lỗi cần sửa

### Lỗi 1: Bỏ `ACCESS_BACKGROUND_LOCATION`

**File:** `fermata/src/main/AndroidManifest.xml`

Quyền này không có trong yêu cầu và không cần thiết. Trên máy `15c36230` quyền này **chưa được cấp** mà S1–S3 vẫn PASS. Khai báo nó buộc Google Play xét duyệt "background location", rất dễ bị từ chối.

- [ ] Xoá dòng `<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />`.
- [ ] Giữ `FOREGROUND_SERVICE_LOCATION`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.
- [ ] Tìm và xoá mọi chỗ code xin hoặc kiểm tra `ACCESS_BACKGROUND_LOCATION`: `grep -rn "BACKGROUND_LOCATION" fermata/src modules`.
- [ ] Thêm assert vào `AutoManifestPolicyTest` (hoặc một test manifest của main): manifest đã merge **không** chứa `ACCESS_BACKGROUND_LOCATION`.

### Lỗi 2: Race và NPE khi `GlassSpeedoTripController.stop()` gán null worker

**File:** `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java`

Hiện tại `stop()` gán `thread = null; worker = null` ngay trên thread gọi, rồi gửi `quitSafely()` vào thread cũ. Hai hậu quả:

1. **NPE và crash:** các lambda đã post trước đó trong `startTrip()` (`worker.removeCallbacks(heartbeat); worker.post(heartbeat)`), `pauseTrip()` và `resetTrip()` (`worker.removeCallbacks(drainRunnable)`) đọc field `worker` **lúc chạy**. Nếu `stop()` đã gán null, chúng ném `NullPointerException` trên thread `GlassSpeedo` và process crash.
2. **Speedo đứng:** gọi `stop()` rồi `start()` nhanh (reload dashboard trên điện thoại) tạo thread T2, trong khi tác vụ dọn dẹp của T1 vẫn chạy song song. Nếu T1 chạy *sau* `registerLocation()` của T2, nó đặt `running = false` và `removeUpdates` cho chính các listener T2 vừa đăng ký. Speedo đứng ở WAITING.

Controller giờ chỉ có **một instance cho cả process** (nằm trong `TripTrackingRegistry`), nên không còn lý do để huỷ thread.

- [ ] Tạo `HandlerThread` **một lần** (trong constructor hoặc `ensureWorker()` lần đầu) và không bao giờ `quit` nữa. Đánh dấu `thread` và `worker` là `final`, hoặc ít nhất không bao giờ gán lại.
- [ ] `stop()` chỉ đặt `attached = false` và post tác vụ dọn dẹp (`running=false`, gỡ callback, `unregisterLocation`, `unregisterImu`, `checkpoint`). Không `quitSafely()`, không gán null.
- [ ] Mọi thao tác trên state (`running`, `registered`, `engine`, `fusionHub`) phải chạy **trên worker thread**. Không đọc hoặc ghi chúng trực tiếp từ main thread.
- [ ] Bỏ các nhánh `if (worker == null)` / `|| worker == null` không còn cần thiết.
- [ ] **Test 1 (phải fail trước khi sửa):** gọi `start()`, `toggleTrip()` (pause), rồi `stop()` liền nhau trên main thread, sau đó chạy looper của worker (`shadowOf(looper).idle()`). Kỳ vọng: không có exception.
- [ ] **Test 2 (phải fail trước khi sửa):** gọi `start()`, `stop()`, rồi `start()` liền nhau, sau đó idle mọi looper. Kỳ vọng: `running == true`, listener GPS vẫn đăng ký (kiểm tra bằng `ShadowLocationManager.getRequestLocationUpdateListeners()` hoặc tương đương), và `Thread.getAllStackTraces()` chỉ có một thread tên `GlassSpeedo`.

### Lỗi 3: `startForegroundService()` rồi `stopSelf()` mà không gọi `startForeground()`

**File:** `TripTrackingService.java`

Trong `onStartCommand`, nếu thiếu quyền vị trí thì service gọi `stopSelf()` mà **không** gọi `startForeground()`. Trên Android 8–11 (minSdk là 28), cách gọi `startForegroundService()` như vậy gây `RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()` và **app crash**. Máy test chạy Android 16 nên S10 không thấy lỗi.

- [ ] Trong `TripTrackingService.start(Context)`: kiểm tra quyền FINE hoặc COARSE **trước** khi gọi `startForegroundService`. Thiếu quyền thì gọi `registry().reportSignal(Signal.PERMISSION)` rồi return, không khởi động service.
- [ ] Trong `onStartCommand`, với nhánh `ACTION_START` (hoặc action null): nếu vẫn phát hiện thiếu quyền (quyền bị thu hồi giữa chừng), **gọi `startForeground` trước** với notification hiện có, rồi mới `stopForeground` + `stopSelf`. Chỉ khi chính `startForeground` ném lỗi thì mới `stopSelf` luôn (hệ thống đã biết lần khởi động thất bại).
- [ ] Với `ACTION_TOGGLE` (từ PendingIntent của notification): khi service đang chạy foreground thì giữ như cũ. Nếu service chưa foreground, không được rơi vào nhánh `stopSelf()` mà không gọi `startForeground`. PendingIntent dùng `getService`, nên lệnh này đi qua `startService`, không phải `startForegroundService`. Kiểm tra lại cho chắc.
- [ ] **Test:** thu hồi quyền trong Robolectric (`shadowOf(app).denyPermissions(...)`), gọi `TripTrackingService.start(ctx)`. Kỳ vọng: `ShadowApplication.getNextStartedService()` trả `null`, và registry báo `Signal.PERMISSION`.

### Lỗi 4: Bật service sai thời điểm, không thử lại, và bỏ qua trạng thái pause

**File:** `TripTrackingAutoBinder.java`, `fermata/src/auto/java/me/app/fermatax/auto/CarService.java`, `TripTrackingService.java`

Hiện tại:
- `TripTrackingAutoBinder` gọi start ngay khi `CONNECTED`, trong khi yêu cầu là `APP_VISIBLE`, thời điểm Android dễ chấp nhận FGS nhất.
- `started = true` được đặt **trước** khi biết kết quả. Nếu bị chặn (`BACKGROUND_BLOCKED`), khi chuyển sang `APP_VISIBLE` không thử lại.
- `CarService.onCreate()` và `onBind()` gọi thẳng `TripTrackingService.start(this)`, bỏ qua `TripStore.isUserPaused()`. Kết quả là notification "Đang ghi trip" hiện dù người dùng đã pause.

Cần làm:
- [ ] **Xoá** hai lời gọi `TripTrackingService.start(this)` trong `CarService`. Chỉ `TripTrackingAutoBinder` được quyền bật hoặc tắt service theo phiên AA.
- [ ] Trong `TripTrackingAutoBinder.onStateChanged`:
  - `APP_VISIBLE`: nếu `!store.isUserPaused()` **và** service chưa chạy (`TripTrackingService.registry().isServiceRunning() == false`) thì gọi start. Không dùng cờ `started` cục bộ để chặn thử lại.
  - `CONNECTED`: **không** bật service.
  - `DISCONNECTED`: gọi stop nếu service đang chạy.
- [ ] Khi người dùng **resume** trip (từ nút trên thẻ hoặc notification) trong lúc AA đang chiếu mà service chưa chạy: gọi `TripTrackingService.start(ctx)`. Đây là thao tác của người dùng nên được miễn trừ.
- [ ] Khi người dùng **pause** trip trong lúc service đang chạy: giữ service và notification với nút "Resume trip" như hiện tại. Không tự stop service, để người dùng resume được từ notification.
- [ ] **Test:**
  - DISCONNECTED → CONNECTED: không có `start`.
  - → APP_VISIBLE: có `start` đúng 1 lần.
  - Giả lập start bị chặn (`isServiceRunning` vẫn `false`), APP_VISIBLE lần nữa: có `start` lần 2.
  - `isUserPaused = true`, APP_VISIBLE: không có `start`.
  - → DISCONNECTED: có `stop`.

## 3. Việc nhỏ (làm cùng lúc)

- [ ] **Bỏ code test trong production:** xoá đoạn reflection `org.robolectric.RuntimeEnvironment` trong `TripTrackingService.registry()`. Trong test, gọi `TripTrackingService.init(ApplicationProvider.getApplicationContext())` trong `@Before`, hoặc thêm một hàm `@VisibleForTesting static void resetForTests()`.
- [ ] **Dialog reset hiện được trên màn hình xe:** `GlassSpeedoTripView.confirmResetTrip` đang dùng `android.app.AlertDialog`. Chuyển sang cùng cơ chế dialog mà các màn hình Glass khác đang dùng trên xe: `activity.createDialogBuilder(ctx)` (`me.aap.utils.ui.view.DialogBuilder`), như `GlassDashboardController.onShortcutLongClicked`. Trong `CarActivity`, dialog Android thường có thể không hiển thị lên màn hình xe.
- [ ] **Notification theo đơn vị đo:** `TripTrackingService.buildNotification` đang luôn hiển thị km. Đọc `MainActivityPrefs.GLASS_SPEED_UNIT` (0 = km, 1 = mi) và dùng `GlassSpeedoTripController.formatDistance(...)`.
- [ ] (Tuỳ chọn) Dùng hoặc xoá 4 string còn bị lint báo UnusedResources: `speedo_speed_unknown`, `speedo_signal_lost`, `speedo_estimated`, `speedo_incomplete`. Nếu xoá, xoá ở **cả 6 locale**.

## 4. Kiểm thử

### 4.1 Tự động
- [ ] `:fermata:testAutoDebugUnitTest` và `:fermata:testMobileDebugUnitTest` pass 100%, gồm các test mới ở mục 2.
- [ ] `lintAutoDebug` có 0 error.
- [ ] `ArchitectureBoundaryTest` pass.
- [ ] Bump `appVersionCode` lên 328.

### 4.2 Trên máy `15c36230` và DHU

Chạy lại các kịch bản sau. Mỗi kịch bản cần **bằng chứng thật**: lệnh đã chạy, output `dumpsys` hoặc logcat, ảnh chụp.

| ID | Kịch bản | Kỳ vọng |
|---|---|---|
| R1 | `adb shell dumpsys package me.app.fermataX.auto.test.debug \| findstr BACKGROUND_LOCATION` | Không có kết quả (quyền không còn được khai báo) |
| S1 | Kết nối DHU, mở FermataX | TripTrackingService foreground `types=0x00000008`. Log cho thấy service được bật khi **APP_VISIBLE**, không phải CONNECTED |
| S2 | Pause nhạc 3 phút | Vẫn nhận fix khoảng mỗi 1 giây, thẻ không báo "GPS signal lost" |
| S7b | Pause trip trên thẻ → ngắt DHU → kết nối lại | Service **không** tự bật lại (vì đang pause). Bấm Resume trên thẻ thì service bật và GPS chạy |
| S8 | Chạm giữ dòng thống kê trip **trên DHU** | Dialog **hiện trên màn hình DHU**, có ảnh chụp. Cancel giữ nguyên số liệu, OK reset về 0 |
| S9b | Trên **điện thoại** (không DHU): vào dashboard Glass, đổi Day/Night 10 lần nhanh | Không crash. Speedo vẫn nhận fix sau lần đổi cuối (không đứng ở WAITING). Đúng 1 thread `GlassSpeedo` |
| S10 | `pm revoke` quyền FINE và COARSE → kết nối DHU | Không crash. Thẻ báo cần quyền. `dumpsys activity services` không có TripTrackingService |
| S11 | Gây trạng thái `BACKGROUND_BLOCKED` thật nếu được (ví dụ gọi start khi app không hiển thị trên xe). Nếu không gây được, ghi **KHÔNG CHẠY ĐƯỢC** | Chạm thẻ thì service bật |
| S6 | Không DHU: mở app → HOME → `KEYCODE_MEDIA_PLAY` | Nhạc **thật sự phát**. Chứng minh bằng `dumpsys media_session` có `state=PLAYING`, hoặc `dumpsys audio` cho thấy player đang active, không chỉ "log không có lỗi" |

Nếu có máy Android 8–11 hoặc emulator API 28–30, chạy thêm S10 trên đó để xác nhận Lỗi 3 đã hết. Nếu không có, ghi rõ "chưa xác minh trên API ≤ 30".

## 5. Báo cáo

Cập nhật `audit_reports/feat-aa-glass-theme/2026-10-22-trip-service-dhu-verification.md` bằng một mục mới **"Vòng 2 — sửa lỗi review"**:
- Mỗi lỗi 1–4: nguyên nhân, cách sửa, tên test tái hiện và trạng thái fail-trước / pass-sau.
- Bảng kịch bản ở mục 4.2 với PASS / FAIL / **KHÔNG CHẠY ĐƯỢC**.
- **Sửa lại** đánh giá S8 và S11 của vòng 1 thành "chưa xác minh trên máy (chỉ unit test)" nếu vòng 1 chưa chạy thật.
- Ghi rõ S2 chỉ xác minh luồng GPS khi máy đứng yên, chưa xác minh số liệu tốc độ.
- Không dùng các cụm "100%" hay "tuyệt đối" khi có kịch bản chưa chạy được.

## 6. Định nghĩa hoàn thành

- [ ] Manifest không còn `ACCESS_BACKGROUND_LOCATION`.
- [ ] Controller dùng một `HandlerThread` cố định, không còn NPE hay race khi stop rồi start nhanh, có test chứng minh.
- [ ] Không còn đường nào gọi `startForegroundService` mà không gọi `startForeground`.
- [ ] Service chỉ được bật bởi `TripTrackingAutoBinder` (lúc APP_VISIBLE, có thử lại, tôn trọng pause) hoặc bởi thao tác người dùng.
- [ ] Test tự động xanh, lint 0 error.
- [ ] Báo cáo trung thực, có bằng chứng cho từng kịch bản.
- [ ] Chưa commit gì.
