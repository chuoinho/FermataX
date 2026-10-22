# Báo cáo triển khai Speedo Refactor — 2026-10-22

Plan: `docs/superpowers/plans/2026-10-22-speedo-refactor.md`. Mã lỗi SP-x: `2026-10-22-speedo-algorithm-audit.md`.
Quyết định sản phẩm D1–D4 dùng mặc định trong plan (người dùng yêu cầu "triển khai theo plan"):
- D1: tự tạo trip mới sau 4 giờ suspend.
- D2: rời dashboard trên điện thoại thì suspend.
- D3: hiển thị "≈" khi phần ước tính trên 10%.
- D4: xoá biển báo tốc độ.

**Chưa commit gì.**

## 1. Cách làm
- **Task 1, 2, 4, 5, 6 (phần thuần Java) và Task 8 (bộ phát lại log):** viết và test trong một bản sao biệt lập ở scratchpad. Lúc đó agent vòng 2 vẫn đang sửa controller, view và service; làm riêng để không ghi đè lẫn nhau.
- **Tích hợp vào repo:** chỉ sau khi vòng 2 xong (báo cáo có mục "Vòng 2", không file nào thay đổi từ 21:03).
- **Line ending:** các file sửa bằng script được chuẩn hoá lại về LF.

## 2. Kiến trúc sau refactor

| Class | Dòng | Vai trò |
|---|---:|---|
| `TripEngine` (facade, API cũ giữ nguyên) | 215 | Điều phối; `State` immutable, thêm `speedEstimated`, `suspended`; thêm `suspend()`, `previousTrip()`, `restore(State, long)` |
| `Odometer` | 316 | Quãng đường: Doppler một hoặc hai đầu, cửa sổ vị trí 3–10 s, nối gap 3 fix có kẹp (giữ công thức cũ), độ bất định cộng theo RSS |
| `SpeedPresenter` | 73 | Tốc độ hiển thị (giữ ≤ 3 s, ước lượng "~" đến 8 s, sau đó "—"); max chỉ nhận giá trị 2 Doppler liên tiếp cùng xác nhận |
| `TripClock` | 72 | PAUSED / RUNNING / SUSPENDED; không cộng thời gian khi suspend hoặc khi process đã chết |
| `FixNormalizer` | 51 | Kiểm tra hợp lệ, ngưỡng tin Doppler `σv ≤ max(3, 0.5·v)`, quy tắc fix nào được làm mốc |
| `TripStore` | 77 | Bỏ cộng thời gian offline; lưu `suspended`, `suspended_at_wall`, trip trước (`prev_*`) |
| `TripLogRecorder` (mới) | 112 | Ghi log CSV chuyến đi vào `trip_logs/`, chỉ bật ở bản debug hoặc qua pref ẩn `log_trips` |
| `SpeedoKinematicFusionHub` | — | **Đã xoá** (cùng test của nó) |

**Controller (Task 7):**
- Một nguồn vị trí (FUSED nếu API ≥ 31, không thì GPS). **Bỏ NETWORK dự phòng.**
- `Location` đưa thẳng vào `FixNormalizer.of` rồi `engine.accept`, không còn lớp gộp.
- Cơ chế tự kiểm tra chỉ đăng ký lại khi chưa đăng ký được hoặc không có callback nào quá 60 s, nên không còn gỡ/đăng ký liên tục trong hầm.
- `stop()` gọi `engine.suspend()`. Ghi sự kiện start/stop/pause/reset vào log.

**View (Task 3 và 9):**
- `speed < 0` thì luôn hiển thị "—" (contentDescription `speedo_speed_unknown`).
- Đang ước lượng thì hiển thị "~N" với alpha 0.6.
- Hiển thị "≈" trước số km khi `isMostlyEstimated`.
- STALE hiển thị `speedo_signal_lost`.
- `speedo_estimated` và `speedo_incomplete` được dùng trong contentDescription.
- Nút Pause/Resume chỉ hiện "Resume" khi người dùng thật sự pause, không hiện khi hệ thống suspend.
- Số định dạng theo locale của tài nguyên.
- Xoá hẳn biển báo tốc độ.

## 3. Lệch so với plan

| Mục | Plan | Thực tế | Lý do |
|---|---|---|---|
| Thời gian hiển thị tốc độ ước lượng | 10 s | **8 s** (= `FRESH_MS`) | Khớp với ngưỡng mất tín hiệu sẵn có của engine và test `staleSamplesAndDuplicateCallbacksDoNotKeepSpeedAlive` |
| Fix không được làm mốc | Mọi fix acc > 20 m | Fix **không Doppler và** acc > 20 m | Doppler vẫn chính xác khi vị trí sai số lớn (phố nhiều nhà cao tầng, test `urbanCanyonHighAccuracyDopplerAccepted`). Vẫn sửa được SP-2 vì fix NETWORK không có Doppler |
| Trường `quality` trong `Fix` | Thêm enum | Không thêm; dùng `FixNormalizer.anchors()` | Giữ nguyên record `Fix` công khai |
| Kích thước | Facade ≤ 200, class ≤ 250 | 215 / 316 | `Odometer` chứa nguyên phần nối gap (giữ công thức cũ) |
| Thêm ngoài plan | — | Chống "neo bị gai": hai Doppler liên tiếp nhất quán với nhau nhưng mâu thuẫn với tốc độ ở mốc thì bỏ tốc độ của mốc | Không có cơ chế này thì fix đầu bị gai sẽ chặn mọi fix sau ~6 s và cộng quãng đường sai |
| Thêm ngoài plan | — | `segFromWindow`: sau khi cửa sổ vị trí đã chứng minh xe đang chạy, cần ≥ 5 s mới được kết luận dừng | Không có thì tốc độ nhấp nháy về 0 khi không có Doppler |

## 4. Test

### 4.1 Test đặc tả (`TripScenarioTest`, 20 test, đều pass)
steadyDopplerBaseline, interleavedMissingDoppler10pct/20pct, slowTrafficWithNoDopplerEveryOtherSecond, lowSpeedPoorSpeedAccuracy (3/5/10 m/s), positionsOnlyNoDoppler, positionsOnlyDriveNeverFlickersToZero, tunnelNoFix120s, tunnelWithCoarseFixes, stoppedInsideTunnel, suspendDoesNotCountTime, autoNewTripAfterLongSuspend, shortSuspendKeepsTheTrip, restoredSuspendedTripStartsNewTripAfterLongOfflineTime, unknownSpeedWhileTrackingIsNotZero, firstFixSpikeDoesNotSetMaxOrDistance, parkedJitterNoDistanceNoIncomplete, staleSpeedEstimatedThenHidden, firstGoodDopplerAfterTunnelShowsImmediately, pauseStillExcludesTimeAndIsShownAsPaused.

Không cần `@Ignore` theo từng giai đoạn như plan dự kiến, vì các phần được tích hợp cùng lúc.

### 4.2 Mô phỏng gốc chạy lại (trước → sau)

| Kịch bản | Trước | Sau |
|---|---|---|
| Cứ 10 fix có 1 fix thiếu Doppler | −20% | −0.3% |
| Cứ 5 fix có 1 fix thiếu Doppler | −40% | −0.3% |
| Kẹt xe 3 / 5 / 10 m/s, σv kém | −49 / −24 / −17% | −0.4 / −0.3 / +0.1% |
| Hầm có NETWORK dự phòng | −20% | −0.3% |
| Dừng 5 phút trong hầm | Hiện 54 km/h, km +4.5 | "—", km đứng yên; cuối +2.2% |
| Gai Doppler ở fix đầu | max 216 km/h | max 36 km/h |
| Đỗ xe, GPS rung | `incomplete` sai | Không bật |
| Ngắt AA 1 giờ (qua `suspend`) | 79 phút, TB 13.5 km/h | 20 phút, TB 54 km/h |

### 4.3 Test cũ bị đổi expectation (có ghi lý do ngay trong code)

| Test | Thay đổi | Lý do |
|---|---|---|
| `TripEngineTest.rejectsTeleportAndSingleSpeedSpike` | max ≤ 36 ngay sau fix đầu, = 36 sau 3 fix | Max cần 2 Doppler liên tiếp |
| `TripEngineTest.continuousMotionEstimatesDuringGapAndFeedsBridgeRecovery` | Tốc độ sau 30 s gap: 72 → −1; km hiển thị 0.68 → 0.08 | Không giữ tốc độ cũ; phần ước lượng trong hầm không hiển thị |
| `TripEngineTest.fallbackToSensorTransitionDoesNotCountSameIntervalTwice` | `incomplete` true → false, distance = 60 | Không còn đoạn nào bị mất |
| `GlassSpeedoTripControllerTest.checkpointRetainsEstimates…` (đổi tên `…WithoutCountingOfflineTime`) | total 65 000 → 60 000 | Không cộng thời gian process chết |
| `…viewExplainsEstimates…` | "Waiting for GPS" → "GPS signal lost" | STALE dùng `speedo_signal_lost` |
| `…viewDisplaysCleanSpeedAndDistanceDuringGapMotion` | 12.5 km → 12.0 km | km hiển thị không cộng ước lượng trong hầm |
| `…singleProviderRegistrationAndNetworkFallbackOnlyWhenStarved` → `…WithoutNetworkFallback` | Không có NETWORK, không đăng ký lại sau 12 s | Bỏ NETWORK; watchdog mới |

**Test mới khác:** `FixNormalizerTest` (4); `TripReplayTest` với log tổng hợp `synthetic-city-tunnel` (6.6 km, có hầm và chỗ dừng, sai số −0.4%, giới hạn 4%); `suspendedTripAndPreviousTripRoundTrip`; `stopSuspendsTheTripClock`; `viewNeverRendersUnknownSpeedAsZeroWhileTracking`.

### 4.4 Gradle
Lệnh: `testAutoDebugUnitTest :fermata:testMobileDebugUnitTest lintAutoDebug` với `-PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true`.
- **3 806 test, 0 fail** (gồm `ArchitectureBoundaryTest`); riêng nhóm speedo: 96 test.
- **Lint 0 error.** Không còn `UnusedResources` cho `speedo_*`.
- **Ghi chú môi trường:** lần chạy đầu không truyền `-P…` mà chỉ đặt biến môi trường. 9 module con fail ở `processAutoDebugResources` (thiếu chuỗi base, `aka me.app.fermataX.auto.debug`) do lệch cấu hình với artifact agent vòng 2 build trước đó. Chạy lại với đúng cờ chuẩn thì xanh. Không phải lỗi mã.

## 5. Kiểm thử trên thiết bị (Task 10)

**Đã làm được:**
- Build `assembleAutoDebug`.
- `adb install -r -d` lên `15c36230` (versionCode 328, cài lúc 22:29:31).
- Mở app trên điện thoại: chạy bình thường, logcat không có `FATAL`.

| ID | Kết quả | Ghi chú |
|---|---|---|
| V1 đứng yên trên AA | **KHÔNG CHẠY ĐƯỢC** ở lần đầu, **PASS** ở mục 5.1 | DHU kết nối được tới cổng 5277 và cổng đang LISTEN trên điện thoại, nhưng Android Auto không mở phiên chiếu. Phiên cuối ghi `ProjectionErrorCode = PROTOCOL_IO_ERROR(3) … READER_CLOSE(52)` lúc 22:29:45, ngay khi cài lại app; 3 lần khởi động lại DHU sau đó đều không tạo phiên mới; DHU báo "Don't have video focus" |
| V2 ngắt AA 5 phút | **KHÔNG CHẠY ĐƯỢC** | Cùng lý do. Đã có unit test `stopSuspendsTheTripClock` và `suspendDoesNotCountTime` |
| V3 tắt Location 30 s | **KHÔNG CHẠY ĐƯỢC** | Cùng lý do |
| V4 dashboard trên điện thoại | **KHÔNG ÁP DỤNG ĐƯỢC** trên bản `auto` này | Giao diện điện thoại là trang điều khiển/addon, không có thẻ speedo |
| R1–R4 lái thật | Chờ người dùng | `TripLogRecorder` đã bật trong bản debug đang cài |

Để chạy V1–V3: trên điện thoại vào Android Auto → Developer settings → **Start head unit server** (bật lại), rồi chạy DHU.

### 5.1 Vòng kiểm thử DHU sau khi bật lại head unit server (23:09–23:24)

**Thiết bị:** Redmi Note 8 `15c36230`, Android 16 (SDK 36). DHU 2.0 (`default_720p.ini`) chạy với console đọc lệnh từ file để chạm và chụp màn hình. Ảnh chụp nằm trong `scratchpad/dhu_shots/`.

#### Lỗi thật phát hiện được và đã sửa
Log hệ thống lúc 23:11:11 và 23:11:26:

```
ActivityManager: Background started FGS: Allowed [... uidState: BTOP ... allowWiu:-1]
ActivityManager: Foreground service started from background can not have location/camera/microphone access: service ...TripTrackingService
TripTrackingService.onStartCommand ... ForegroundService start rejected java.lang.SecurityException: Starting FGS with type location ...
```

- **Android 16 từ chối FGS loại location** khi app chỉ hiển thị qua Android Auto (app không có Activity trên điện thoại; tiến trình ở trạng thái BTOP, state 3). Bấm "Resume trip" trên màn hình xe cũng không được tính là thao tác trên điện thoại.
- **Dashboard trên xe lại không tự chạy bộ đo** (code vòng 2 chỉ chạy khi không có AA) mà chờ service, nên **speedo đứng ở "Trip paused" vĩnh viễn**.
- Kết quả S1/S11 của vòng 1–2 có thể đã bị ảnh hưởng vì agent mở `MainActivity` trên điện thoại trước khi đo: tiến trình ở trạng thái TOP thì Android cho phép FGS location.
- **Không cần FGS để nhận GPS trên AA:** `dumpsys activity processes` cho thấy `curProcState=3`, `curCapability=L-MNFUATI`; chữ **L** là `PROCESS_CAPABILITY_FOREGROUND_LOCATION`, có được nhờ car host bind vào app. Các request GPS/FUSED 1 s của app được phục vụ bình thường.

**Cách sửa:**
- `GlassSpeedoTripController` đếm số nơi đang giữ nó (dashboard, service). Bộ đo chỉ dừng khi cả hai đều thả.
- `GlassDashboardController` luôn `start/stop/onHostResumed` bộ đo, kể cả khi đang chiếu AA.
- `TripTrackingService` chỉ dừng bộ đo nếu chính service đã khởi động nó.
- `TripTrackingService` không báo `BACKGROUND_BLOCKED` ("Chạm để bật GPS") khi bộ đo vẫn đang ghi (`isTracking()`).
- Test mới: `controllerKeepsRecordingWhileAnyHolderRemains`.

**Còn mở:** muốn ghi trip khi *không* có dashboard hiển thị (tắt màn hình xe, đang mở app khác trên xe), cần một cơ chế khác FGS location khởi động từ nền; Android 16 không cho phép điều đó khi chỉ chiếu qua AA. Đây là ràng buộc của nền tảng, cần quyết định sản phẩm.

#### Kết quả V1–V3

| ID | Bước | Kết quả | Bằng chứng | Đánh giá |
|---|---|---|---|---|
| V1 | Đứng yên 2 phút (23:14:05 → 23:16:08) | 0 km/h, 0.0 km, TIME 00h 02m, "GPS connected" | `v1_t120.png`; log `trip-20261022-231348.csv` có 119 điểm FUSED (~1 Hz), speed 0.0, sAcc 0.13–0.34; không có FATAL của app | **PASS** |
| V2 | Ngắt AA (thoát DHU 23:16:29) 5,5 phút, nối lại 23:22 | Trước: TIME 00h 02m (`v2_before.png`). Sau: TIME 00h 02m, nút vẫn "Pause trip", "GPS connected" (`v2_after.png`) | Log `E,…,stop`; `dumpsys location`: mọi request của app bị gỡ lúc 23:16:31 (`-registration`), không còn listener nào trong lúc ngắt; log mới có `E,…,start` khi nối lại | **PASS** |
| V3 | `cmd location set-location-enabled false` 23:22:32 → bật lại 23:23:06 | Sau 12 s: "—" + "Turn on GPS, then resume" (`v3_off_12s.png`). 6 s sau khi bật: "0" từ cảm biến đứng yên, nhãn vẫn "Turn on GPS" (`v3_on_6s.png`). ~25 s sau khi bật: "GPS connected" (`v3_on_later.png`) | Log `I,…,1` (IMU yên) trong lúc chờ fix | **PASS**, kèm ghi chú: nhãn trạng thái về muộn hơn tốc độ khoảng 20 s (chờ 3 fix xác nhận). Chuỗi "…then resume" gây hiểu nhầm vì không cần bấm gì; nên đổi thành "Turn on GPS" |

**Ghi chú thêm:**
- **Thời gian trip vẫn chạy khi tắt Location:** TIME 00h 03m → 00h 04m. Đúng thiết kế: mất tín hiệu vẫn tính là thời gian chuyến đi, khác với ngắt AA.
- **Weather (`CarLocatorManager`) vẫn đăng ký 4 nguồn** GPS/NETWORK/FUSED/PASSIVE. Đây là F-4 phía CarFinder, ngoài phạm vi refactor speedo.

**Test sau bản sửa:** 3 808 test, 0 fail; lint 0 error.

## 6. Còn lại / rủi ro
- **Kiểm thử trên DHU và lái thật** (mục 5).
- **Hiệu chỉnh ngưỡng** (`σv`, IMU, `CURVE_PRIOR`) cần log lái thật (plan mục 5).
- **Log có toạ độ thật:** `TripLogRecorder` ghi ở bản debug; chỉ lấy ra được qua `adb pull` từ `Android/data/<pkg>/files/trip_logs/`. Log thật chỉ đưa vào repo khi người dùng đồng ý; hiện chỉ có log tổng hợp.
- **Trip trước:** `previousTrip` được lưu nhưng chưa có UI hiển thị (ngoài phạm vi plan).
